package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates persistence concerns for the auction house such as loading listings, creating new
 * records, and logging transactions. Implementations abstract the underlying data source
 * (SQL, flat files, etc.) while providing asynchronous operations for expensive tasks.
 */
public interface StorageManager {

    /**
     * Initializes the storage layer, establishing connections or creating schema as needed.
     *
     * @return {@code true} if initialization succeeded and the plugin may continue loading
     */
    boolean onEnable();

    /**
     * Gracefully shuts down the storage layer, closing connections and flushing pending writes.
     */
    void onDisable();

    /**
     * Loads all stored items into memory caches to make them available for listings and lookups.
     */
    void loadItems();

    /**
     * Inserts or updates the player entry to ensure ownership and statistics are tracked.
     *
     * @param player player to synchronize with storage
     */
    void upsertPlayer(Player player);

    /**
     * Creates and persists a new auction item record.
     *
     * @param seller        player listing the item
     * @param price         price of the listing
     * @param expiredAt     expiration timestamp in milliseconds
     * @param itemStacks    item stacks being sold
     * @param auctionEconomy economy to use for the listing
     * @return future containing the created {@link AuctionItem}
     */
    CompletableFuture<AuctionItem> createAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy);

    /**
     * Creates and persists a new auction item record using UUID and name directly.
     * This is useful for generating test data without requiring online players.
     *
     * @param sellerUniqueId seller's UUID
     * @param sellerName     seller's name
     * @param price          price of the listing
     * @param expiredAt      expiration timestamp in milliseconds
     * @param itemStacks     item stacks being sold
     * @param auctionEconomy economy to use for the listing
     * @return future containing the created {@link AuctionItem}
     */
    CompletableFuture<AuctionItem> createAuctionItem(UUID sellerUniqueId, String sellerName, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy);

    /**
     * Inserts or updates the player entry using UUID and name directly.
     * This is useful for registering fake players for test data.
     *
     * @param uniqueId player's UUID
     * @param name     player's name
     */
    void upsertPlayer(UUID uniqueId, String name);

    /**
     * Provides access to a specific repository module backed by the storage manager.
     *
     * @param module repository class to retrieve
     * @param <T>    repository type
     * @return repository instance
     */
    <T extends Repository> T with(Class<T> module);

    /**
     * Updates the stored representation of the given item in the specified storage bucket.
     *
     * @param item        item to update
     * @param storageType storage bucket where the item currently resides
     * @return future completing when the update is persisted
     */
    CompletableFuture<Void> updateItem(Item item, StorageType storageType);

    /**
     * Batch updates multiple items grouped by storage type.
     * Executes a single SQL query per storage type for efficiency.
     *
     * @param itemsByStorageType map of storage type to list of items to update
     * @return future completing when all updates are persisted
     */
    CompletableFuture<Void> updateItems(Map<StorageType, List<Item>> itemsByStorageType);

    /**
     * Records an audit log entry describing an action performed on an item.
     *
     * @param logType       type of log entry to create
     * @param itemId        identifier of the affected item
     * @param player        actor performing the action
     * @param targetUniqueId secondary player involved, if any
     * @param price         price related to the action
     * @param economyName   economy used for the transaction
     * @param additionalData extra serialized data for the log entry
     */
    void log(LogType logType, int itemId, Player player, UUID targetUniqueId, BigDecimal price, String economyName, String additionalData);

    /**
     * Retrieves a single item from storage by its identifier.
     *
     * @param id item identifier
     * @return future containing the item when found or {@code null} otherwise
     */
    CompletableFuture<Item> selectItem(int id);

    CompletableFuture<UUID> findUniqueId(String playerName);
}
