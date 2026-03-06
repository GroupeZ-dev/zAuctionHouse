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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item expired", PurchaseFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.IS_PURCHASE_CONFIRM) {
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item not in purchase state", PurchaseFailReason.ITEM_NOT_IN_PURCHASE_STATE));
        }

        // Store the lock token for cleanup on exception
        final AtomicReference<LockToken> tokenHolder = new AtomicReference<>(null);
        final AtomicReference<PurchaseResult> resultHolder = new AtomicReference<>(null);
        final AtomicReference<ItemStatus> previousStatusHolder = new AtomicReference<>(item.getStatus());

        // Load timeout configuration
        var performanceConfig = this.plugin.getConfiguration().getPerformance();

        // 2. Vérifier si l'item est lock (with timeout)
        return clusterBridge.checkAvailability(item)
                .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(available -> {

            if (!available) {
                inventoryManager.updateInventory(player);
                resultHolder.set(PurchaseResult.failure("Item not available", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, player.getUniqueId(), StorageType.LISTED)
                    .orTimeout(performanceConfig.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);

        }).thenCompose(token -> {
            // Store token for exception cleanup
            tokenHolder.set(token);

            // Check if lock was acquired (noop token means lock failed)
            if (LockToken.noop().value().equals(token.value())) {
                inventoryManager.updateInventory(player);
                resultHolder.set(PurchaseResult.failure("Lock failed", PurchaseFailReason.LOCK_FAILED));
                return failedFuture(new IllegalStateException("Item déjà en cours d'achat"));
            }

            // Set status AFTER acquiring lock to ensure atomicity
            item.setStatus(ItemStatus.IS_BEING_PURCHASED);
            return clusterBridge.notifyItemStatusChange(item, previousStatusHolder.get(), ItemStatus.IS_BEING_PURCHASED)
                    .orTimeout(performanceConfig.notifyStatusChangeTimeoutMs(), TimeUnit.MILLISECONDS)
                    .thenCompose(v -> auctionEconomy.has(player, item.getPrice()));

        }).thenCompose(hasMoney -> {

            var token = tokenHolder.get();

            if (hasMoney) {
                return auctionManager.purchaseItem(player, item)
                        .thenCompose(v -> clusterBridge.notifyItemBought(player, item)
                                .orTimeout(performanceConfig.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS))
                        .thenCompose(v -> clusterBridge.unlockItem(item, token, StorageType.LISTED)
                                .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS))
                        .thenApply(v -> {
                            resultHolder.set(PurchaseResult.success("Purchase successful", true));
                            return resultHolder.get();
                        });
            }

            // Insufficient funds - unlock and notify
            message(this.plugin, player, Message.NOT_ENOUGH_MONEY);
            resultHolder.set(PurchaseResult.failure("Insufficient funds", PurchaseFailReason.INSUFFICIENT_FUNDS));
            return clusterBridge.unlockItem(item, token, StorageType.LISTED)
                    .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                    .thenApply(v -> resultHolder.get());

        }).exceptionally(e -> {
            // Handle timeout exceptions specifically
            if (e.getCause() instanceof TimeoutException) {
                logger.warning("Purchase operation timed out for item " + item.getId());
            } else {
                logger.severe("Error during purchase for item " + item.getId() + ": " + e.getMessage());
            }

            // Ensure lock is released on any exception
            var token = tokenHolder.get();
            if (token != null && !LockToken.noop().value().equals(token.value())) {
                clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(unlockError -> {
                    logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                    return null;
                });
            }

            // Restore item status only if it was changed (lock was acquired)
            if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {
                var previousStatus = previousStatusHolder.get();
                item.setStatus(previousStatus);
                clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)
                    .exceptionally(restoreError -> {
                        logger.severe("Failed to restore item status for item " + item.getId() + ": " + restoreError.getMessage());
                        return null;
                    });
            }

            // Return the previously set result or a generic error
            var result = resultHolder.get();
            return result != null ? result : PurchaseResult.failure("Internal error", PurchaseFailReason.INTERNAL_ERROR);
        });
    }
}
