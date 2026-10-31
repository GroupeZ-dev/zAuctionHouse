package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Montee de version d'une base DEJA MIGREE par une version anterieure du plugin.
 * <p>
 * Le socle n'execute PAS les migrations au demarrage : chaque test fabrique d'abord en SQL brut
 * une base "ancienne" (tables sans leurs colonnes recentes, table de suivi deja renseignee), puis
 * lance les migrations de production. Attendu :
 * <ul>
 *   <li>les migrations en {@code createOrAlter} AJOUTENT les colonnes manquantes sans toucher
 *   aux lignes existantes ;</li>
 *   <li>les migrations en {@code create()} deja enregistrees ne font RIEN (MigrationManager
 *   deduplique par nom) : une table ancienne n'y gagne aucune colonne ;</li>
 *   <li>les migrations absentes de la table de suivi s'executent normalement.</li>
 * </ul>
 * Le DDL "ancien" utilise {@code AUTOINCREMENT} : ces tests sont propres a SQLite.
 */
class SchemaUpgradeTest extends DatabaseTestBase {

    private static final List<String> ITEM_COLUMNS = List.of("id", "item_type", "seller_unique_id", "buyer_unique_id", "price",
            "economy_name", "storage_type", "server_name", "expired_at", "pending_publish", "created_at", "updated_at");
    private static final List<String> TRANSACTION_COLUMNS = List.of("id", "item_id", "player_unique_id", "economy_name", "before",
            "after", "value", "status", "claim_token", "claim_reserved_at", "created_at", "updated_at");

    private boolean runProductionMigrations = false;

    /**
     * Le socle appelle cette methode au demarrage : rien ne doit se passer tant que la base
     * "ancienne" n'a pas ete fabriquee. {@link #upgrade()} la reactive.
     */
    @Override
    protected void registerAndRunMigrations() {
        if (this.runProductionMigrations) {
            super.registerAndRunMigrations();
        }
    }

    @BeforeEach
    void requireSqlite() {
        assumeTrue(isSqlite(), "le DDL ancien (AUTOINCREMENT) est propre a SQLite");
    }

    private void upgrade() {
        this.runProductionMigrations = true;
        registerAndRunMigrations();
    }

    // ------------------------------------------------------------------------------------------
    // Fabrique de base "ancienne"
    // ------------------------------------------------------------------------------------------

