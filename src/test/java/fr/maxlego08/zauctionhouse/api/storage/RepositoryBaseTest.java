package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.SchemaBuilder;
import fr.maxlego08.sarah.database.Schema;
import fr.maxlego08.sarah.exceptions.DatabaseException;
import fr.maxlego08.sarah.logger.Logger;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de la classe de base {@link Repository}, verifie sur une vraie base SQLite a travers
 * une sous-classe sonde qui expose ses methodes protegees.
 * <p>
 * Les tables utilisees sont celles des migrations de production : {@code players} (cle
 * primaire non auto-incrementee, pour l'upsert) et {@code logs} (auto-increment, pour les
 * identifiants generes).
 */
class RepositoryBaseTest extends DatabaseTestBase {

    private static final String MISSING_TABLE = "%prefix%does_not_exist";

    private ProbeRepository players;
    private ProbeRepository logsProbe;
    private ProbeRepository missing;

    @BeforeEach
    void setUpProbes() {
        this.players = new ProbeRepository(this.plugin, this.connection, Tables.PLAYERS);
        this.logsProbe = new ProbeRepository(this.plugin, this.connection, Tables.LOGS);
        this.missing = new ProbeRepository(this.plugin, this.connection, MISSING_TABLE);
    }

    // ------------------------------------------------------------------------------------------
    // Sonde : rend publiques les methodes protegees de Repository, sans rien y ajouter
    // ------------------------------------------------------------------------------------------

    static final class ProbeRepository extends Repository {

        ProbeRepository(AuctionPlugin plugin, DatabaseConnection connection, String tableName) {
            super(plugin, connection, tableName);
        }

        static int chunkSize() {
            return IN_CLAUSE_CHUNK_SIZE;
        }

        void doUpsert(Consumer<Schema> consumer) {
            upsert(consumer);
        }

        void doUpsertBatch(List<Schema> schemas) {
            upsert(schemas);
        }

        void doUpdate(Consumer<Schema> consumer) {
            update(consumer);
        }

        void doUpdateBatch(List<Schema> schemas) {
            update(schemas);
        }

        int doUpdateReturning(Consumer<Schema> consumer) {
            return updateReturning(consumer);
        }

        int doUpdateReturning(List<Schema> schemas) {
            return updateReturning(schemas);
        }

        void doInsert(Consumer<Schema> consumer) {
            insert(consumer);
        }

        void doInsert(Consumer<Schema> consumer, Consumer<Integer> callback) {
            insert(consumer, callback);
        }

        void doInsertBatch(List<Schema> schemas) {
            insert(schemas);
        }

        int doInsertSync(Consumer<Schema> consumer) {
            return insertSync(consumer);
        }

        long doCount(Consumer<Schema> consumer) {
            return select(consumer);
        }

        <T> List<T> doSelect(Class<T> clazz, Consumer<Schema> consumer) {
            return select(clazz, consumer);
        }

        <T> List<T> doSelectAll(Class<T> clazz) {
            return selectAll(clazz);
        }

        <T> List<T> doSelectOrFail(Class<T> clazz, Consumer<Schema> consumer) {
            return selectOrFail(clazz, consumer);
        }

        <T> List<T> doSelectAllOrFail(Class<T> clazz) {
            return selectAllOrFail(clazz);
        }

        <T> List<T> doSelectInOrFail(Class<T> clazz, String columnName, List<String> values) {
            return selectInOrFail(clazz, columnName, values);
        }

        int doDelete(Consumer<Schema> consumer) {
            return delete(consumer);
        }

        Schema doCreateInsertSchema(Consumer<Schema> consumer) {
            return createInsertSchema(consumer);
        }

        Schema doCreateUpdateSchema(Consumer<Schema> consumer) {
            return createUpdateSchema(consumer);
        }
    }

    /**
     * Connexion qui compte les ouvertures JDBC : Sarah ouvre UNE connexion par requete, le
     * compteur donne donc le nombre de requetes reellement emises (utile pour la pagination).
     */
    static final class CountingConnection extends DatabaseConnection {

        private final DatabaseConnection delegate;
        private final AtomicInteger opened = new AtomicInteger();

