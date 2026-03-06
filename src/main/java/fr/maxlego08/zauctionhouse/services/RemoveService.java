package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;

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

    /**
     * Executes the removal process with proper locking and cluster notification.
     * This method follows the sequence:
     * 1. Check availability
     * 2. Acquire lock
     * 3. Change status
     * 4. Notify cluster
     * 5. Execute local removal
     * 6. Notify cluster of removal
     * 7. Release lock
     */
    private CompletableFuture<RemoveResult> executeRemoval(
            ItemStatus targetStatus,
            Player player,
            Item item,
            Runnable onUnavailable,
            Supplier<CompletableFuture<Void>> onLocalRemoval,
            StorageType storageType) {

        var context = new RemovalContext(item, targetStatus, storageType, onUnavailable, onLocalRemoval);
        var performanceConfig = this.plugin.getConfiguration().getPerformance();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();

        return checkAvailabilityStep(context, clusterBridge, performanceConfig)
                .thenCompose(available -> acquireLockStep(context, available, player, clusterBridge, performanceConfig))
                .thenCompose(token -> changeStatusAndNotifyStep(context, token, clusterBridge, performanceConfig))
                .thenCompose(v -> executeLocalRemovalStep(context, clusterBridge, performanceConfig))
                .thenCompose(v -> unlockAndCompleteStep(context, clusterBridge, performanceConfig))
                .exceptionally(throwable -> handleRemovalException(context, throwable, clusterBridge, logger));
    }

    /**
     * Step 1: Check if the item is available on the cluster.
     */
    private CompletableFuture<Boolean> checkAvailabilityStep(
            RemovalContext context,
            AuctionClusterBridge clusterBridge,
            PerformanceConfiguration config) {

        return clusterBridge.checkAvailability(context.item)
                .orTimeout(config.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Step 2: Acquire lock on the item.
     */
    private CompletableFuture<LockToken> acquireLockStep(
            RemovalContext context,
            boolean available,
            Player player,
            AuctionClusterBridge clusterBridge,
            PerformanceConfiguration config) {

        if (!available) {
            context.onUnavailable.run();
            context.result = RemoveResult.failure("Item not available", RemoveFailReason.ITEM_NOT_AVAILABLE);
            return failedFuture(new IllegalStateException("Item introuvable"));
        }

        return clusterBridge.lockItem(context.item, player.getUniqueId(), context.storageType)
                .orTimeout(config.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Step 3: Change item status and notify cluster.
     */
    private CompletableFuture<Void> changeStatusAndNotifyStep(
            RemovalContext context,
            LockToken token,
            AuctionClusterBridge clusterBridge,
            PerformanceConfiguration config) {

        context.token = token;

        if (LockToken.noop().value().equals(token.value())) {
            context.onUnavailable.run();
            context.result = RemoveResult.failure("Lock failed", RemoveFailReason.LOCK_FAILED);
            return failedFuture(new IllegalStateException("Item déjà en cours de traitement"));
        }

        // Change status after acquiring lock to ensure atomicity
        context.item.setStatus(context.targetStatus);
        context.statusChanged = true;

        return clusterBridge.notifyItemStatusChange(context.item, context.oldStatus, context.targetStatus)
                .orTimeout(config.notifyStatusChangeTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Step 4: Execute the local removal operation and notify cluster.
     */
    private CompletableFuture<Void> executeLocalRemovalStep(
            RemovalContext context,
            AuctionClusterBridge clusterBridge,
            PerformanceConfiguration config) {

        return context.onLocalRemoval.get()
                .thenCompose(v -> clusterBridge.removeItem(context.item, context.storageType)
                        .orTimeout(config.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS));
    }

    /**
     * Step 5: Unlock the item and complete the removal.
     */
    private CompletableFuture<RemoveResult> unlockAndCompleteStep(
            RemovalContext context,
            AuctionClusterBridge clusterBridge,
            PerformanceConfiguration config) {

        return clusterBridge.unlockItem(context.item, context.token, context.storageType)
                .orTimeout(config.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenApply(v -> {
                    context.result = RemoveResult.success("Item removed successfully", true);
                    return context.result;
                });
    }

    /**
     * Handles exceptions during the removal process, including cleanup.
     */
    private RemoveResult handleRemovalException(
            RemovalContext context,
            Throwable throwable,
            AuctionClusterBridge clusterBridge,
            Logger logger) {

        // Log appropriately based on exception type
        if (throwable.getCause() instanceof TimeoutException) {
            logger.warning("Removal operation timed out for item " + context.item.getId());
        } else {
            logger.severe("Error during removal for item " + context.item.getId() + ": " + throwable.getMessage());
        }

        // Release lock if acquired
        releaseLockOnError(context, clusterBridge, logger);

        // Restore status if changed
        restoreStatusOnError(context, clusterBridge);

        return context.result != null ? context.result : RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR);
    }

    /**
     * Releases the lock on error if it was acquired.
     */
    private void releaseLockOnError(RemovalContext context, AuctionClusterBridge clusterBridge, Logger logger) {
        if (context.token != null && !LockToken.noop().value().equals(context.token.value())) {
            clusterBridge.unlockItem(context.item, context.token, context.storageType)
                    .exceptionally(unlockError -> {
                        logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                        return null;
                    });
        }
    }

    /**
     * Restores the item status on error if it was changed.
     */
    private void restoreStatusOnError(RemovalContext context, AuctionClusterBridge clusterBridge) {
        if (context.statusChanged) {
            context.item.setStatus(context.oldStatus);
            clusterBridge.notifyItemStatusChange(context.item, context.targetStatus, context.oldStatus);
        }
    }

    /**
     * Context object holding state for the removal operation.
     * Reduces parameter passing between steps.
     */
    private static class RemovalContext {
        final Item item;
        final ItemStatus oldStatus;
        final ItemStatus targetStatus;
        final StorageType storageType;
        final Runnable onUnavailable;
        final Supplier<CompletableFuture<Void>> onLocalRemoval;

        LockToken token;
        boolean statusChanged;
        RemoveResult result;

        RemovalContext(Item item, ItemStatus targetStatus, StorageType storageType,
                       Runnable onUnavailable, Supplier<CompletableFuture<Void>> onLocalRemoval) {
            this.item = item;
            this.oldStatus = item.getStatus();
            this.targetStatus = targetStatus;
            this.storageType = storageType;
            this.onUnavailable = onUnavailable;
            this.onLocalRemoval = onLocalRemoval;
            this.statusChanged = false;
        }
    }
}