    /** Table de suivi telle que Sarah la cree, avec les migrations "deja appliquees". */
    private void migrationTableWith(String... appliedMigrations) throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " (`migration` TEXT NOT NULL, `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        for (String name : appliedMigrations) {
            execute("INSERT INTO " + MIGRATION_TABLE + " (`migration`) VALUES (?)", name);
        }
    }

    /** items SANS pending_publish. */
    private void legacyItemsTable() throws SQLException {
        execute("CREATE TABLE " + Tables.ITEMS + " ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "`item_type` VARCHAR(255) NOT NULL, "
                + "`seller_unique_id` VARCHAR(36) NOT NULL, "
                + "`buyer_unique_id` VARCHAR(36) NULL, "
                + "`price` DECIMAL(65,2) NOT NULL, "
                + "`economy_name` VARCHAR(255) NOT NULL, "
                + "`storage_type` TEXT NOT NULL, "
                + "`server_name` VARCHAR(255) NOT NULL, "
                + "`expired_at` TIMESTAMP NOT NULL, "
                + "`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    private int legacyItem(UUID seller, BigDecimal price, long expiredAt) throws SQLException {
        execute("INSERT INTO " + Tables.ITEMS + " (`item_type`, `seller_unique_id`, `price`, `economy_name`, `storage_type`, `server_name`, `expired_at`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "AUCTION", seller.toString(), price, "vault", StorageType.LISTED.name(), "legacy-server", expiredAt);
        return (int) queryLong("SELECT MAX(id) FROM " + Tables.ITEMS);
    }

    /** transactions SANS claim_token ni claim_reserved_at (ou avec claim_token seul). */
    private void legacyTransactionsTable(boolean withClaimToken) throws SQLException {
        execute("CREATE TABLE " + Tables.TRANSACTIONS + " ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "`item_id` INT NOT NULL, "
                + "`player_unique_id` VARCHAR(36) NOT NULL, "
                + "`economy_name` VARCHAR(255) NOT NULL, "
                + "`before` DECIMAL(65,2) NOT NULL, "
                + "`after` DECIMAL(65,2) NOT NULL, "
                + "`value` DECIMAL(65,2) NOT NULL, "
                + "`status` VARCHAR(32) NOT NULL, "
                + (withClaimToken ? "`claim_token` VARCHAR(36) NULL, " : "")
                + "`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    private int legacyTransaction(int itemId, UUID player, BigDecimal value) throws SQLException {
        execute("INSERT INTO " + Tables.TRANSACTIONS + " (`item_id`, `player_unique_id`, `economy_name`, `before`, `after`, `value`, `status`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                itemId, player.toString(), "vault", BigDecimal.ZERO, value, value, TransactionStatus.PENDING.name());
        return (int) queryLong("SELECT MAX(id) FROM " + Tables.TRANSACTIONS);
    }

    /** logs SANS additional_data ni readed_at : une table create() d'une version anterieure. */
    private void legacyLogsTable() throws SQLException {
        execute("CREATE TABLE " + Tables.LOGS + " ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "`item_id` INT NOT NULL, "
                + "`log_type` VARCHAR(255) NOT NULL, "
                + "`player_unique_id` VARCHAR(36) NOT NULL, "
                + "`target_unique_id` VARCHAR(36) NULL, "
                + "`itemstack` LONGTEXT NULL, "
                + "`price` DECIMAL(65,2) NOT NULL DEFAULT 0, "
                + "`economy_name` VARCHAR(255) NULL, "
                + "`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "`updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    /** migration_state avec seulement les trois premieres colonnes. */
    private void legacyMigrationStateTable() throws SQLException {
        execute("CREATE TABLE " + Tables.MIGRATION_STATE + " ("
                + "`provider_id` VARCHAR(64) NOT NULL, "
                + "`server_name` VARCHAR(255) NOT NULL, "
                + "`migrated_at` BIGINT NOT NULL, "
                + "PRIMARY KEY (`provider_id`))");
    }

    private Set<String> indexNames() throws SQLException {
        Set<String> names = new HashSet<>();
        for (Object[] row : queryRows("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%'")) {
            names.add((String) row[0]);
        }
        return names;
    }

    // ------------------------------------------------------------------------------------------
    // createOrAlter : colonnes ajoutees, lignes conservees
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("items et transactions anciens : les colonnes manquantes sont ajoutees, les lignes et leurs valeurs conservees")
    void upgrade_addsMissingColumnsAndKeepsRows() throws Exception {
        legacyItemsTable();
        legacyTransactionsTable(false);
        migrationTableWith("CreateItemMigration", "CreateTransactionsMigration");
        UUID seller = UUID.nameUUIDFromBytes("legacy-seller".getBytes());
        long expiredAt = System.currentTimeMillis() + 60_000L;
        int firstItem = legacyItem(seller, new BigDecimal("12.50"), expiredAt);
        int secondItem = legacyItem(seller, new BigDecimal("3"), expiredAt);
        int transaction = legacyTransaction(firstItem, seller, new BigDecimal("12.50"));
        assertFalse(columnsOf(Tables.ITEMS).contains("pending_publish"), "pre-condition : base ancienne");
        assertFalse(columnsOf(Tables.TRANSACTIONS).contains("claim_token"), "pre-condition : base ancienne");

        upgrade();

        // Colonnes : memes noms qu'une base neuve, les ajouts d'ALTER TABLE arrivent en fin.
        List<String> itemColumns = columnsOf(Tables.ITEMS);
        assertEquals(new HashSet<>(ITEM_COLUMNS), new HashSet<>(itemColumns));
        assertEquals("pending_publish", itemColumns.get(itemColumns.size() - 1));

        List<String> transactionColumns = columnsOf(Tables.TRANSACTIONS);
        assertEquals(new HashSet<>(TRANSACTION_COLUMNS), new HashSet<>(transactionColumns));
        assertEquals(List.of("claim_token", "claim_reserved_at"), transactionColumns.subList(transactionColumns.size() - 2, transactionColumns.size()),
                "les deux colonnes manquantes sont ajoutees dans l'ordre de la migration (un ALTER par colonne sous SQLite)");

        // Lignes conservees, colonnes ajoutees a NULL (jamais 0 ou 1 : NULL = ligne anterieure).
        assertEquals(2, count(Tables.ITEMS));
        assertNull(queryObject("SELECT pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", firstItem));
        assertNull(queryObject("SELECT pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", secondItem));
        assertEquals(seller.toString(), queryString("SELECT seller_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", firstItem));
        assertEquals("legacy-server", queryString("SELECT server_name FROM " + Tables.ITEMS + " WHERE id = ?", firstItem));
        assertEquals(1, count(Tables.TRANSACTIONS));
        assertNull(queryObject("SELECT claim_token FROM " + Tables.TRANSACTIONS + " WHERE id = ?", transaction));
        assertNull(queryObject("SELECT claim_reserved_at FROM " + Tables.TRANSACTIONS + " WHERE id = ?", transaction));

        // Les repositories lisent les lignes anciennes comme des lignes neuves.
        ItemDTO item = items().select(firstItem).orElseThrow();
        assertEquals(seller, item.seller_unique_id());
        assertNull(item.buyer_unique_id());
        assertEquals(0, new BigDecimal("12.50").compareTo(item.price()));
        assertEquals(StorageType.LISTED, item.storage_type());
        assertEquals("legacy-server", item.server_name());
        assertEquals(expiredAt, item.expired_at().getTime());

        List<TransactionDTO> transactions = transactions().selectByPlayer(seller);
        assertEquals(1, transactions.size());
        assertEquals(TransactionStatus.PENDING, transactions.get(0).status());
        assertEquals(0, new BigDecimal("12.50").compareTo(transactions.get(0).value()));

        // Le reste du schema est cree normalement, sans dupliquer les migrations deja enregistrees.
        for (String table : List.of(Tables.PLAYERS, Tables.AUCTION_ITEMS, Tables.LOGS, Tables.OPTIONS, Tables.MIGRATION_STATE)) {
            assertTrue(tableExists(table), "table manquante apres montee de version : " + table);
        }
        assertEquals(11, count(MIGRATION_TABLE));
        assertEquals(1, count(MIGRATION_TABLE, "migration = ?", "CreateItemMigration"));
        assertEquals(1, count(MIGRATION_TABLE, "migration = ?", "CreateTransactionsMigration"));
        assertEquals(Set.of("idx_zah_test_items_storage_type", "idx_zah_test_transactions_player_unique_id",
                "idx_zah_test_logs_created_at", "idx_zah_test_players_name"), indexNames());
    }

    @Test
    @DisplayName("transactions avec claim_token mais sans claim_reserved_at : seule la colonne absente est ajoutee, les jetons sont conserves")
    void upgrade_partiallyUpgradedTable_addsOnlyAbsentColumn() throws Exception {
        legacyTransactionsTable(true);
        migrationTableWith("CreateTransactionsMigration");
        UUID player = UUID.nameUUIDFromBytes("claimer".getBytes());
        int transaction = legacyTransaction(7, player, BigDecimal.TEN);
        execute("UPDATE " + Tables.TRANSACTIONS + " SET claim_token = ? WHERE id = ?", "token-123", transaction);

        upgrade();

        List<String> columns = columnsOf(Tables.TRANSACTIONS);
        assertEquals(new HashSet<>(TRANSACTION_COLUMNS), new HashSet<>(columns));
        assertEquals(1, columns.stream().filter("claim_token"::equals).count(), "claim_token ne doit pas etre ajoute une seconde fois");
        assertEquals("claim_reserved_at", columns.get(columns.size() - 1));
        assertEquals("token-123", queryString("SELECT claim_token FROM " + Tables.TRANSACTIONS + " WHERE id = ?", transaction));
        assertNull(queryObject("SELECT claim_reserved_at FROM " + Tables.TRANSACTIONS + " WHERE id = ?", transaction));
    }

    @Test
    @DisplayName("migration_state ancienne : les compteurs d'import manquants sont ajoutes et la ligne conservee")
    void upgrade_migrationState_addsCounterColumns() throws Exception {
        legacyMigrationStateTable();
        migrationTableWith("CreateMigrationStateMigration");
        execute("INSERT INTO " + Tables.MIGRATION_STATE + " (`provider_id`, `server_name`, `migrated_at`) VALUES (?, ?, ?)", "v3", "node-1", 1_700_000_000_000L);

        upgrade();

        List<String> columns = columnsOf(Tables.MIGRATION_STATE);
        assertEquals(List.of("provider_id", "server_name", "migrated_at", "players_imported", "items_imported", "transactions_imported"), columns);
        assertEquals(1, count(Tables.MIGRATION_STATE));
        assertEquals("node-1", queryString("SELECT server_name FROM " + Tables.MIGRATION_STATE + " WHERE provider_id = ?", "v3"));
        assertEquals(1_700_000_000_000L, queryLong("SELECT migrated_at FROM " + Tables.MIGRATION_STATE + " WHERE provider_id = ?", "v3"));
        assertNull(queryObject("SELECT players_imported FROM " + Tables.MIGRATION_STATE + " WHERE provider_id = ?", "v3"));
        assertEquals(1, count(MIGRATION_TABLE, "migration = ?", "CreateMigrationStateMigration"));
    }

    // ------------------------------------------------------------------------------------------
    // create() : une migration deja enregistree n'altere jamais la table
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("logs ancienne (create() deja enregistree) : aucune colonne ajoutee, lignes conservees, index cree")
    void upgrade_registeredCreateMigration_doesNotAlterTable() throws Exception {
        legacyLogsTable();
        migrationTableWith("CreateLogsMigration");
        UUID player = UUID.nameUUIDFromBytes("logger".getBytes());
        execute("INSERT INTO " + Tables.LOGS + " (`item_id`, `log_type`, `player_unique_id`, `price`) VALUES (?, ?, ?, ?)", 1, "SALE", player.toString(), BigDecimal.ONE);
        List<String> before = columnsOf(Tables.LOGS);
        assertFalse(before.contains("additional_data"), "pre-condition : base ancienne");

        upgrade();

        assertEquals(before, columnsOf(Tables.LOGS), "MigrationManager deduplique par nom : une migration create() enregistree ne rejoue rien");
        assertFalse(columnsOf(Tables.LOGS).contains("additional_data"));
        assertFalse(columnsOf(Tables.LOGS).contains("readed_at"));
        assertEquals(1, count(Tables.LOGS));
        assertEquals(player.toString(), queryString("SELECT player_unique_id FROM " + Tables.LOGS));
        assertEquals(1, count(MIGRATION_TABLE, "migration = ?", "CreateLogsMigration"));
        assertEquals(11, count(MIGRATION_TABLE));
        // La migration d'index, elle, n'etait pas enregistree : elle s'applique sur la table ancienne.
        assertTrue(indexNames().contains("idx_zah_test_logs_created_at"));
        // Les autres tables, absentes de la table de suivi, sont creees completes.
        assertEquals(ITEM_COLUMNS, columnsOf(Tables.ITEMS));
        assertEquals(TRANSACTION_COLUMNS, columnsOf(Tables.TRANSACTIONS));
    }

    // ------------------------------------------------------------------------------------------
    // Rejeu apres montee de version
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("rejouer les migrations apres la montee de version ne leve pas et ne change plus le schema")
    void upgrade_thenRerun_isStable() throws Exception {
        legacyItemsTable();
        legacyTransactionsTable(false);
        migrationTableWith("CreateItemMigration", "CreateTransactionsMigration");
        legacyItem(UUID.nameUUIDFromBytes("legacy-seller".getBytes()), BigDecimal.ONE, System.currentTimeMillis() + 60_000L);
        upgrade();
        List<String> itemColumns = columnsOf(Tables.ITEMS);
        List<String> transactionColumns = columnsOf(Tables.TRANSACTIONS);

        assertDoesNotThrow(this::registerAndRunMigrations);

        assertEquals(itemColumns, columnsOf(Tables.ITEMS));
        assertEquals(transactionColumns, columnsOf(Tables.TRANSACTIONS));
        assertEquals(11, count(MIGRATION_TABLE));
        assertEquals(1, count(Tables.ITEMS));
    }

    @Test
    @DisplayName("sans table ancienne, la montee de version equivaut a une installation neuve")
    void upgrade_freshDatabase_matchesCleanInstall() throws Exception {
        assertFalse(tableExists(Tables.ITEMS), "pre-condition : le socle n'a rien migre");

        upgrade();

        assertEquals(ITEM_COLUMNS, columnsOf(Tables.ITEMS));
        assertEquals(TRANSACTION_COLUMNS, columnsOf(Tables.TRANSACTIONS));
        assertEquals(11, count(MIGRATION_TABLE));
        assertEquals(4, indexNames().size());
    }
}
