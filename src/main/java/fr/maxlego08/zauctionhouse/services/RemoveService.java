package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemovePurchasedItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveResult;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class RemoveService extends AuctionService implements AuctionRemoveService {

    private final AuctionPlugin plugin;

    public RemoveService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<RemoveResult> removeListedItem(Player player, Item item) {

        var event = new AuctionPreRemoveListedItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.plugin.getAuctionManager();
        var logger = this.plugin.getLogger();

        if (item.isExpired()) {
            logger.info("Item expired (Remove Listed)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            manager.openMainAuction(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.AVAILABLE && item.getStatus() != ItemStatus.IS_REMOVE_CONFIRM) {
            logger.info("Item not available (Remove Listed)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            manager.openMainAuction(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not available", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        return executeRemoval(ItemStatus.IS_BEING_REMOVED, player, item, () -> manager.updateInventory(player), () -> manager.removeListedItem(player, item), StorageType.LISTED);
    }

    @Override
    public CompletableFuture<RemoveResult> removeSellingItem(Player player, Item item) {

        var event = new AuctionPreRemoveListedItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.plugin.getAuctionManager();
        var logger = this.plugin.getLogger();

        if (item.isExpired()) {
            logger.info("Item expired (Remove Selling)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            logger.info("Item not available (Remove Selling)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not available", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        return executeRemoval(ItemStatus.IS_BEING_REMOVED, player, item, () -> manager.updateInventory(player), () -> manager.removeSellingItem(player, item), StorageType.LISTED);
    }

    @Override
    public CompletableFuture<RemoveResult> removeExpiredItem(Player player, Item item) {

        var event = new AuctionPreRemoveExpiredItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.plugin.getAuctionManager();
        var logger = this.plugin.getLogger();

        if (item.isExpired()) {
            logger.info("Item expired (Remove Expired)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);
            manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.REMOVED) {
            logger.info("Item not available (Remove Expired), Current status: " + item.getStatus());
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);
            manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not in removed status", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        return executeRemoval(ItemStatus.DELETED, player, item, () -> manager.updateInventory(player), () -> this.plugin.getAuctionManager().removeExpiredItem(player, item), StorageType.EXPIRED);
    }

    @Override
    public CompletableFuture<RemoveResult> removePurchasedItem(Player player, Item item) {

        var event = new AuctionPreRemovePurchasedItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.plugin.getAuctionManager();
        var logger = this.plugin.getLogger();

        if (item.isExpired()) {
            logger.info("Item expired (Remove Purchased)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);
            manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.PURCHASED) {
            logger.info("Item not available (Remove Purchased)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);
            manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not in purchased status", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        return executeRemoval(ItemStatus.DELETED, player, item, () -> manager.updateInventory(player), () -> manager.removePurchasedItem(player, item), StorageType.PURCHASED);
    }

    private CompletableFuture<RemoveResult> executeRemoval(ItemStatus itemStatus, Player player, Item item, Runnable onUnavailable, Supplier<CompletableFuture<Void>> onLocalRemoval, StorageType storageType) {

        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();

        var oldStatus = item.getStatus();
        item.setStatus(itemStatus);

        // Store the lock token for cleanup on exception
        final AtomicReference<LockToken> tokenHolder = new AtomicReference<>(null);
        final AtomicReference<RemoveResult> resultHolder = new AtomicReference<>(null);

        return clusterBridge.notifyItemStatusChange(item, oldStatus, itemStatus).thenCompose(v1 -> clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                logger.info("Item is not available");
                onUnavailable.run();
                resultHolder.set(RemoveResult.failure("Item not available", RemoveFailReason.ITEM_NOT_AVAILABLE));
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, player.getUniqueId(), storageType);

        })).thenCompose(token -> {
            // Store token for exception cleanup
            tokenHolder.set(token);

            // Check if lock was acquired
            if (LockToken.noop().value().equals(token.value())) {
                logger.info("Failed to acquire lock on item");
                onUnavailable.run();
                resultHolder.set(RemoveResult.failure("Lock failed", RemoveFailReason.LOCK_FAILED));
                return failedFuture(new IllegalStateException("Item déjà en cours de traitement"));
            }

            return onLocalRemoval.get()
                    .thenCompose(v -> clusterBridge.removeItem(item, storageType))
                    .thenCompose(vv -> clusterBridge.unlockItem(item, token, storageType))
                    .thenApply(vv -> {
                        resultHolder.set(RemoveResult.success("Item removed successfully", true));
                        return resultHolder.get();
                    });

        }).exceptionally(throwable -> {
            logger.severe("Error during removal: " + throwable.getMessage());
            throwable.printStackTrace();

            // Ensure lock is released on any exception
            var token = tokenHolder.get();
            if (token != null && !LockToken.noop().value().equals(token.value())) {
                clusterBridge.unlockItem(item, token, storageType)
                        .exceptionally(unlockError -> {
                            logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                            return null;
                        });
            }

            // Restore item status
            item.setStatus(oldStatus);
            clusterBridge.notifyItemStatusChange(item, itemStatus, oldStatus);

            // Return the previously set result or a generic error
            var result = resultHolder.get();
            return result != null ? result : RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR);
        });
    }
}
