package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.MigrationManager;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contrat du schema produit par les 11 migrations de production sur une base NEUVE : tables,
 * colonnes (liste exacte et ordonnee), index, cles, table de suivi des migrations, idempotence.
 * <p>
 * Les verifications qui lisent {@code sqlite_master} ou un {@code PRAGMA} sont reservees a SQLite
 * (le backend par defaut des tests) et sautees sur un serveur MySQL/MariaDB.
 */
class MigrationsTest extends DatabaseTestBase {

    /** Noms simples des classes de migration, dans l'ORDRE d'enregistrement de ZStorageManager. */
    private static final List<String> MIGRATION_NAMES = List.of(
            "CreatePlayerMigration",
            "CreateItemMigration",
            "CreateAuctionItemMigration",
            "CreateTransactionsMigration",
            "CreateLogsMigration",
            "CreateOptionsMigration",
            "CreateMigrationStateMigration",
            "CreateItemsStorageTypeIndexMigration",
            "CreateTransactionsPlayerIndexMigration",
            "CreateLogsCreatedAtIndexMigration",
            "CreatePlayersNameIndexMigration"
    );

    private static final List<String> MIGRATED_TABLES = List.of(
            Tables.PLAYERS, Tables.ITEMS, Tables.AUCTION_ITEMS, Tables.TRANSACTIONS, Tables.LOGS, Tables.OPTIONS, Tables.MIGRATION_STATE
    );

    static Stream<Arguments> expectedColumns() {
        return Stream.of(
                Arguments.of(Tables.PLAYERS, List.of("unique_id", "name", "created_at", "updated_at")),
                Arguments.of(Tables.ITEMS, List.of("id", "item_type", "seller_unique_id", "buyer_unique_id", "price", "economy_name",
                        "storage_type", "server_name", "expired_at", "pending_publish", "created_at", "updated_at")),
                Arguments.of(Tables.AUCTION_ITEMS, List.of("id", "item_id", "itemstack", "created_at", "updated_at")),
                Arguments.of(Tables.TRANSACTIONS, List.of("id", "item_id", "player_unique_id", "economy_name", "before", "after", "value",
                        "status", "claim_token", "claim_reserved_at", "created_at", "updated_at")),
                Arguments.of(Tables.LOGS, List.of("id", "item_id", "log_type", "player_unique_id", "target_unique_id", "itemstack", "price",
                        "economy_name", "additional_data", "readed_at", "created_at", "updated_at")),
                Arguments.of(Tables.OPTIONS, List.of("player_unique_id", "option_name", "option_value", "created_at", "updated_at")),
                Arguments.of(Tables.MIGRATION_STATE, List.of("provider_id", "server_name", "migrated_at", "players_imported", "items_imported", "transactions_imported"))
        );
    }

    // ------------------------------------------------------------------------------------------
    // Helpers PRAGMA (SQLite)
    // ------------------------------------------------------------------------------------------

    /** PRAGMA table_info : nom de colonne -> ligne (cid, name, type, notnull, dflt_value, pk). */
    private Map<String, Object[]> tableInfo(String tableName) throws SQLException {
        Map<String, Object[]> columns = new LinkedHashMap<>();
        for (Object[] row : queryRows("PRAGMA table_info(" + tableName + ")")) {
            columns.put(((String) row[1]).toLowerCase(), row);
        }
        return columns;
    }

    private static String typeOf(Map<String, Object[]> info, String column) {
        return ((String) info.get(column)[2]).toUpperCase();
    }

    private static int notNullOf(Map<String, Object[]> info, String column) {
        return ((Number) info.get(column)[3]).intValue();
    }

    private static Object defaultOf(Map<String, Object[]> info, String column) {
        return info.get(column)[4];
    }

    private static int primaryKeyOf(Map<String, Object[]> info, String column) {
        return ((Number) info.get(column)[5]).intValue();
    }

    /** PRAGMA foreign_key_list : "from -> table(to) ON DELETE action". */
    private Set<String> foreignKeysOf(String tableName) throws SQLException {
        Set<String> keys = new HashSet<>();
        for (Object[] row : queryRows("PRAGMA foreign_key_list(" + tableName + ")")) {
            // colonnes : id, seq, table, from, to, on_update, on_delete, match
            keys.add(row[3] + " -> " + row[2] + "(" + row[4] + ") ON DELETE " + row[6]);
        }
        return keys;
    }

