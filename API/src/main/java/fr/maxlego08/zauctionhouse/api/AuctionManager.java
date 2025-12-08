package fr.maxlego08.zauctionhouse.api;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Central entry point for manipulating the auction house state. Implementations coordinate UI
 * opening, item storage, cache management, and asynchronous item lifecycle actions so that other
 * plugins can interact with the marketplace without duplicating logic.
 */
public interface AuctionManager {

    /**
     * Opens the main auction house inventory for the provided player using the default first page
     * configuration.
     *
     * @param player viewer who should see the main auction interface
     */
    void openMainAuction(Player player);

    /**
     * Opens the main auction house inventory at a specific page for pagination-aware UIs.
     *
     * @param player viewer who should see the main auction interface
     * @param page   zero or one based page index depending on the configured inventory provider
     */
    void openMainAuction(Player player, int page);

    /**
     * Forces the currently opened auction inventory of the player to refresh its contents and UI
     * components. Implementations typically re-run pagination and sorting logic before redrawing
     * the view.
     *
     * @param player player whose auction inventory should be updated
     */
    void updateInventory(Player player);

    /**
     * @return service responsible for processing purchases and related validations
     */
    AuctionPurchaseService getPurchaseService();

    /**
     * @return service used to list items for sale and validate prices or player limits
     */
    AuctionSellService getSellService();

    /**
     * @return service coordinating removal of items from storage or live listings
     */
    AuctionRemoveService getRemoveService();

    /**
     * @return service that moves items into expired storage and notifies owners when applicable
     */
    AuctionExpireService getExpireService();

    /**
     * Retrieves every item stored under the given bucket (listed, expired, purchased, etc.).
     *
     * @param storageType logical container to read from
     * @return immutable or defensive copy list of items currently recorded for that storage type
     */
    List<Item> getItems(StorageType storageType);

    /**
     * Retrieves items from the given storage type and filters them before returning. Filtering is
     * executed synchronously on the current thread and should remain inexpensive.
     *
     * @param storageType logical container to read from
     * @param predicate   filter applied to each candidate item
     * @return list of items that satisfy the predicate
     */
    List<Item> getItems(StorageType storageType, Predicate<Item> predicate);

    /**
     * Retrieves, filters, and sorts items from the given storage type in one pass to support
     * inventory rendering and API consumers that require deterministic ordering.
     *
     * @param storageType logical container to read from
     * @param predicate   filter applied to each candidate item
     * @param comparator  ordering applied to the filtered results
     * @return sorted list of items that satisfy the predicate
     */
    List<Item> getItems(StorageType storageType, Predicate<Item> predicate, Comparator<Item> comparator);

    /**
     * Adds a new item into the specified storage bucket. Implementations are responsible for
     * persisting the item and updating any caches or live inventory views.
     *
     * @param storageType logical container to add the item to
     * @param item        item instance to store
     */
    void addItem(StorageType storageType, Item item);

    /**
     * Removes the provided item reference from the specified storage type if it exists.
     *
     * @param storageType storage bucket the item should be removed from
     * @param item        item instance to delete
     */
    void removeItem(StorageType storageType, Item item);

    /**
     * Removes an item identified by its unique ID from the specified storage type without
     * requiring a full item instance.
     *
     * @param storageType storage bucket the item should be removed from
     * @param itemId      unique identifier of the item to delete
     */
    void removeItem(StorageType storageType, int itemId);

    /**
     * Retrieves every item currently listed for sale by the given player, excluding expired or
     * purchased entries.
     *
     * @param player player who listed the items
     * @return list of active listings created by the player
     */
    List<Item> getItemsListedForSale(Player player);

    /**
     * Retrieves expired listings that belong to the specified player so they can reclaim them.
     *
     * @param player player owner of the expired items
     * @return expired items awaiting reclamation
     */
    List<Item> getExpiredItems(Player player);

    /**
     * Retrieves items currently owned by the player but still within the auction house storage
     * (for example, unsold items or items awaiting delivery).
     *
     * @param player player owner to search for
     * @return items belonging to the player across storage types
     */
    List<Item> getPlayerOwnedItems(Player player);

    /**
     * Retrieves items the player successfully purchased and that are held in storage until
     * collected.
     *
     * @param player player who purchased the items
     * @return purchased items awaiting delivery
     */
    List<Item> getPurchasedItems(Player player);