        CountingConnection(DatabaseConnection delegate, Logger logger) {
            super(delegate.getDatabaseConfiguration(), logger);
            this.delegate = delegate;
        }

        @Override
        public Connection connectToDatabase() throws Exception {
            this.opened.incrementAndGet();
            return this.delegate.connectToDatabase();
        }

        int opened() {
            return this.opened.get();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------

    private static Consumer<Schema> playerInsert(UUID uniqueId, String name) {
        return schema -> {
            schema.uuid("unique_id", uniqueId);
            schema.string("name", name);
        };
    }

    private static Consumer<Schema> playerUpsert(UUID uniqueId, String name) {
        return schema -> {
            schema.uuid("unique_id", uniqueId).primary();
            schema.string("name", name);
        };
    }

    private static Consumer<Schema> logInsert(UUID playerUniqueId) {
        return schema -> {
            schema.object("item_id", 1);
            schema.string("log_type", LogType.PURCHASE.name());
            schema.uuid("player_unique_id", playerUniqueId);
            schema.decimal("price", BigDecimal.ONE);
        };
    }

    private String nameOf(UUID uniqueId) throws SQLException {
        return queryString("SELECT name FROM " + Tables.PLAYERS + " WHERE unique_id = ?", uniqueId.toString());
    }

    /** Insere des joueurs en une seule transaction JDBC et rend leurs UUID (fixture rapide). */
    private List<String> bulkPlayers(int count) throws SQLException {
        List<String> uuids = new ArrayList<>(count);
        try (Connection jdbc = jdbc()) {
            jdbc.setAutoCommit(false);
            try (PreparedStatement statement = jdbc.prepareStatement("INSERT INTO " + table(Tables.PLAYERS) + " (unique_id, name) VALUES (?, ?)")) {
                for (int index = 0; index < count; index++) {
                    String uuid = UUID.nameUUIDFromBytes(("bulk-" + index).getBytes()).toString();
                    uuids.add(uuid);
                    statement.setString(1, uuid);
                    statement.setString(2, "b" + index);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            jdbc.commit();
            jdbc.setAutoCommit(true);
        }
        return uuids;
    }

    // ==========================================================================================

    @Nested
    @DisplayName("accesseurs")
    class Accessors {

        @Test
        @DisplayName("expose le plugin, la connexion, la table et un logger")
        void getters_exposeConstructorArguments() {
            assertSame(RepositoryBaseTest.this.plugin, players.getPlugin());
            assertSame(RepositoryBaseTest.this.connection, players.getConnection());
            assertEquals(Tables.PLAYERS, players.getTableName());
            assertNotNull(players.getLogger());
        }

        @Test
        @DisplayName("la taille de tranche IN est 500")
        void chunkSize_is500() {
            assertEquals(500, ProbeRepository.chunkSize());
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("insertions")
    class Inserts {

        @Test
        @DisplayName("insertSync rend l'identifiant genere, strictement positif et croissant")
        void insertSync_returnsGeneratedIncreasingIds() throws Exception {
            UUID player = newPlayer("Alice");

            int first = logsProbe.doInsertSync(logInsert(player));
            int second = logsProbe.doInsertSync(logInsert(player));
            int third = logsProbe.doInsertSync(logInsert(player));

            assertTrue(first > 0);
            assertTrue(second > first);
            assertTrue(third > second);
            assertEquals(3, count(Tables.LOGS));
            assertEquals(third, queryLong("SELECT MAX(id) FROM " + Tables.LOGS));
        }

        @Test
        @DisplayName("insert(consumer, callback) transmet l'identifiant genere au callback")
        void insert_withCallback_receivesGeneratedId() throws Exception {
            UUID player = newPlayer("Alice");
            List<Integer> received = new ArrayList<>();

            logsProbe.doInsert(logInsert(player), received::add);

            assertEquals(1, received.size());
            assertEquals(queryLong("SELECT MAX(id) FROM " + Tables.LOGS), received.getFirst().longValue());
            assertEquals(1, count(Tables.LOGS));
        }

        @Test
        @DisplayName("insert(consumer) ecrit la ligne avec les valeurs fournies")
        void insert_writesRow() throws Exception {
            UUID uniqueId = UUID.randomUUID();

            players.doInsert(playerInsert(uniqueId, "Alice"));

            assertEquals(1, count(Tables.PLAYERS));
            assertEquals("Alice", nameOf(uniqueId));
        }

        @Test
        @DisplayName("createInsertSchema ne touche pas la base ; insert(List) execute le lot")
        void createInsertSchema_notExecuted_untilBatchInsert() throws Exception {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();

            Schema schemaA = players.doCreateInsertSchema(playerInsert(a, "A"));
            Schema schemaB = players.doCreateInsertSchema(playerInsert(b, "B"));
            assertNotNull(schemaA);
            assertEquals(0, count(Tables.PLAYERS), "la creation d'un schema ne doit rien inserer");

            players.doInsertBatch(List.of(schemaA, schemaB));

            assertEquals(2, count(Tables.PLAYERS));
            assertEquals("A", nameOf(a));
            assertEquals("B", nameOf(b));
        }

        @Test
        @Disabled("BUG: Repository.insertSync documente '-1 if the operation failed' (Repository.java:187-194) mais Sarah leve une DatabaseException (RuntimeException), jamais une SQLException : le -1 est inatteignable et l'echec se propage")
        @DisplayName("insertSync rend -1 quand l'ecriture echoue")
        void insertSync_onFailure_returnsMinusOne() {
            assertEquals(-1, missing.doInsertSync(playerInsert(UUID.randomUUID(), "x")));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("upsert")
    class Upserts {

        @Test
        @DisplayName("insere puis met a jour sur la cle primaire, sans doublon")
        void upsert_insertsThenUpdates() throws Exception {
            UUID uniqueId = UUID.randomUUID();

            players.doUpsert(playerUpsert(uniqueId, "Alice"));
            assertEquals(1, count(Tables.PLAYERS));
            assertEquals("Alice", nameOf(uniqueId));

            players.doUpsert(playerUpsert(uniqueId, "Alice_renamed"));
            assertEquals(1, count(Tables.PLAYERS));
            assertEquals("Alice_renamed", nameOf(uniqueId));
        }

        @Test
        @DisplayName("deux cles distinctes donnent deux lignes")
        void upsert_distinctKeys_twoRows() throws Exception {
            players.doUpsert(playerUpsert(UUID.randomUUID(), "A"));
            players.doUpsert(playerUpsert(UUID.randomUUID(), "B"));

            assertEquals(2, count(Tables.PLAYERS));
        }

        @Test
        @DisplayName("upsert(List) : melange de nouvelles lignes et d'une ligne existante")
        void upsertBatch_insertsNewAndUpdatesExisting() throws Exception {
            UUID existing = UUID.randomUUID();
            UUID newA = UUID.randomUUID();
            UUID newB = UUID.randomUUID();
            players.doUpsert(playerUpsert(existing, "old"));

            players.doUpsertBatch(List.of(
                    SchemaBuilder.upsert(players.getTableName(), playerUpsert(existing, "new")),
                    SchemaBuilder.upsert(players.getTableName(), playerUpsert(newA, "A")),
                    SchemaBuilder.upsert(players.getTableName(), playerUpsert(newB, "B"))));

            assertEquals(3, count(Tables.PLAYERS));
            assertEquals("new", nameOf(existing));
            assertEquals("A", nameOf(newA));
            assertEquals("B", nameOf(newB));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("mises a jour")
    class Updates {

        private UUID a;
        private UUID b;
        private UUID c;

        @BeforeEach
        void seed() {
            this.a = newPlayer("dup-a");
            this.b = newPlayer("dup-b");
            this.c = newPlayer("solo");
            players.doUpdate(schema -> {
                schema.where("unique_id", this.a.toString());
                schema.string("name", "dup");
            });
            players.doUpdate(schema -> {
                schema.where("unique_id", this.b.toString());
                schema.string("name", "dup");
            });
        }

        @Test
        @DisplayName("update(consumer) applique la modification aux lignes filtrees")
        void update_appliesToMatchingRows() throws Exception {
            players.doUpdate(schema -> {
                schema.where("name", "dup");
                schema.string("name", "renamed");
            });

            assertEquals("renamed", nameOf(this.a));
            assertEquals("renamed", nameOf(this.b));
            assertEquals("solo", nameOf(this.c));
        }

        @Test
        @DisplayName("updateReturning(consumer) rend le nombre de lignes modifiees")
        void updateReturning_returnsAffectedRows() throws Exception {
            int affected = players.doUpdateReturning(schema -> {
                schema.where("name", "dup");
                schema.string("name", "renamed");
            });

            assertEquals(2, affected);
            assertEquals(2, count(Tables.PLAYERS, "name = ?", "renamed"));
        }

        @Test
        @DisplayName("updateReturning(consumer) rend 0 quand aucune ligne ne correspond")
        void updateReturning_noMatch_returnsZero() throws Exception {
            int affected = players.doUpdateReturning(schema -> {
                schema.where("name", "nobody");
                schema.string("name", "renamed");
            });

            assertEquals(0, affected);
            assertEquals(0, count(Tables.PLAYERS, "name = ?", "renamed"));
        }

        @Test
        @DisplayName("updateReturning(consumer) propage l'echec SQL au lieu de le confondre avec une course perdue")
        void updateReturning_onFailure_throws() {
            assertThrows(DatabaseException.class, () -> missing.doUpdateReturning(schema -> {
                schema.where("name", "x");
                schema.string("name", "y");
            }));
        }

        @Test
        @DisplayName("updateReturning(List) rend la somme des lignes modifiees par chaque schema")
        void updateReturningBatch_returnsSum() throws Exception {
            List<Schema> schemas = List.of(
                    renameSchema(this.a, "batch"),
                    renameSchema(this.b, "batch"),
                    renameSchema(UUID.randomUUID(), "batch"));

            int affected = players.doUpdateReturning(schemas);

            assertEquals(2, affected);
            assertEquals("batch", nameOf(this.a));
            assertEquals("batch", nameOf(this.b));
            assertEquals("solo", nameOf(this.c));
        }

        @Test
        @DisplayName("updateReturning(List) rend 0 et ne touche pas la base pour une liste vide")
        void updateReturningBatch_emptyList_returnsZero() throws Exception {
            CountingConnection counting = new CountingConnection(RepositoryBaseTest.this.connection, RepositoryBaseTest.this.sarahLogger);
            ProbeRepository counted = new ProbeRepository(RepositoryBaseTest.this.plugin, counting, Tables.PLAYERS);

            assertEquals(0, counted.doUpdateReturning(List.of()));
            assertEquals(0, counting.opened());
        }

        @Test
        @DisplayName("createUpdateSchema ne touche pas la base ; update(List) execute le lot")
        void createUpdateSchema_notExecuted_untilBatchUpdate() throws Exception {
            Schema schemaA = renameSchema(this.a, "later");
            Schema schemaB = renameSchema(this.b, "later");
            assertNotNull(schemaA);
            assertEquals("dup", nameOf(this.a), "la creation d'un schema ne doit rien modifier");

            players.doUpdateBatch(List.of(schemaA, schemaB));

            assertEquals("later", nameOf(this.a));
            assertEquals("later", nameOf(this.b));
            assertEquals("solo", nameOf(this.c));
        }

        private Schema renameSchema(UUID uniqueId, String name) {
            return players.doCreateUpdateSchema(schema -> {
                schema.where("unique_id", uniqueId.toString());
                schema.string("name", name);
            });
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("lectures")
    class Selects {

        private UUID a;
        private UUID b;
        private UUID c;

        @BeforeEach
        void seed() {
            this.a = newPlayer("alpha");
            this.b = newPlayer("beta");
            this.c = newPlayer("gamma");
        }

        @Test
        @DisplayName("select(consumer) compte les lignes filtrees")
        void selectCount_filtered() {
            assertEquals(1, players.doCount(schema -> schema.where("name", "alpha")));
            assertEquals(0, players.doCount(schema -> schema.where("name", "nobody")));
        }

        @Test
        @DisplayName("select(consumer) sans filtre compte toute la table")
        void selectCount_unfiltered() {
            assertEquals(3, players.doCount(schema -> {
            }));
        }

        @Test
        @DisplayName("select(consumer) rend 0 quand la requete echoue")
        void selectCount_onFailure_returnsZero() {
            assertEquals(0, missing.doCount(schema -> {
            }));
        }

        @Test
        @DisplayName("select(Class, consumer) mappe les lignes sur le DTO")
        void select_mapsRows() {
            List<PlayerDTO> rows = players.doSelect(PlayerDTO.class, schema -> schema.where("name", "beta"));

            assertEquals(1, rows.size());
            assertEquals(this.b, rows.getFirst().unique_id());
            assertEquals("beta", rows.getFirst().name());
        }

        @Test
        @DisplayName("select(Class, consumer) AVALE une erreur SQL et rend une liste vide")
        void select_unknownColumn_returnsEmptyList() {
            List<PlayerDTO> rows = players.doSelect(PlayerDTO.class, schema -> schema.where("no_such_column", "x"));

            assertTrue(rows.isEmpty());
        }

        @Test
        @DisplayName("selectAll(Class) rend toutes les lignes")
        void selectAll_returnsEveryRow() {
            List<PlayerDTO> rows = players.doSelectAll(PlayerDTO.class);

            assertEquals(3, rows.size());
            assertEquals(Set.of(this.a, this.b, this.c), rows.stream().map(PlayerDTO::unique_id).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("selectAll(Class) AVALE une erreur SQL et rend une liste vide")
        void selectAll_missingTable_returnsEmptyList() {
            assertTrue(missing.doSelectAll(PlayerDTO.class).isEmpty());
        }

        @Test
        @DisplayName("selectOrFail rend les lignes en nominal")
        void selectOrFail_nominal() {
            List<PlayerDTO> rows = players.doSelectOrFail(PlayerDTO.class, schema -> schema.where("name", "gamma"));

            assertEquals(1, rows.size());
            assertEquals(this.c, rows.getFirst().unique_id());
        }

        @Test
        @DisplayName("selectOrFail PROPAGE une erreur SQL en IllegalStateException")
        void selectOrFail_unknownColumn_throws() {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> players.doSelectOrFail(PlayerDTO.class, schema -> schema.where("no_such_column", "x")));

            assertNotNull(exception.getCause(), "la cause SQL doit etre conservee");
            assertTrue(exception.getMessage().contains(Tables.PLAYERS));
        }

        @Test
        @DisplayName("selectAllOrFail rend toutes les lignes en nominal")
        void selectAllOrFail_nominal() {
            assertEquals(3, players.doSelectAllOrFail(PlayerDTO.class).size());
        }

        @Test
        @DisplayName("selectAllOrFail PROPAGE une erreur SQL en IllegalStateException")
        void selectAllOrFail_missingTable_throws() {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> missing.doSelectAllOrFail(LogDTO.class));

            assertNotNull(exception.getCause());
            assertTrue(exception.getMessage().contains(MISSING_TABLE));
        }

        @Test
        @DisplayName("un DTO avec colonnes nullables se mappe (target_unique_id NULL)")
        void select_nullableColumns_mapToNull() {
            logsProbe.doInsert(logInsert(this.a));

            List<LogDTO> rows = logsProbe.doSelectAll(LogDTO.class);

            assertEquals(1, rows.size());
            LogDTO log = rows.getFirst();
            assertEquals(LogType.PURCHASE, log.log_type());
            assertEquals(this.a, log.player_unique_id());
            assertNull(log.target_unique_id());
            assertNull(log.readed_at());
            assertEquals(0, BigDecimal.ONE.compareTo(log.price()));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("selectInOrFail : clause IN paginee")
    class SelectIn {

        private CountingConnection counting;
        private ProbeRepository counted;

        @BeforeEach
        void setUpCounting() {
            this.counting = new CountingConnection(RepositoryBaseTest.this.connection, RepositoryBaseTest.this.sarahLogger);
            this.counted = new ProbeRepository(RepositoryBaseTest.this.plugin, this.counting, Tables.PLAYERS);
        }

        @Test
        @DisplayName("liste vide : resultat vide et AUCUNE requete emise")
        void selectIn_empty_noQuery() {
            newPlayer("x");

            assertTrue(counted.doSelectInOrFail(PlayerDTO.class, "unique_id", List.of()).isEmpty());
            assertEquals(0, counting.opened());
        }

        @ParameterizedTest(name = "{0} valeurs -> {1} requete(s)")
        @CsvSource({"1, 1", "499, 1", "500, 1", "501, 2", "1000, 2", "1001, 3"})
        @DisplayName("une requete par tranche de 500, sans perdre aucune ligne")
        void selectIn_oneQueryPerChunk(int size, int expectedQueries) throws Exception {
            List<String> uuids = bulkPlayers(size);

            List<PlayerDTO> rows = counted.doSelectInOrFail(PlayerDTO.class, "unique_id", uuids);

            assertEquals(expectedQueries, counting.opened());
            assertEquals(size, rows.size());
            assertEquals(new HashSet<>(uuids), rows.stream().map(dto -> dto.unique_id().toString()).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("les valeurs inconnues sont ignorees et ne comptent que dans la pagination")
        void selectIn_unknownValues_ignored() throws Exception {
            List<String> known = bulkPlayers(3);
            List<String> values = new ArrayList<>(known);
            for (int index = 0; index < 498; index++) values.add(UUID.randomUUID().toString());

            List<PlayerDTO> rows = counted.doSelectInOrFail(PlayerDTO.class, "unique_id", values);

            assertEquals(3, rows.size());
            assertEquals(2, counting.opened(), "501 valeurs = 2 tranches, meme si 498 sont inconnues");
        }

        @Test
        @DisplayName("ne rend pas les lignes absentes de la liste")
        void selectIn_returnsOnlyRequested() throws Exception {
            List<String> all = bulkPlayers(10);
            List<String> wanted = all.subList(2, 5);

            List<PlayerDTO> rows = counted.doSelectInOrFail(PlayerDTO.class, "unique_id", wanted);

            assertEquals(3, rows.size());
            assertEquals(new HashSet<>(wanted), rows.stream().map(dto -> dto.unique_id().toString()).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("PROPAGE l'echec d'une tranche en IllegalStateException")
        void selectIn_unknownColumn_throws() {
            newPlayer("x");

            assertThrows(IllegalStateException.class, () -> counted.doSelectInOrFail(PlayerDTO.class, "no_such_column", List.of("a", "b")));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("suppressions")
    class Deletes {

        @Test
        @DisplayName("delete rend le nombre de lignes supprimees et laisse les autres")
        void delete_returnsDeletedCount() throws Exception {
            UUID keep = newPlayer("keep");
            newPlayer("gone-a");
            newPlayer("gone-b");
            players.doUpdate(schema -> {
                schema.where("name", "gone-a");
                schema.string("name", "gone");
            });
            players.doUpdate(schema -> {
                schema.where("name", "gone-b");
                schema.string("name", "gone");
            });

            int deleted = players.doDelete(schema -> schema.where("name", "gone"));

            assertEquals(2, deleted);
            assertEquals(1, count(Tables.PLAYERS));
            assertEquals("keep", nameOf(keep));
        }

        @Test
        @DisplayName("delete rend 0 quand aucune ligne ne correspond")
        void delete_noMatch_returnsZero() throws Exception {
            newPlayer("keep");

            assertEquals(0, players.doDelete(schema -> schema.where("name", "nobody")));
            assertEquals(1, count(Tables.PLAYERS));
        }

        @Test
        @Disabled("BUG: Repository.delete documente '-1 if the operation failed' (Repository.java:319-327) et LogRepository.deleteByPlayer s'appuie dessus via Math.max(0, ...), mais Sarah leve une DatabaseException (RuntimeException), jamais une SQLException : le -1 est inatteignable et l'echec se propage")
        @DisplayName("delete rend -1 quand la suppression echoue")
        void delete_onFailure_returnsMinusOne() {
            assertEquals(-1, missing.doDelete(schema -> schema.where("name", "x")));
        }
    }
}
