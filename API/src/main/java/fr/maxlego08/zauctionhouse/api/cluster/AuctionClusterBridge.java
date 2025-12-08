package fr.maxlego08.zauctionhouse.api.cluster;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Defines the contract used to synchronize auction house actions across multiple server instances.
 * Implementations are responsible for coordinating locks and broadcasting state changes so that
 * concurrent purchases remain consistent in clustered environments.
 */
public interface AuctionClusterBridge {

    /**
     * Checks whether the item can still be purchased across the cluster, preventing stale views.
     *
     * @param item item being evaluated
     * @return future resolving to {@code true} if the item is still available
     */
    CompletableFuture<Boolean> checkAvailability(Item item);

    /**
     * Attempts to lock the item for the given buyer in the specified storage context, preventing
     * other servers from selling it simultaneously.
     *
     * @param item        item to lock
     * @param buyerId     UUID of the buyer
     * @param storageType storage bucket the item resides in
     * @return future containing a lock token to be used when unlocking
     */
    CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType);

    /**
     * Releases a previously acquired lock, allowing other nodes to act on the item again.
     *
     * @param item        item to unlock
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future completing once the unlock has been propagated
     */
    CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType);

    /**
     * Notifies the cluster that a player bought an item so caches and live views can be updated.
     *
     * @param player buyer who completed the purchase
     * @param item   item that was purchased
     * @return future completing after the notification is processed
     */
    CompletableFuture<Void> notifyItemBought(Player player, Item item);

    /**
     * Notifies the cluster that a new item has been listed for sale.
     *
     * @param item item that was listed
     * @return future completing after the notification is processed
     */
    CompletableFuture<Void> notifyItemListed(Item item);

    /**
     * Broadcasts a status change for an item so other nodes can mirror the new state.
     *
     * @param item      item whose status changed
     * @param oldStatus previous status value
     * @param newStatus new status value
     * @return future completing after the notification is processed
     */
    CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus);

    /**
     * Removes an item from the specified storage type across the cluster.
     *
     * @param item        item to remove
     * @param storageType storage bucket the item currently resides in
     * @return future completing after the deletion is processed
     */
    CompletableFuture<Void> removeItem(Item item, StorageType storageType);
}
