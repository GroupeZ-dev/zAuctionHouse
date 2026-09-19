package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
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
     * @param seller         player listing the item
     * @param price          price of the listing
     * @param expiredAt      expiration timestamp in milliseconds
     * @param itemStacks     item stacks being sold
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
     * Gets the underlying database connection used by the storage manager.
     *
     * @return the database connection
     */
    DatabaseConnection getDatabaseConnection();

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
     * Updates the item in compare-and-set mode: the write only succeeds while the row still
     * carries {@code from}.
     * <p>
     * Le future echoue avec {@link StaleItemException} quand la course est perdue. L'appelant ne
     * doit alors ni remettre l'item au joueur, ni deplacer d'argent, ni restaurer un statut du
     * cycle LISTED : il doit purger sa copie memoire et converger vers la base.
     * <p>
     * L'implementation par defaut delegue a {@link #updateItem(Item, StorageType)} et n'offre donc
     * AUCUNE garantie de compare-and-set : elle n'existe que pour ne pas casser les implementations
     * tierces existantes de cette interface.
     *
     * @param item item to update
     * @param from storage bucket the row is expected to still carry
     * @param to   destination storage bucket
     * @return future completing when the update is persisted, failing with
     *         {@link StaleItemException} when no row matched
     */
    default CompletableFuture<Void> updateItem(Item item, StorageType from, StorageType to) {
        return updateItem(item, to);
    }

    /**
     * Batch compare-and-set variant of {@link #updateItems(Map)}.
     *
     * @param itemsByStorageType destination bucket to items to move
     * @param from               storage bucket all these rows are expected to still carry
     * @return future completing with the ids that lost the race (empty when everything moved);
     *         the default implementation always reports an empty list
     */
    default CompletableFuture<List<Integer>> updateItems(Map<StorageType, List<Item>> itemsByStorageType, StorageType from) {
        return updateItems(itemsByStorageType).thenApply(ignored -> List.of());
    }

    /**
     * Reserve une annonce : la ligne parente est creee dans un etat NON PUBLIABLE et les contenus
     * sont inseres, mais l'annonce n'est visible d'aucun serveur tant que
     * {@link #publishAuctionItem(AuctionItem)} n'a pas ete appele.
     * <p>
     * L'implementation par defaut delegue a {@link #createAuctionItem} : un StorageManager tiers
     * compile contre une version anterieure de l'API continue de fonctionner a l'identique, sans
     * le gain d'atomicite.
     *
     * @param seller            player listing the item
     * @param price             price of the listing
     * @param expiredAt         expiration timestamp in milliseconds
     * @param itemStacks        item stacks being sold
     * @param encodedItemStacks les contenus DEJA encodes ({@code Base64ItemStack}), dans le meme
     *                          ordre que {@code itemStacks} ; aucun element ne doit etre null
     * @param auctionEconomy    economy to use for the listing
     * @return future containing the reserved {@link AuctionItem}
     */
    default CompletableFuture<AuctionItem> reserveAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, List<String> encodedItemStacks, AuctionEconomy auctionEconomy) {
        return createAuctionItem(seller, price, expiredAt, itemStacks, auctionEconomy);
    }

    /**
     * Rend visible une annonce reservee.
     *
     * @param auctionItem the reserved listing to publish
     * @return le nombre de lignes publiees : 1 = succes, 0 = la reservation n'existe plus
     */
    default CompletableFuture<Integer> publishAuctionItem(AuctionItem auctionItem) {
        return CompletableFuture.completedFuture(1);
    }

    /**
     * Annule une reservation qui n'a pas pu aboutir (les contenus partent en cascade).
     *
     * @param auctionItem the reservation to cancel
     * @return future completing when the reservation has been removed
     */
    default CompletableFuture<Void> cancelReservation(AuctionItem auctionItem) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Records an audit log entry describing an action performed on an item.
     *
     * @param logType        type of log entry to create
     * @param itemId         identifier of the affected item
     * @param player         actor performing the action
     * @param targetUniqueId secondary player involved, if any
     * @param itemstack      Base64-encoded itemstack data
     * @param price          price related to the action
     * @param economyName    economy used for the transaction
     * @param additionalData extra serialized data for the log entry
     * @param readedAt       if not null, marks the log as already read (e.g., when seller was online during sale)
     */
    void log(LogType logType, int itemId, Player player, UUID targetUniqueId, String itemstack, BigDecimal price, String economyName, String additionalData, Date readedAt);

    /**
     * Creates a transaction record for economy operations.
     *
     * @param playerUniqueId player's UUID
     * @param economyName    economy used for the transaction
     * @param before         balance before the transaction
     * @param after          balance after the transaction
     * @param value          amount changed
     */
    void createTransaction(Item item, UUID playerUniqueId, String economyName, BigDecimal before, BigDecimal after, BigDecimal value, TransactionStatus status);

    /**
     * Retrieves a single item from storage by its identifier.
     *
     * @param id item identifier
     * @return future containing the item when found or {@code null} otherwise
     */
    CompletableFuture<Item> selectItem(int id);

    /**
     * Outcome of a single-item lookup.
     */
    enum LookupState {
        /** The row exists and the item could be fully rebuilt. */
        FOUND,
        /** The row does not exist any more (deleted, or never existed): the item is definitively gone. */
        GONE,
        /** The row exists but could NOT be rebuilt: unknown economy, unreadable content, unsupported type. */
        UNAVAILABLE
    }

    /**
     * Result of {@link #selectItemState(int)}.
     *
     * @param state the lookup outcome
     * @param item  the resolved item, non-null only when {@code state} is {@link LookupState#FOUND}
     */
    record ItemLookupResult(LookupState state, Item item) {

        /**
         * Creates a result describing a fully resolved item.
         *
         * @param item the resolved item
         * @return the lookup result
         */
        public static ItemLookupResult found(Item item) {
            return new ItemLookupResult(LookupState.FOUND, item);
        }

        /**
         * Creates a result describing a row that does not exist any more.
         *
         * @return the lookup result
         */
        public static ItemLookupResult gone() {
            return new ItemLookupResult(LookupState.GONE, null);
        }

        /**
         * Creates a result describing a row that exists but could not be rebuilt.
         *
         * @return the lookup result
         */
        public static ItemLookupResult unavailable() {
            return new ItemLookupResult(LookupState.UNAVAILABLE, null);
        }

        /**
         * Tells whether the item could be fully resolved.
         *
         * @return {@code true} when the state is {@link LookupState#FOUND}
         */
        public boolean isFound() {
            return this.state == LookupState.FOUND;
        }
    }

    /**
     * Looks up a single item and reports WHY it could not be resolved.
     * <p>
     * {@link #selectItem(int)} collapses three very different situations into a single
     * {@code null}: the row was deleted, the economy referenced by the row is missing from
     * economies.yml, or the content could not be decoded. Callers then treat all three as
     * "sold elsewhere" and purge the local copy - which silently makes a listing vanish when
     * the real problem is a configuration mistake.
     * <p>
     * The default implementation maps {@code null} to {@link LookupState#GONE}, so third-party
     * implementations of this interface keep compiling and behaving exactly as before.
     *
     * @param id identifier of the item
     * @return future resolving to the lookup outcome
     */
    default CompletableFuture<ItemLookupResult> selectItemState(int id) {
        return selectItem(id).thenApply(item -> item == null ? ItemLookupResult.gone() : ItemLookupResult.found(item));
    }

    /**
     * Finds a player's UUID by their username.
     *
     * @param playerName the player's username
     * @return future containing the player's UUID, or {@code null} if not found
     */
    CompletableFuture<UUID> findUniqueId(String playerName);

    /**
     * Gets a player's username by their UUID.
     *
     * @param uuid the player's UUID
     * @return the player's username, or {@code null} if not found
     */
    String getPlayerName(UUID uuid);

    /**
     * Retrieves the sales history for a player.
     *
     * @param playerUniqueId the player's UUID
     * @param expireAfterMs  only return logs created after this duration in ms (0 = no filter)
     * @return list of log entries for the player's sales
     */
    List<LogDTO> selectSalesHistory(UUID playerUniqueId, long expireAfterMs);

    /**
     * Retrieves multiple items by their IDs.
     *
     * @param integers list of item IDs to retrieve
     * @return list of items found
     */
    List<Item> selectItems(List<Integer> integers);

    /**
     * Retrieves player usernames for a list of UUIDs.
     *
     * @param uuids list of UUID strings
     * @return map of UUID to username
     */
    Map<UUID, String> selectPlayers(List<String> uuids);

    /**
     * Marks unread purchase logs as read for a specific item and seller.
     * Used by the cluster addon when the seller receives a real-time notification
     * on another server, to prevent a duplicate "while you were away" notification.
     *
     * @param itemId         the item ID
     * @param sellerUniqueId the seller's UUID
     */
    void markPurchaseLogAsRead(int itemId, UUID sellerUniqueId);
}
