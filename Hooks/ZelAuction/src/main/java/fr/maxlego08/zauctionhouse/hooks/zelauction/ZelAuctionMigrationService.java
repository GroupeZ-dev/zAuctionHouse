package fr.maxlego08.zauctionhouse.hooks.zelauction;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.SchemaBuilder;
import fr.maxlego08.sarah.database.Schema;
import fr.maxlego08.sarah.logger.JULogger;
import fr.maxlego08.sarah.logger.Logger;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service that handles the actual ZelAuction data migration process.
 * <p>
 * Reads items and transactions directly from the ZelAuction database (SQLite or MySQL)
 * by reading the database.yml configuration from the ZelAuction plugin folder.
 * </p>
 */
public class ZelAuctionMigrationService {

    private final AuctionPlugin plugin;
    private final Logger logger;
    private Consumer<String> progressCallback;

    public ZelAuctionMigrationService(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.logger = JULogger.from(plugin.getLogger());
    }

    public void onProgress(Consumer<String> callback) {
        this.progressCallback = callback;
    }

    public CompletableFuture<ZelMigrationResult> migrate() {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            AtomicInteger errors = new AtomicInteger(0);
            Connection connection = null;

            try {
                // Read ZelAuction database configuration
                File zelAuctionFolder = new File("plugins/ZelAuction");
                File databaseYml = new File(zelAuctionFolder, "database.yml");

                if (!databaseYml.exists()) {
                    return ZelMigrationResult.failure("ZelAuction database.yml not found at: " + databaseYml.getAbsolutePath());
                }

                YamlConfiguration dbConfig = YamlConfiguration.loadConfiguration(databaseYml);
                String databaseType = dbConfig.getString("Database-Type", "SQLite");

                // Connect to ZelAuction database
                progress("Connecting to ZelAuction " + databaseType + " database...");
                connection = createConnection(databaseType, dbConfig, zelAuctionFolder);

                if (connection == null || connection.isClosed()) {
                    return ZelMigrationResult.failure("Failed to connect to ZelAuction database");
                }
                progress("Connection successful!");

                // Count data
                int productCount = countRows(connection, "products");
                int mailboxCount = countRows(connection, "mailbox");
                int transactionCount = countRows(connection, "transactions");
                progress("Found " + productCount + " products, " + mailboxCount + " mailbox items, " + transactionCount + " transactions");

                if (productCount == 0 && mailboxCount == 0 && transactionCount == 0) {
                    return ZelMigrationResult.failure("No data found to migrate");
                }

                // V4 database connection for inserts
                DatabaseConnection v4Connection = plugin.getStorageManager().getDatabaseConnection();

                // Collect all unique players
                Map<UUID, String> players = new HashMap<>();

                // Migrate products (listed items)
                progress("Migrating products (listed items)...");
                int itemsMigrated = migrateProducts(connection, v4Connection, players, errors);
                progress("Migrated " + itemsMigrated + " products");

                // Migrate mailbox (purchased items awaiting claim)
                progress("Migrating mailbox (purchased items)...");
                int mailboxMigrated = migrateMailbox(connection, v4Connection, players, errors);
                progress("Migrated " + mailboxMigrated + " mailbox items");

                // Migrate transactions
                progress("Migrating transactions...");
                int transactionsMigrated = migrateTransactions(connection, v4Connection, players, errors);
                progress("Migrated " + transactionsMigrated + " transactions");

                long duration = System.currentTimeMillis() - startTime;
                progress("Migration completed in " + duration + "ms");

                return ZelMigrationResult.success(players.size(), itemsMigrated + mailboxMigrated, transactionsMigrated, errors.get(), duration);

            } catch (Exception exception) {
                this.plugin.getLogger().severe("Migration failed: " + exception.getMessage());
                exception.printStackTrace();
                return ZelMigrationResult.failure("Migration failed: " + exception.getMessage());
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }, plugin.getExecutorService());
    }

    private Connection createConnection(String databaseType, YamlConfiguration dbConfig, File zelAuctionFolder) throws SQLException {
        if (databaseType.equalsIgnoreCase("SQLite")) {
            File dbFile = findSqliteFile(zelAuctionFolder);
            if (dbFile == null) {
                throw new SQLException("No SQLite database file found in " + zelAuctionFolder.getAbsolutePath());
            }
            progress("Using SQLite database: " + dbFile.getName());
            return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } else {
            String host = dbConfig.getString("MySQL.host", "localhost");
            int port = dbConfig.getInt("MySQL.port", 3306);
            String database = dbConfig.getString("MySQL.database", "zelauction");
            String user = dbConfig.getString("MySQL.user", "root");
            String password = dbConfig.getString("MySQL.password", "");
            boolean useSsl = dbConfig.getBoolean("MySQL.use-ssl", false);

            String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl + "&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
            return DriverManager.getConnection(jdbcUrl, user, password);
        }
    }

    private File findSqliteFile(File folder) {
        File[] dbFiles = folder.listFiles((dir, name) -> name.endsWith(".db"));
        if (dbFiles != null && dbFiles.length > 0) {
            return dbFiles[0];
        }
        return null;
    }

    private int countRows(Connection connection, String table) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not count rows in " + table + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Migrates products table -> LISTED items in V4.
     */
    private int migrateProducts(Connection zelConnection, DatabaseConnection v4Connection, Map<UUID, String> players, AtomicInteger errors) {
        int migrated = 0;
        String sql = "SELECT uuid, item, seller, sellerName, price, date FROM products";

        try (PreparedStatement stmt = zelConnection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String itemData = rs.getString("item");
                    UUID sellerUuid = UUID.fromString(rs.getString("seller"));
                    String sellerName = rs.getString("sellerName");
                    double price = rs.getDouble("price");
                    long date = rs.getLong("date");

                    trackPlayer(v4Connection, players, sellerUuid, sellerName);

                    String v4Itemstack = convertItemStack(itemData);
                    if (v4Itemstack == null) {
                        plugin.getLogger().warning("Failed to convert itemstack for product, skipping");
                        errors.incrementAndGet();
                        continue;
                    }

                    // Default 48h expiration from listing date
                    long expireAt = date > 0 ? date + (48L * 60 * 60 * 1000) : System.currentTimeMillis() + (48L * 60 * 60 * 1000);

                    int itemId = createItem(v4Connection, sellerUuid, null, price, StorageType.LISTED, expireAt);
                    if (itemId == -1) {
                        errors.incrementAndGet();
                        continue;
                    }

                    insertAuctionItem(v4Connection, itemId, v4Itemstack);
                    migrated++;

                    if (migrated % 100 == 0) {
                        progress("Migrated " + migrated + " products...");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to migrate product: " + e.getMessage());
                    errors.incrementAndGet();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to read products table: " + e.getMessage());
        }

        return migrated;
    }

    /**
     * Migrates mailbox table -> PURCHASED items in V4.
     * In ZelAuction, mailbox contains items bought by a player waiting to be claimed.
     */
    private int migrateMailbox(Connection zelConnection, DatabaseConnection v4Connection, Map<UUID, String> players, AtomicInteger errors) {
        int migrated = 0;
        String sql = "SELECT uuid, item, seller, price FROM mailbox";

        try (PreparedStatement stmt = zelConnection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String itemData = rs.getString("item");
                    UUID sellerUuid = UUID.fromString(rs.getString("seller"));
                    double price = rs.getDouble("price");

                    trackPlayer(v4Connection, players, sellerUuid, null);

                    String v4Itemstack = convertItemStack(itemData);
                    if (v4Itemstack == null) {
                        this.plugin.getLogger().warning("Failed to convert itemstack for mailbox item, skipping");
                        errors.incrementAndGet();
                        continue;
                    }

                    int itemId = createItem(v4Connection, sellerUuid, null, price, StorageType.EXPIRED, System.currentTimeMillis());
                    if (itemId == -1) {
                        errors.incrementAndGet();
                        continue;
                    }

                    insertAuctionItem(v4Connection, itemId, v4Itemstack);
                    migrated++;

                    if (migrated % 100 == 0) {
                        progress("Migrated " + migrated + " mailbox items...");
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to migrate mailbox item: " + e.getMessage());
                    errors.incrementAndGet();
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Failed to read mailbox table: " + e.getMessage());
        }

        return migrated;
    }

    /**
     * Migrates transactions table -> LOGS in V4.
     */
    private int migrateTransactions(Connection zelConnection, DatabaseConnection v4Connection, Map<UUID, String> players, AtomicInteger errors) {
        int migrated = 0;
        String sql = "SELECT uuid, item, transactionType, fromUUID, fromUsername, toUsername, price, date FROM transactions";

        try (PreparedStatement stmt = zelConnection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    String itemData = rs.getString("item");
                    String transactionType = rs.getString("transactionType");
                    UUID fromUuid = UUID.fromString(rs.getString("fromUUID"));
                    String fromUsername = rs.getString("fromUsername");
                    String toUsername = rs.getString("toUsername");
                    double price = rs.getDouble("price");
                    long date = rs.getLong("date");

                    trackPlayer(v4Connection, players, fromUuid, fromUsername);

                    var offlinePlayer = Bukkit.getOfflinePlayer(toUsername);
                    trackPlayer(v4Connection, players, offlinePlayer.getUniqueId(), toUsername);

                    String v4Itemstack = convertItemStack(itemData);
                    if (v4Itemstack == null) {
                        this.plugin.getLogger().warning("Failed to convert itemstack for transaction, skipping");
                        errors.incrementAndGet();
                        continue;
                    }

                    LogType logType = "BUY".equalsIgnoreCase(transactionType) ? LogType.PURCHASE : LogType.SALE;
                    // createLogEntry leve desormais SQLException / IllegalStateException au lieu
                    // d'avaler l'echec : la ligne n'est PAS comptee comme migree et le
                    // catch(Exception) ci-dessous la remonte a l'exploitant (C-033).
                    createLogEntry(v4Connection, fromUuid, v4Itemstack, price, logType, date);
                    migrated++;

                    if (migrated % 100 == 0) {
                        progress("Migrated " + migrated + " transactions...");
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to migrate transaction: " + e.getMessage());
                    errors.incrementAndGet();
                }
            }
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Failed to read transactions table: " + e.getMessage());
        }

        return migrated;
    }

    /**
     * Converts a ZelAuction Base64 itemstack (legacy Bukkit serialization without GZIP)
     * to the V4 format (Base64 with GZIP compression).
     */
    private String convertItemStack(String zelAuctionData) {
        ItemStack itemStack = Serialize.deserializeLegacy(zelAuctionData, plugin);
        if (itemStack == null) return null;
        return Base64ItemStack.encode(itemStack);
    }

    private int createItem(DatabaseConnection v4Connection, UUID sellerUuid, UUID buyerUuid, double price, StorageType storageType, long expireAt) {
        try {
            Schema schema = SchemaBuilder.insert(Tables.ITEMS, s -> {
                s.string("item_type", ItemType.AUCTION.name());
                s.uuid("seller_unique_id", sellerUuid);
                if (buyerUuid != null) {
                    s.uuid("buyer_unique_id", buyerUuid);
                }
                s.decimal("price", BigDecimal.valueOf(price));
                s.string("economy_name", "vault");
                s.string("storage_type", storageType.name());
                s.string("server_name", plugin.getConfiguration().getServerName());
                s.object("expired_at", new Date(expireAt));
            });

            return schema.execute(v4Connection, logger);
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to create item: " + e.getMessage());
            return -1;
        }
    }

    private void insertAuctionItem(DatabaseConnection v4Connection, int itemId, String itemstack) {
        try {
            Schema schema = SchemaBuilder.insert(Tables.AUCTION_ITEMS, s -> {
                s.object("item_id", itemId);
                s.string("itemstack", itemstack);
            });

            schema.execute(v4Connection, logger);
        } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to create auction item for item_id " + itemId + ": " + e.getMessage());
        }
    }

    /**
     * Cree la ligne {@code %prefix%items} porteuse de la cle etrangere de {@code %prefix%logs}
     * pour une transaction ZelAuction.
     * <p>
     * ZelAuction n'a aucun item V4 correspondant : la ligne est ecrite directement en
     * {@link StorageType#DELETED}, donc jamais chargee en jeu (ItemRepository filtre DELETED),
     * mais bien reelle pour la base. La cle etrangere etant ON DELETE CASCADE, purger ces
     * lignes effacerait aussi les logs associes -- c'est le comportement voulu.
     * <p>
     * Le vendeur doit deja exister dans {@code %prefix%players} (la colonne
     * {@code seller_unique_id} porte elle-meme une cle etrangere) : migrateTransactions
     * appelle trackPlayer AVANT createLogEntry, cet invariant est donc satisfait.
     *
     * @param v4Connection connexion a la base V4
     * @param sellerUuid   vendeur porteur de la ligne sentinelle
     * @param price        prix de la transaction ZelAuction
     * @param date         horodatage de la transaction ZelAuction
     * @return l'identifiant genere de la ligne sentinelle
     * @throws SQLException si le pilote refuse l'insertion
     */
    private int createSentinelItem(DatabaseConnection v4Connection, UUID sellerUuid, double price, long date) throws SQLException {
        return SchemaBuilder.insert(Tables.ITEMS, s -> {
            s.string("item_type", ItemType.AUCTION.name());
            s.uuid("seller_unique_id", sellerUuid);
            s.decimal("price", BigDecimal.valueOf(price));
            s.string("economy_name", "vault");
            s.string("storage_type", StorageType.DELETED.name());
            s.string("server_name", plugin.getConfiguration().getServerName());
            s.object("expired_at", new Date(date));
            s.object("created_at", new Date(date));
        }).execute(v4Connection, logger);
    }

    /**
     * Ecrit l'entree d'historique V4 correspondant a une transaction ZelAuction.
     * <p>
     * L'ancienne version ecrivait {@code item_id = 0}, ce qui violait la cle etrangere
     * {@code logs -> items} declaree par CreateLogsMigration : sous MySQL (InnoDB) l'insert
     * levait une DatabaseException que le {@code catch(SQLException)} d'alors ne voyait meme
     * pas, et TOUT l'historique ZelAuction etait perdu en silence. Sous SQLite, ou Sarah
     * n'emet aucun {@code PRAGMA foreign_keys=ON}, l'insert passait : le comportement
     * basculait donc selon le backend, sans aucun signal pour l'exploitant (C-033).
     * <p>
     * On cree desormais une vraie ligne sentinelle en {@link StorageType#DELETED} qui
     * satisfait la cle etrangere sans aucun changement de schema. Rendre {@code item_id}
     * nullable serait inoperant : le MigrationManager de Sarah ne sait qu'AJOUTER des
     * colonnes manquantes, il n'emet jamais d'{@code ALTER ... MODIFY} sur les
     * installations existantes.
     *
     * @param v4Connection connexion a la base V4
     * @param playerUuid   joueur a l'origine de la ligne d'historique
     * @param itemstack    itemstack deja converti au format V4
     * @param price        prix de la transaction ZelAuction
     * @param logType      type de log (PURCHASE ou SALE)
     * @param date         horodatage de la transaction ZelAuction
     * @throws SQLException     si le pilote refuse l'une des deux insertions
     * @throws IllegalStateException si la ligne sentinelle n'a pas pu etre creee : on
     *                               n'ecrit JAMAIS un log orphelin en repli
     */
    private void createLogEntry(DatabaseConnection v4Connection, UUID playerUuid, String itemstack, double price, LogType logType, long date) throws SQLException {
        int sentinelItemId = createSentinelItem(v4Connection, playerUuid, price, date);
        if (sentinelItemId <= 0) {
            throw new IllegalStateException("unable to create the sentinel item row for the ZelAuction log");
        }
        SchemaBuilder.insert(Tables.LOGS, s -> {
            s.string("log_type", logType.name());
            s.object("item_id", sentinelItemId);
            s.uuid("player_unique_id", playerUuid);
            s.string("itemstack", itemstack);
            s.decimal("price", BigDecimal.valueOf(price));
            s.string("economy_name", "vault");
            s.string("additional_data", "migrated_from_zelauction");
            s.object("created_at", new Date(date));
        }).execute(v4Connection, logger);
    }

    /**
     * Tracks a player UUID→name mapping, always preferring a real name over "Unknown".
     * Creates or updates the player directly in the V4 database and adds them to the local map.
     */
    private void trackPlayer(DatabaseConnection v4Connection, Map<UUID, String> players, UUID uuid, String name) {
        String resolvedName = (name != null && !name.isBlank()) ? name : "Unknown";
        String previous = players.get(uuid);
        // Only upsert if this is a new player or we have a better name
        if (previous == null || ("Unknown".equals(previous) && !"Unknown".equals(resolvedName))) {
            players.put(uuid, resolvedName);
            try {
                SchemaBuilder.upsert(Tables.PLAYERS, s -> {
                    s.uuid("unique_id", uuid).primary();
                    s.string("name", resolvedName);
                }).execute(v4Connection, logger);
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to upsert player " + uuid + ": " + e.getMessage());
            }
        }
    }

    private void progress(String message) {
        this.plugin.getLogger().info("[Migration] " + message);
        if (this.progressCallback != null) {
            this.progressCallback.accept(message);
        }
    }
}