    /**
     * Version of {@link #getExpiredItems(Player)} using a UUID for offline player compatibility.
     *
     * @param uniqueId unique identifier of the player
     * @return expired items awaiting reclamation
     */
    List<Item> getExpiredItems(java.util.UUID uniqueId);

    /**
     * Version of {@link #getPlayerOwnedItems(Player)} using a UUID for offline player support.
     *
     * @param uniqueId unique identifier of the player
     * @return items belonging to the player across storage types
     */
    List<Item> getPlayerOwnedItems(java.util.UUID uniqueId);

    /**
     * Version of {@link #getPurchasedItems(Player)} using a UUID for offline player support.
     *
     * @param uniqueId unique identifier of the player
     * @return purchased items awaiting delivery
     */
    List<Item> getPurchasedItems(java.util.UUID uniqueId);

    /**
     * Retrieves or initializes the cache entry associated with the given player, exposing
     * frequently accessed player-specific data.
     *
     * @param player player whose cache should be retrieved
     * @return cache wrapper providing quick access to player metadata
     */
    PlayerCache getCache(Player player);

    /**
     * Clears the requested cache keys for every tracked player, forcing the values to be recomputed
     * on next access.
     *
     * @param keys cache keys to reset; when empty all caches remain untouched
     */
    void clearPlayersCache(PlayerCacheKey... keys);

    /**
     * Clears the requested cache keys for a specific player so the information will be recalculated
     * the next time it is requested.
     *
     * @param player player whose cache should be purged
     * @param keys   cache keys to reset; when empty no keys are cleared
     */
    void clearPlayerCache(Player player, PlayerCacheKey... keys);

    /**
     * Removes the entire cache entry for a player, typically when they disconnect or no longer need
     * to be tracked.
     *
     * @param player player whose cache should be removed
     */
    void removeCache(Player player);

    /**
     * Asynchronously removes an item the player listed for sale, executing required storage and
     * refund operations.
     *
     * @param player player requesting the removal
     * @param item   listing to remove
     * @return future completing once the removal has been processed
     */
    CompletableFuture<Void> removeListedItem(Player player, Item item);

    /**
     * Asynchronously removes an item owned by the player from their storage, for example when they
     * discard a reclaimed item.
     *
     * @param player player requesting the removal
     * @param item   owned item to delete
     * @return future completing once the removal has been processed
     */
    CompletableFuture<Void> removeOwnedItem(Player player, Item item);

    /**
     * Asynchronously removes an expired item from the player's expired storage.
     *
     * @param player player requesting the removal
     * @param item   expired item to delete
     * @return future completing once the removal has been processed
     */
    CompletableFuture<Void> removeExpiredItem(Player player, Item item);

    /**
     * Asynchronously removes a purchased item from the player's purchased storage.
     *
     * @param player player requesting the removal
     * @param item   purchased item to delete
     * @return future completing once the removal has been processed
     */
    CompletableFuture<Void> removePurchasedItem(Player player, Item item);

    /**
     * Allows an administrator to remove an item that belongs to another player from any storage
     * type. Implementations should log the action and respect configured permissions.
     *
     * @param admin            administrator performing the removal
     * @param targetUniqueId   owner of the item
     * @param item             item to remove
     * @param storageType      storage bucket the item currently resides in
     */
    void adminRemoveItem(Player admin, java.util.UUID targetUniqueId, Item item, StorageType storageType);

    /**
     * Attempts to purchase the provided item on behalf of the player. The returned future completes
     * once validations, economy transactions, storage updates, and notifications are finished.
     *
     * @param player buyer initiating the purchase
     * @param item   listing to buy
     * @return future completing after the purchase workflow finishes
     */
    CompletableFuture<Void> purchaseItem(Player player, Item item);

    /**
     * Sends a localized or parameterized message to the player using the plugin's messaging system.
     *
     * @param player recipient of the message
     * @param message message descriptor to send
     * @param args    optional parameters inserted into the message template
     */
    void message(Player player, Message message, Object... args);

    /**
     * Propagates changes to an item currently listed for sale so viewers can see the update without
     * reopening the interface.
     *
     * @param item          listing that changed
     * @param added         {@code true} when the item was added, {@code false} when removed
     * @param ignoredPlayer optional player who should not receive the update (e.g., action initiator)
     */
    void updateListedItems(Item item, boolean added, Player ignoredPlayer);
}
