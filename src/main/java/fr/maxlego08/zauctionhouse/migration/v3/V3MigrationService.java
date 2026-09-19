package fr.maxlego08.zauctionhouse.migration.v3;

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
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;
import fr.maxlego08.zauctionhouse.migration.v3.reader.V3DataReader;
import fr.maxlego08.zauctionhouse.migration.v3.reader.V3JsonDataReader;
import fr.maxlego08.zauctionhouse.migration.v3.reader.V3SqlDataReader;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    private static final String UNKNOWN_PLAYER_NAME = "Unknown";

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
     * Connexion de destination (base V4), partagee par toutes les ecritures de la migration.
     * <p>
     * Evite de passer par {@code with(PlayerRepository.class).getConnection()} dans cinq
     * methodes distinctes : la connexion est celle du gestionnaire de stockage, pas celle
     * d'un repository particulier.
     *
     * @return la connexion JDBC de la base V4
     */
    private DatabaseConnection connection() {
        return plugin.getStorageManager().getDatabaseConnection();
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
            trackPlayer(players, item.getSeller(), item.getSellerName());
            trackPlayer(players, item.getBuyer(), null);
        }

        for (V3Transaction transaction : transactions) {
            trackPlayer(players, transaction.getSeller(), null);
            trackPlayer(players, transaction.getBuyer(), null);
        }

        return players;
    }

    /**
     * Un vrai pseudo l'emporte TOUJOURS sur le placeholder, quel que soit l'ordre de parcours.
     * <p>
     * Avec {@code putIfAbsent}, un joueur rencontre d'abord comme ACHETEUR figeait "Unknown"
     * alors qu'on connaissait son pseudo en tant que vendeur un peu plus loin dans la liste.
     * Les hooks freres le font deja correctement (CrazyAuctionsMigrationService.trackPlayer).
     *
     * @param players  la table de collecte, mutee sur place
     * @param uniqueId l'identifiant du joueur, ignore quand il est {@code null}
     * @param name     le pseudo connu, ou {@code null} quand la source ne le porte pas
     */
    private void trackPlayer(Map<UUID, String> players, UUID uniqueId, String name) {
        if (uniqueId == null) return;

        String resolved = (name != null && !name.isBlank()) ? name : UNKNOWN_PLAYER_NAME;
        String previous = players.get(uniqueId);

        if (previous == null || (UNKNOWN_PLAYER_NAME.equals(previous) && !UNKNOWN_PLAYER_NAME.equals(resolved))) {
            players.put(uniqueId, resolved);
        }
    }

    /**
     * Migrates players to V4 database.
     */
    private int migratePlayers(Map<UUID, String> players, AtomicInteger errors) {
        int migrated = 0;
        PlayerRepository playerRepo = plugin.getStorageManager().with(PlayerRepository.class);

        // Le pseudo V4 vient de PlayerListener.onConnect : il est TOUJOURS plus fiable que le
        // placeholder de la migration. upsertPlayer ecrasait la ligne existante et remplacait
        // le pseudo reel d'un joueur actif par "Unknown" (C-108).
        //
        // ATTENTION : je REJETTE le correctif propose par l'audit
        // (`if ("Unknown".equals(entry.getValue())) continue;`). Sauter l'INSERTION d'un UUID
        // inconnu casse deux invariants verifies : items.buyer_unique_id porte une FK vers
        // players (CreateItemMigration:15), donc sous MySQL l'item entier est rejete ; et
        // ItemLoaderUtils met desormais l'item en quarantaine si son vendeur manque de la table.
        // Les UUID inconnus DOIVENT donc etre inseres, meme sous "Unknown".
        Set<UUID> knownPlayers = playerRepo.select().stream().map(PlayerDTO::unique_id).collect(Collectors.toSet());

        for (Map.Entry<UUID, String> entry : players.entrySet()) {

            if (knownPlayers.contains(entry.getKey())) continue;

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

            int itemId = -1;
            try {
                // V3SqlDataReader.parseItem ne controle NI le vendeur NI l'itemstack : une seule
                // ligne V3 corrompue produisait soit une NPE dans SchemaBuilder.uuid (value.toString()),
                // soit un INSERT enfant avec un itemstack null, donc une annonce vide et VENDABLE.
                if (v3Item.getSeller() == null) {
                    plugin.getLogger().warning("Skipping V3 item " + v3Item.getId() + ": no seller");
                    errors.incrementAndGet();
                    continue;
                }
                if (v3Item.getItemstack() == null || v3Item.getItemstack().isBlank()) {
                    plugin.getLogger().warning("Skipping V3 item " + v3Item.getId() + ": empty itemstack payload");
                    errors.incrementAndGet();
                    continue;
                }

                itemId = createItem(v3Item);

                // InsertRequest rend 0 (et non -1) quand le pilote ne remonte aucune cle generee :
                // la garde `itemId == -1` etait morte deux fois, le catch qui rendait -1 l'etant
                // lui aussi (Sarah leve une DatabaseException runtime, jamais une SQLException).
                if (itemId <= 0) {
                    plugin.getLogger().warning("Failed to create the parent row for V3 item " + v3Item.getId());
                    errors.incrementAndGet();
                    continue;
                }

                createAuctionItems(itemId, v3Item);

                migrated++;

                if (migrated % 100 == 0) {
                    progress("Migrated " + migrated + "/" + items.size() + " items...");
                }
            } catch (Exception e) {
                // COMPENSATION. La ligne parente est deja commitee : sans ce DELETE, l'annonce
                // reste VENDABLE au prix plein avec un lot ampute ou vide (C-075). La FK
                // auction_items.item_id est ON DELETE CASCADE, les contenus deja inseres partent
                // avec la ligne parente.
                errors.incrementAndGet();
                plugin.getLogger().severe("Failed to migrate V3 item " + v3Item.getId() + " (V4 id " + itemId + "): " + e.getMessage());
                if (itemId > 0) {
                    deleteOrphanItem(itemId);
                }
            }
        }

        return migrated;
    }

    /**
     * Supprime la ligne %prefix%items orpheline laissee par un lot partiellement insere.
     *
     * @param itemId identifiant V4 de la ligne parente a compenser
     */
    private void deleteOrphanItem(int itemId) {
        try {
            SchemaBuilder.delete(Tables.ITEMS).where("id", itemId).execute(connection(), logger);
            plugin.getLogger().warning("Rolled back the orphan V4 item row #" + itemId);
        } catch (Exception e) {
            // Trace exploitable : sans l'id V4 exact, l'exploitant n'a AUCUN moyen de retrouver
            // l'annonce a supprimer a la main.
            plugin.getLogger().severe("MANUAL ACTION REQUIRED: the orphan V4 item row #" + itemId
                    + " could not be deleted (" + e.getMessage() + "). It is currently listed for sale "
                    + "with a truncated or empty content.");
        }
    }

    /**
     * Creates an item in the V4 ITEMS table.
     * <p>
     * Le {@code catch (SQLException)} d'origine etait du CODE MORT : Sarah leve une
     * {@code DatabaseException extends SarahException extends RuntimeException}. L'echec
     * remontait donc quand meme, mais au parent, apres avoir fait croire au contraire.
     *
     * @param v3Item la ligne V3 a importer
     * @return l'identifiant genere de la ligne parente
     * @throws SQLException si le pilote refuse l'insertion
     */
    private int createItem(V3AuctionItem v3Item) throws SQLException {
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

        return schema.execute(connection(), logger);
    }

    /**
     * Creates auction item entries in the V4 AUCTION_ITEMS table.
     * Handles both single items and multi-item (INVENTORY type) items.
     *
     * @param itemId identifiant de la ligne parente
     * @param v3Item la ligne V3 a importer
     * @throws SQLException si le pilote refuse une insertion
     */
    private void createAuctionItems(int itemId, V3AuctionItem v3Item) throws SQLException {
        String itemstack = v3Item.getItemstack();
        int inserted = 0;

        if (v3Item.isInventoryType() && itemstack.contains(";")) {
            // Multi-item: split by semicolon
            for (String stack : itemstack.split(";")) {
                if (!stack.trim().isEmpty()) {
                    insertAuctionItem(itemId, stack.trim());
                    inserted++;
                }
            }
        } else {
            // Single item
            insertAuctionItem(itemId, itemstack);
            inserted++;
        }

        // Un lot V3 dont la charge utile vaut exactement ";" produit `";".split(";")` = tableau
        // VIDE : ZERO contenu insere, ZERO exception, item compte comme migre, annonce vendable
        // au prix plein pour un lot vide. C'est le declencheur le plus certain de C-075.
        if (inserted == 0) {
            throw new IllegalStateException("no readable ItemStack in the V3 payload for item_id " + itemId);
        }
    }

    /**
     * Insere un contenu unique de l'annonce.
     *
     * @param itemId    identifiant de la ligne parente
     * @param itemstack charge utile serialisee, jamais vide
     * @throws SQLException si le pilote refuse l'insertion
     */
    private void insertAuctionItem(int itemId, String itemstack) throws SQLException {
        if (itemstack == null || itemstack.isBlank()) {
            throw new IllegalStateException("empty itemstack payload for item_id " + itemId);
        }

        SchemaBuilder.insert(Tables.AUCTION_ITEMS, s -> {
            s.object("item_id", itemId);
            s.string("itemstack", itemstack);
        }).execute(connection(), logger);
    }

    /**
     * Migrates V3 transactions to V4 logs and transactions tables.
     */
    private int migrateTransactions(List<V3Transaction> transactions, AtomicInteger errors) {
        int migrated = 0;

        for (V3Transaction v3Trans : transactions) {
            try {
                if (v3Trans.getSeller() == null || v3Trans.getBuyer() == null) {
                    // SchemaBuilder.uuid fait value.toString() : un UUID null partait en NPE.
                    plugin.getLogger().warning("Skipping V3 transaction " + v3Trans.getId() + ": missing seller or buyer");
                    errors.incrementAndGet();
                    continue;
                }

                // item_id = 0 violait la cle etrangere logs/transactions -> items sous MySQL
                // (CreateLogsMigration:12 et CreateTransactionsMigration:12 declarent bien la FK,
                // et SchemaBuilder l'emet en InnoDB) : l'insert levait une DatabaseException que
                // le catch(SQLException) ne voyait pas, et TOUT l'argent PENDING V3 etait perdu.
                // Sous SQLite, ou Sarah n'emet aucun PRAGMA foreign_keys=ON, l'insert passait :
                // d'ou une bascule de comportement selon le backend, invisible a l'admin (C-033).
                //
                // On cree une vraie ligne %prefix%items en storage_type DELETED : jamais chargee
                // (ItemRepository.select et select(int) filtrent DELETED), mais elle satisfait la FK.
                // Je REJETTE le correctif de l'audit (rendre item_id nullable) : MigrationManager
                // de Sarah ne sait qu'AJOUTER des colonnes manquantes, il n'emet jamais
                // d'ALTER ... MODIFY, le correctif serait inoperant sur les installations existantes.
                int sentinelItemId = createSentinelItem(v3Trans);
                if (sentinelItemId <= 0) {
                    plugin.getLogger().severe("Failed to create the sentinel row for V3 transaction " + v3Trans.getId() + ", skipping it");
                    errors.incrementAndGet();
                    continue;
                }

                // L'ARGENT D'ABORD. L'ordre d'origine ecrivait l'historique en premier : un
                // echec de log faisait sauter le `continue` implicite et les gains en attente
                // du vendeur n'etaient jamais importes. Chacun a desormais son propre try/catch.
                if (v3Trans.isNeedMoney()) {
                    try {
                        createPendingTransaction(sentinelItemId, v3Trans);
                    } catch (Exception e) {
                        plugin.getLogger().severe("MONEY LOST: failed to import the pending money of V3 transaction "
                                + v3Trans.getId() + " (seller " + v3Trans.getSeller() + ", amount " + v3Trans.getPrice()
                                + "): " + e.getMessage());
                        errors.incrementAndGet();
                    }
                }

                try {
                    createLogEntry(sentinelItemId, v3Trans);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to import the history of V3 transaction " + v3Trans.getId() + ": " + e.getMessage());
                    errors.incrementAndGet();
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
     * Cree la ligne %prefix%items porteuse des FK de logs/transactions pour une transaction V3.
     * <p>
     * La V3 n'a aucun item correspondant : la ligne est ecrite directement en
     * {@link StorageType#DELETED}, donc invisible en jeu, mais reelle pour la base.
     * La FK etant ON DELETE CASCADE, purger ces lignes effacerait aussi les logs et
     * transactions associes -- c'est le comportement voulu.
     *
     * @param v3Trans la transaction V3 a importer
     * @return l'identifiant genere de la ligne sentinelle
     * @throws SQLException si le pilote refuse l'insertion
     */
    private int createSentinelItem(V3Transaction v3Trans) throws SQLException {
        return SchemaBuilder.insert(Tables.ITEMS, s -> {
            s.string("item_type", ItemType.AUCTION.name());
            s.uuid("seller_unique_id", v3Trans.getSeller());
            s.uuid("buyer_unique_id", v3Trans.getBuyer());
            s.decimal("price", BigDecimal.valueOf(v3Trans.getPrice()));
            s.string("economy_name", v3Trans.getEconomy() != null ? v3Trans.getEconomy() : "vault");
            s.string("storage_type", StorageType.DELETED.name());
            s.string("server_name", plugin.getConfiguration().getServerName());
            s.object("expired_at", new Date(v3Trans.getTransactionDate()));
            s.object("created_at", new Date(v3Trans.getTransactionDate()));
        }).execute(connection(), logger);
    }

    /**
     * Creates a log entry for a V3 transaction.
     *
     * @param itemId   identifiant de la ligne sentinelle porteuse de la FK
     * @param v3Trans  la transaction V3 a importer
     * @throws SQLException si le pilote refuse l'insertion
     */
    private void createLogEntry(int itemId, V3Transaction v3Trans) throws SQLException {
        SchemaBuilder.insert(Tables.LOGS, s -> {
            s.string("log_type", LogType.PURCHASE.name());
            s.object("item_id", itemId);
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
        }).execute(connection(), logger);
    }

    /**
     * Creates a pending transaction entry for unclaimed money.
     *
     * @param itemId   identifiant de la ligne sentinelle porteuse de la FK
     * @param v3Trans  la transaction V3 a importer
     * @throws SQLException si le pilote refuse l'insertion
     */
    private void createPendingTransaction(int itemId, V3Transaction v3Trans) throws SQLException {
        SchemaBuilder.insert(Tables.TRANSACTIONS, s -> {
            s.object("item_id", itemId);
            s.uuid("player_unique_id", v3Trans.getSeller());
            s.string("economy_name", v3Trans.getEconomy() != null ? v3Trans.getEconomy() : "vault");
            s.decimal("before", BigDecimal.ZERO);
            s.decimal("after", BigDecimal.ZERO);
            s.decimal("value", BigDecimal.valueOf(v3Trans.getPrice()));
            s.string("status", TransactionStatus.PENDING.name());
            s.object("created_at", new Date(v3Trans.getTransactionDate()));
        }).execute(connection(), logger);
    }
}
