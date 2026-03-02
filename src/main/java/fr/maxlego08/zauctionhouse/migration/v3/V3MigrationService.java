package fr.maxlego08.zauctionhouse.migration.v3;

import fr.maxlego08.sarah.SchemaBuilder;
import fr.maxlego08.sarah.database.Schema;
import fr.maxlego08.sarah.logger.JULogger;
import fr.maxlego08.sarah.logger.Logger;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;
import fr.maxlego08.zauctionhouse.migration.v3.reader.V3DataReader;
import fr.maxlego08.zauctionhouse.migration.v3.reader.V3JsonDataReader;
import fr.maxlego08.zauctionhouse.migration.v3.reader.V3SqlDataReader;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.PlayerRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service for migrating data from zAuctionHouse V3 to V4.
 * <p>
 * This service handles the complete migration process including:
 * <ul>
 *   <li>Player data migration</li>
 *   <li>Auction items migration (with multi-item support)</li>
 *   <li>Transaction history migration</li>
 *   <li>Logging of imported data</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * V3MigrationService migration = new V3MigrationService(plugin);
 *
 * // For SQL database
 * migration.migrateFromSql("localhost", 3306, "database", "user", "pass", "zauctionhouse_")
 *          .thenAccept(result -> logger.info(result.toString()));
 *
 * // For SQLite
 * migration.migrateFromSqlite("/path/to/database.db", "zauctionhouse_")
 *          .thenAccept(result -> logger.info(result.toString()));
 *
 * // For JSON
 * migration.migrateFromJson(new File(dataFolder, "v3data"))
 *          .thenAccept(result -> logger.info(result.toString()));
 * }</pre>
 */
public class V3MigrationService {

    private final AuctionPlugin plugin;
    private final Logger logger;
    private Consumer<String> progressCallback;

    public V3MigrationService(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.logger = JULogger.from(plugin.getLogger());
    }

    /**
     * Sets a callback for progress updates during migration.
     */
    public V3MigrationService onProgress(Consumer<String> callback) {
        this.progressCallback = callback;
        return this;
    }

    private void progress(String message) {
        plugin.getLogger().info("[Migration] " + message);
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }

    /**
     * Migrates data from a V3 MySQL/MariaDB database.
     */
    public CompletableFuture<V3MigrationResult> migrateFromSql(String host, int port, String database, String username, String password, String tablePrefix) {
        V3SqlDataReader reader = new V3SqlDataReader(plugin, host, port, database, username, password, tablePrefix);
        return migrate(reader);
    }

    /**
     * Migrates data from a V3 SQLite database.
     */
    public CompletableFuture<V3MigrationResult> migrateFromSqlite(String sqlitePath, String tablePrefix) {
        V3SqlDataReader reader = new V3SqlDataReader(plugin, sqlitePath, tablePrefix);
        return migrate(reader);
    }

    /**
     * Migrates data from V3 JSON files.
     */
    public CompletableFuture<V3MigrationResult> migrateFromJson(java.io.File dataFolder) {
        V3JsonDataReader reader = new V3JsonDataReader(plugin, dataFolder);
        return migrate(reader);
    }

    /**
     * Performs the migration using the provided data reader.
     */
    public CompletableFuture<V3MigrationResult> migrate(V3DataReader reader) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            AtomicInteger errors = new AtomicInteger(0);