    private List<String> migrationNamesInOrder() throws SQLException {
        return queryRows("SELECT migration FROM " + MIGRATION_TABLE + " ORDER BY rowid").stream()
                .map(row -> (String) row[0])
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------------------------------
    // Tables et colonnes
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("les migrations creent les sept tables du plugin")
    void migrations_createEveryTable() throws Exception {
        for (String table : MIGRATED_TABLES) {
            assertTrue(tableExists(table), "table manquante : " + table);
        }
    }

    @Test
    @DisplayName("aucune migration ne cree les tables bid_items et rend_items declarees dans Tables")
    void migrations_doNotCreateUnusedTables() throws Exception {
        // Constantes historiques sans migration : si l'une d'elles recoit une migration un jour,
        // ce test doit etre mis a jour en meme temps que la liste MIGRATION_NAMES.
        assertFalse(tableExists(Tables.BID_ITEMS));
        assertFalse(tableExists(Tables.RENT_ITEMS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedColumns")
    @DisplayName("chaque table porte exactement les colonnes attendues, dans l'ordre de declaration")
    void migrations_createExactColumns(String table, List<String> expected) throws Exception {
        assertEquals(expected, columnsOf(table));
    }

    @Test
    @DisplayName("Tables.MIGRATION_STATE et MigrationStateRepository.TABLE_NAME designent la meme table")
    void migrationStateTableName_isConsistent() {
        assertEquals(Tables.MIGRATION_STATE, MigrationStateRepository.TABLE_NAME);
    }

    // ------------------------------------------------------------------------------------------
    // Contraintes de colonnes (SQLite)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("logs : nullabilite et valeurs par defaut conformes a CreateLogsMigration")
    void logsTable_columnConstraints() throws Exception {
        assumeTrue(isSqlite(), "PRAGMA table_info est propre a SQLite");
        Map<String, Object[]> info = tableInfo(Tables.LOGS);

        assertEquals(1, primaryKeyOf(info, "id"));
        assertEquals(1, notNullOf(info, "item_id"));
        assertEquals(1, notNullOf(info, "log_type"));
        assertEquals(1, notNullOf(info, "player_unique_id"));
        assertEquals(0, notNullOf(info, "target_unique_id"));
        assertEquals(0, notNullOf(info, "itemstack"));
        assertEquals(0, notNullOf(info, "economy_name"));
        assertEquals(0, notNullOf(info, "additional_data"));
        assertEquals(0, notNullOf(info, "readed_at"));
        assertEquals("0", String.valueOf(defaultOf(info, "price")));
        assertEquals("CURRENT_TIMESTAMP", defaultOf(info, "created_at"));
        assertEquals("CURRENT_TIMESTAMP", defaultOf(info, "updated_at"));
        assertEquals("VARCHAR(36)", typeOf(info, "player_unique_id"));
        assertEquals("DECIMAL(65,2)", typeOf(info, "price"));
    }

    @Test
    @DisplayName("items : pending_publish nullable sans defaut, storage_type en TEXT, buyer nullable")
    void itemsTable_columnConstraints() throws Exception {
        assumeTrue(isSqlite(), "PRAGMA table_info est propre a SQLite");
        Map<String, Object[]> info = tableInfo(Tables.ITEMS);

        assertEquals(1, primaryKeyOf(info, "id"));
        assertEquals(0, notNullOf(info, "pending_publish"), "NULL = ligne anterieure, jamais une reservation");
        assertNull(defaultOf(info, "pending_publish"), "aucune valeur par defaut : l'import V3 ne doit pas ecrire 0 ou 1");
        assertEquals("INT", typeOf(info, "pending_publish"));
        assertEquals(0, notNullOf(info, "buyer_unique_id"));
        assertEquals(1, notNullOf(info, "seller_unique_id"));
        assertEquals(1, notNullOf(info, "expired_at"));
        assertEquals("TEXT", typeOf(info, "storage_type"), "ENUM n'existe pas sous SQLite : Sarah retombe sur TEXT");
    }

    @Test
    @DisplayName("transactions : claim_token et claim_reserved_at sont nullables (ligne libre = NULL)")
    void transactionsTable_columnConstraints() throws Exception {
        assumeTrue(isSqlite(), "PRAGMA table_info est propre a SQLite");
        Map<String, Object[]> info = tableInfo(Tables.TRANSACTIONS);

        assertEquals(1, primaryKeyOf(info, "id"));
        assertEquals(0, notNullOf(info, "claim_token"));
        assertEquals("VARCHAR(36)", typeOf(info, "claim_token"));
        assertEquals(0, notNullOf(info, "claim_reserved_at"));
        assertEquals("TIMESTAMP", typeOf(info, "claim_reserved_at"));
        assertEquals(1, notNullOf(info, "status"));
        assertEquals("VARCHAR(32)", typeOf(info, "status"));
    }

    @Test
    @DisplayName("cles primaires : players.unique_id, options (player_unique_id, option_name), migration_state.provider_id")
    void primaryKeys_matchMigrations() throws Exception {
        assumeTrue(isSqlite(), "PRAGMA table_info est propre a SQLite");

        assertEquals(1, primaryKeyOf(tableInfo(Tables.PLAYERS), "unique_id"));
        assertEquals(0, primaryKeyOf(tableInfo(Tables.PLAYERS), "name"));

        Map<String, Object[]> options = tableInfo(Tables.OPTIONS);
        assertEquals(1, primaryKeyOf(options, "player_unique_id"));
        assertEquals(2, primaryKeyOf(options, "option_name"), "cle composite : option_name est la 2e colonne de la cle");
        assertEquals(0, primaryKeyOf(options, "option_value"));

        assertEquals(1, primaryKeyOf(tableInfo(Tables.MIGRATION_STATE), "provider_id"));
    }

    @Test
    @DisplayName("cles etrangeres : toutes vers players.unique_id ou items.id, en ON DELETE CASCADE")
    void foreignKeys_matchMigrations() throws Exception {
        assumeTrue(isSqlite(), "PRAGMA foreign_key_list est propre a SQLite");
        String players = table(Tables.PLAYERS);
        String items = table(Tables.ITEMS);

        assertEquals(Set.of(
                "seller_unique_id -> " + players + "(unique_id) ON DELETE CASCADE",
                "buyer_unique_id -> " + players + "(unique_id) ON DELETE CASCADE"
        ), foreignKeysOf(Tables.ITEMS));
        assertEquals(Set.of("item_id -> " + items + "(id) ON DELETE CASCADE"), foreignKeysOf(Tables.AUCTION_ITEMS));
        assertEquals(Set.of(
                "item_id -> " + items + "(id) ON DELETE CASCADE",
                "player_unique_id -> " + players + "(unique_id) ON DELETE CASCADE"
        ), foreignKeysOf(Tables.TRANSACTIONS));
        assertEquals(Set.of(
                "item_id -> " + items + "(id) ON DELETE CASCADE",
                "player_unique_id -> " + players + "(unique_id) ON DELETE CASCADE",
                "target_unique_id -> " + players + "(unique_id) ON DELETE CASCADE"
        ), foreignKeysOf(Tables.LOGS));
        assertEquals(Set.of("player_unique_id -> " + players + "(unique_id) ON DELETE CASCADE"), foreignKeysOf(Tables.OPTIONS));
        assertTrue(foreignKeysOf(Tables.PLAYERS).isEmpty());
        assertTrue(foreignKeysOf(Tables.MIGRATION_STATE).isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // Index
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("les quatre index de production existent, sur la bonne table et la bonne colonne")
    void migrations_createFourIndexes() throws Exception {
        assumeTrue(isSqlite(), "sqlite_master est propre a SQLite");

        Map<String, String> indexes = new LinkedHashMap<>();
        for (Object[] row : queryRows("SELECT name, tbl_name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%' ORDER BY name")) {
            indexes.put((String) row[0], (String) row[1]);
        }

        assertEquals(Map.of(
                "idx_zah_test_items_storage_type", "zah_test_items",
                "idx_zah_test_transactions_player_unique_id", "zah_test_transactions",
                "idx_zah_test_logs_created_at", "zah_test_logs",
                "idx_zah_test_players_name", "zah_test_players"
        ), indexes);

        assertEquals("storage_type", indexedColumn("idx_zah_test_items_storage_type"));
        assertEquals("player_unique_id", indexedColumn("idx_zah_test_transactions_player_unique_id"));
        assertEquals("created_at", indexedColumn("idx_zah_test_logs_created_at"));
        assertEquals("name", indexedColumn("idx_zah_test_players_name"));
    }

    private String indexedColumn(String indexName) throws SQLException {
        // PRAGMA index_info : seqno, cid, name
        List<Object[]> rows = queryRows("PRAGMA index_info(" + indexName + ")");
        assertEquals(1, rows.size(), "un index de production porte sur une seule colonne");
        return (String) rows.get(0)[2];
    }

    // ------------------------------------------------------------------------------------------
    // Table de suivi des migrations
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("la table de suivi contient exactement les 11 noms simples, une fois chacun")
    void migrationTable_containsEachMigrationOnce() throws Exception {
        assertEquals(11, MIGRATION_NAMES.size(), "liste de reference incomplete");
        assertEquals(11, count(MIGRATION_TABLE));
        for (String name : MIGRATION_NAMES) {
            assertEquals(1, count(MIGRATION_TABLE, "migration = ?", name), "migration absente ou dupliquee : " + name);
        }
        assertEquals(List.of("migration", "created_at"), columnsOf(MIGRATION_TABLE));
    }

    @Test
    @DisplayName("la table de suivi enregistre les migrations dans l'ordre d'enregistrement de ZStorageManager")
    void migrationTable_preservesRegistrationOrder() throws Exception {
        assumeTrue(isSqlite(), "ORDER BY rowid est propre a SQLite");

        assertEquals(MIGRATION_NAMES, migrationNamesInOrder());
    }

    @Test
    @DisplayName("la liste de reference du socle de test est bien celle des 11 migrations de production")
    void productionMigrations_matchReferenceNames() {
        List<String> registered = productionMigrations().stream().map(migration -> migration.getClass().getSimpleName()).toList();

        assertEquals(MIGRATION_NAMES, registered);
        assertEquals(11, MigrationManager.getMigrations().size(), "MigrationManager doit encore porter les 11 migrations du dernier execute()");
    }

    @Test
    @DisplayName("seules les migrations items, transactions et migration_state sont en createOrAlter (isAlter)")
    void onlyUpgradableMigrations_areAlter() {
        // isAlter() n'est positionne qu'apres up() : il faut invoquer up() sur des instances neuves,
        // ce qui enregistre des schemas statiques... remis a zero par le tearDown du socle.
        Map<String, Boolean> alterByName = new LinkedHashMap<>();
        for (var migration : productionMigrations()) {
            migration.up();
            alterByName.put(migration.getClass().getSimpleName(), migration.isAlter());
        }

        assertEquals(Set.of("CreateItemMigration", "CreateTransactionsMigration", "CreateMigrationStateMigration"),
                alterByName.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet()));
    }

    // ------------------------------------------------------------------------------------------
    // Idempotence
    // ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} passage(s) supplementaire(s)")
    @ValueSource(ints = {1, 2})
    @DisplayName("rejouer les migrations sur une base deja migree ne leve pas, ne duplique rien et conserve les donnees")
    void migrations_rerun_isIdempotent(int extraRuns) throws Exception {
        UUID alice = newPlayer("Alice");
        int itemId = items().create(alice, ItemType.AUCTION, BigDecimal.TEN, System.currentTimeMillis() + 60_000L, this.economy);
        logs().createLog(LogType.SALE, itemId, alice, null, null, BigDecimal.TEN, "vault", null, null);
        List<String> itemColumnsBefore = columnsOf(Tables.ITEMS);

        for (int run = 0; run < extraRuns; run++) {
            assertDoesNotThrow(this::registerAndRunMigrations);
        }

        assertEquals(11, count(MIGRATION_TABLE));
        for (String name : MIGRATION_NAMES) {
            assertEquals(1, count(MIGRATION_TABLE, "migration = ?", name), "migration dupliquee au rejeu : " + name);
        }
        assertEquals(1, count(Tables.PLAYERS));
        assertEquals(1, count(Tables.ITEMS));
        assertEquals(1, count(Tables.LOGS));
        assertEquals("Alice", players().select(alice));
        assertNotNull(items().select(itemId).orElse(null));
        assertEquals(itemColumnsBefore, columnsOf(Tables.ITEMS), "un rejeu ne doit ajouter aucune colonne");
        for (String table : MIGRATED_TABLES) {
            assertTrue(tableExists(table));
        }
    }
}
