package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.purchase.AuctionPrePurchaseItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseResult;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class PurchaseService extends AuctionService implements AuctionPurchaseService {

    private final AuctionPlugin plugin;

    public PurchaseService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<PurchaseResult> purchaseItem(Player player, Item item) {

        var event = new AuctionPrePurchaseItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(PurchaseResult.failure("Event cancelled", PurchaseFailReason.EVENT_CANCELLED));
        }

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();
        var auctionEconomy = item.getAuctionEconomy();

        var configuration = this.plugin.getConfiguration().getActions().purchased();
        if (configuration.giveItem() && configuration.freeSpace() && !item.canReceiveItem(player)) {
            message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Not enough space", PurchaseFailReason.INSUFFICIENT_SPACE));
        }

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            logger.info("Item expired");
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item expired", PurchaseFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.IS_PURCHASE_CONFIRM) {
            logger.info("Item not available");
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item not in purchase state", PurchaseFailReason.ITEM_NOT_IN_PURCHASE_STATE));
        }
        item.setStatus(ItemStatus.IS_BEING_PURCHASED);

        // Store the lock token for cleanup on exception
        final AtomicReference<LockToken> tokenHolder = new AtomicReference<>(null);
        final AtomicReference<PurchaseResult> resultHolder = new AtomicReference<>(null);

        // 2. Vérifier si l'item est lock
        return clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                logger.info("Item is not available");
                inventoryManager.updateInventory(player);
                resultHolder.set(PurchaseResult.failure("Item not available", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, player.getUniqueId(), StorageType.LISTED);

        }).thenCompose(token -> {
            // Store token for exception cleanup
            tokenHolder.set(token);

            // Check if lock was acquired (noop token means lock failed)
            if (LockToken.noop().value().equals(token.value())) {
                logger.info("Failed to acquire lock on item");
                inventoryManager.updateInventory(player);
                resultHolder.set(PurchaseResult.failure("Lock failed", PurchaseFailReason.LOCK_FAILED));
                return failedFuture(new IllegalStateException("Item déjà en cours d'achat"));
            }

            return auctionEconomy.has(player, item.getPrice()).thenApply(hasMoney -> hasMoney);

        }).thenCompose(hasMoney -> {

            var token = tokenHolder.get();

            if (hasMoney) {
                return auctionManager.purchaseItem(player, item)
                        .thenCompose(v -> clusterBridge.notifyItemBought(player, item))
                        .thenCompose(v -> clusterBridge.unlockItem(item, token, StorageType.LISTED))
                        .thenApply(v -> {
                            resultHolder.set(PurchaseResult.success("Purchase successful", true));
                            return resultHolder.get();
                        });
            }

            // Insufficient funds - unlock and notify
            message(this.plugin, player, Message.NOT_ENOUGH_MONEY);
            resultHolder.set(PurchaseResult.failure("Insufficient funds", PurchaseFailReason.INSUFFICIENT_FUNDS));
            return clusterBridge.unlockItem(item, token, StorageType.LISTED).thenApply(v -> resultHolder.get());

        }).exceptionally(e -> {
            logger.severe("Error during purchase: " + e.getMessage());
            e.printStackTrace();

            // Ensure lock is released on any exception
            var token = tokenHolder.get();
            if (token != null && !LockToken.noop().value().equals(token.value())) {
                clusterBridge.unlockItem(item, token, StorageType.LISTED)
                        .exceptionally(unlockError -> {
                            logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                            return null;
                        });
            }

            // Restore item status
            item.setStatus(ItemStatus.AVAILABLE);
            clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, ItemStatus.AVAILABLE);

            // Return the previously set result or a generic error
            var result = resultHolder.get();
            return result != null ? result : PurchaseResult.failure("Internal error", PurchaseFailReason.INTERNAL_ERROR);
        });
    }
}