            try {
                // Test connection
                progress("Testing connection to V3 data source...");
                Boolean connected = reader.testConnection().join();
                if (!connected) {
                    return V3MigrationResult.failure("Failed to connect to V3 data source");
                }
                progress("Connection successful!");

                // Get counts
                int itemCount = reader.getItemCount().join();
                int transactionCount = reader.getTransactionCount().join();
                progress("Found " + itemCount + " items and " + transactionCount + " transactions to migrate");

                if (itemCount == 0 && transactionCount == 0) {
                    return V3MigrationResult.failure("No data found to migrate");
                }

                // Read all data
                progress("Reading V3 items...");
                List<V3AuctionItem> v3Items = reader.readItems().join();
                progress("Read " + v3Items.size() + " items");

                progress("Reading V3 transactions...");
                List<V3Transaction> v3Transactions = reader.readTransactions().join();
                progress("Read " + v3Transactions.size() + " transactions");

                // Collect all unique players
                progress("Collecting player data...");
                Map<UUID, String> players = collectPlayers(v3Items, v3Transactions);
                progress("Found " + players.size() + " unique players");

                // Migrate players
                progress("Migrating players...");
                int playersMigrated = migratePlayers(players, errors);
                progress("Migrated " + playersMigrated + " players");

                // Migrate items
                progress("Migrating items...");
                int itemsMigrated = migrateItems(v3Items, errors);
                progress("Migrated " + itemsMigrated + " items");

                // Migrate transactions to logs
                progress("Migrating transactions to logs...");
                int transactionsMigrated = migrateTransactions(v3Transactions, errors);
                progress("Migrated " + transactionsMigrated + " transactions");

                long duration = System.currentTimeMillis() - startTime;
                progress("Migration completed in " + duration + "ms");

                return V3MigrationResult.success(playersMigrated, itemsMigrated, transactionsMigrated, errors.get(), duration);

            } catch (Exception e) {
                plugin.getLogger().severe("Migration failed: " + e.getMessage());
                e.printStackTrace();
                return V3MigrationResult.failure("Migration failed: " + e.getMessage());
            } finally {
                reader.close();
            }
        }, plugin.getExecutorService());
    }

    /**
     * Collects all unique players from items and transactions.
     */
    private Map<UUID, String> collectPlayers(List<V3AuctionItem> items, List<V3Transaction> transactions) {
        Map<UUID, String> players = new HashMap<>();

        for (V3AuctionItem item : items) {
            if (item.getSeller() != null && item.getSellerName() != null) {
                players.putIfAbsent(item.getSeller(), item.getSellerName());
            }
            if (item.getBuyer() != null) {
                players.putIfAbsent(item.getBuyer(), "Unknown");
            }
        }

        for (V3Transaction transaction : transactions) {
            if (transaction.getSeller() != null) {
                players.putIfAbsent(transaction.getSeller(), "Unknown");
            }
            if (transaction.getBuyer() != null) {
                players.putIfAbsent(transaction.getBuyer(), "Unknown");
            }
        }

        return players;
    }

    /**
     * Migrates players to V4 database.
     */
    private int migratePlayers(Map<UUID, String> players, AtomicInteger errors) {
        int migrated = 0;
        PlayerRepository playerRepo = plugin.getStorageManager().with(PlayerRepository.class);

        for (Map.Entry<UUID, String> entry : players.entrySet()) {
            try {
                playerRepo.upsertPlayer(entry.getKey(), entry.getValue());
                migrated++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate player " + entry.getKey() + ": " + e.getMessage());
                errors.incrementAndGet();
            }
        }

        return migrated;
    }

    /**
     * Migrates auction items to V4 database.
     */
    private int migrateItems(List<V3AuctionItem> items, AtomicInteger errors) {
        int migrated = 0;

        for (V3AuctionItem v3Item : items) {
            try {
                // Create item in ITEMS table
                int itemId = createItem(v3Item);
                if (itemId == -1) {
                    errors.incrementAndGet();
                    continue;
                }

                // Create auction item(s) in AUCTION_ITEMS table
                createAuctionItems(itemId, v3Item);

                migrated++;

                // Progress update every 100 items
                if (migrated % 100 == 0) {
                    progress("Migrated " + migrated + "/" + items.size() + " items...");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate item " + v3Item.getId() + ": " + e.getMessage());
                errors.incrementAndGet();
            }
        }

        return migrated;
    }

    /**
     * Creates an item in the V4 ITEMS table.
     */
    private int createItem(V3AuctionItem v3Item) {
        try {
            Schema schema = SchemaBuilder.insert(Tables.ITEMS, s -> {
                s.string("item_type", ItemType.AUCTION.name());
                s.uuid("seller_unique_id", v3Item.getSeller());
                if (v3Item.getBuyer() != null) {
                    s.uuid("buyer_unique_id", v3Item.getBuyer());
                }
                s.decimal("price", BigDecimal.valueOf(v3Item.getPrice()));
                s.string("economy_name", v3Item.getEconomy() != null ? v3Item.getEconomy() : "vault");
                s.string("storage_type", v3Item.getStorageType().toV4StorageType().name());
                s.string("server_name", v3Item.getServerName() != null ? v3Item.getServerName() : plugin.getConfiguration().getServerName());
                s.object("expired_at", new Date(v3Item.getExpireAt()));
            });

            return schema.execute(plugin.getStorageManager().with(PlayerRepository.class).getConnection(), logger);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create item: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Creates auction item entries in the V4 AUCTION_ITEMS table.
     * Handles both single items and multi-item (INVENTORY type) items.
     */
    private void createAuctionItems(int itemId, V3AuctionItem v3Item) {
        String itemstack = v3Item.getItemstack();

        if (v3Item.isInventoryType() && itemstack.contains(";")) {
            // Multi-item: split by semicolon
            String[] itemstacks = itemstack.split(";");
            for (String stack : itemstacks) {
                if (!stack.trim().isEmpty()) {
                    insertAuctionItem(itemId, stack.trim());
                }
            }
        } else {
            // Single item
            insertAuctionItem(itemId, itemstack);
        }
    }

    private void insertAuctionItem(int itemId, String itemstack) {
        try {
            Schema schema = SchemaBuilder.insert(Tables.AUCTION_ITEMS, s -> {
                s.object("item_id", itemId);
                s.string("itemstack", itemstack);
            });

            schema.execute(plugin.getStorageManager().with(PlayerRepository.class).getConnection(), logger);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create auction item for item_id " + itemId + ": " + e.getMessage());
        }
    }

    /**
     * Migrates V3 transactions to V4 logs and transactions tables.
     */
    private int migrateTransactions(List<V3Transaction> transactions, AtomicInteger errors) {
        int migrated = 0;

        for (V3Transaction v3Trans : transactions) {
            try {
                // Create a log entry for the purchase
                createLogEntry(v3Trans);

                // If money is still pending, create a transaction entry
                if (v3Trans.isNeedMoney()) {
                    createPendingTransaction(v3Trans);
                }

                migrated++;

                if (migrated % 100 == 0) {
                    progress("Migrated " + migrated + "/" + transactions.size() + " transactions...");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate transaction " + v3Trans.getId() + ": " + e.getMessage());
                errors.incrementAndGet();
            }
        }

        return migrated;
    }

    /**
     * Creates a log entry for a V3 transaction.
     */
    private void createLogEntry(V3Transaction v3Trans) {
        try {
            Schema schema = SchemaBuilder.insert(Tables.LOGS, s -> {
                s.string("log_type", LogType.PURCHASE.name());
                s.object("item_id", 0); // No direct mapping, use 0
                s.uuid("player_unique_id", v3Trans.getBuyer());
                s.uuid("target_unique_id", v3Trans.getSeller());
                s.string("itemstack", v3Trans.getItemstack());
                s.decimal("price", BigDecimal.valueOf(v3Trans.getPrice()));
                s.string("economy_name", v3Trans.getEconomy() != null ? v3Trans.getEconomy() : "vault");
                s.string("additional_data", "migrated_from_v3");
                if (v3Trans.isRead()) {
                    s.object("readed_at", new Date(v3Trans.getTransactionDate()));
                }
                s.object("created_at", new Date(v3Trans.getTransactionDate()));
            });

            schema.execute(plugin.getStorageManager().with(PlayerRepository.class).getConnection(), logger);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create log entry: " + e.getMessage());
        }
    }

    /**
     * Creates a pending transaction entry for unclaimed money.
     */
    private void createPendingTransaction(V3Transaction v3Trans) {
        try {
            Schema schema = SchemaBuilder.insert(Tables.TRANSACTIONS, s -> {
                s.object("item_id", 0);
                s.uuid("player_unique_id", v3Trans.getSeller());
                s.string("economy_name", v3Trans.getEconomy() != null ? v3Trans.getEconomy() : "vault");
                s.decimal("before", BigDecimal.ZERO);
                s.decimal("after", BigDecimal.ZERO);
                s.decimal("value", BigDecimal.valueOf(v3Trans.getPrice()));
                s.string("status", TransactionStatus.PENDING.name());
                s.object("created_at", new Date(v3Trans.getTransactionDate()));
            });

            schema.execute(plugin.getStorageManager().with(PlayerRepository.class).getConnection(), logger);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create pending transaction: " + e.getMessage());
        }
    }
}
