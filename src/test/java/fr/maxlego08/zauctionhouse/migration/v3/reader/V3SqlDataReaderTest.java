package fr.maxlego08.zauctionhouse.migration.v3.reader;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.migration.v3.V3StorageType;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.V3SourceDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lecteur SQL V3 teste contre une base SQLite V3 FABRIQUEE ({@link V3SourceDatabase}).
 */
class V3SqlDataReaderTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;
    private V3SourceDatabase source;
    private final List<V3DataReader> openedReaders = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        this.plugin = MockPlugins.plugin(this.tempDir);
        this.source = V3SourceDatabase.create(new File(this.tempDir, "v3/database.db"));
    }

    /**
     * Les lecteurs gardent une connexion SQLite ouverte : il faut la fermer avant que JUnit
     * supprime le dossier temporaire (verrou de fichier sous Windows).
     */
    @AfterEach
    void closeReaders() {
        this.openedReaders.forEach(V3DataReader::close);
        this.openedReaders.clear();
    }

    private V3SqlDataReader reader() {
        return reader(this.source.path(), V3SourceDatabase.DEFAULT_PREFIX);
    }

    private V3SqlDataReader reader(String path, String prefix) {
        V3SqlDataReader reader = new V3SqlDataReader(this.plugin, path, prefix);
        this.openedReaders.add(reader);
        return reader;
    }

    @Nested
    @DisplayName("testConnection")
    class TestConnection {

        @Test
        @DisplayName("rend true sur une base SQLite existante")
        void existingDatabase_true() {
            assertTrue(reader().testConnection().join());
        }

        @Test
        @DisplayName("rend false et journalise en SEVERE quand le fichier ne peut pas etre ouvert")
        void unreachablePath_falseAndLogged() {
            String path = new File(tempDir, "missing/dir/database.db").getAbsolutePath();

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                assertFalse(reader(path, null).testConnection().join());
                assertTrue(logs.contains(Level.SEVERE, "Failed to connect to V3 database"));
            }
        }
    }

    @Nested
    @DisplayName("getItemCount / getTransactionCount")
    class Counts {

        @Test
        @DisplayName("comptent toutes les lignes, y compris celles que le parseur rejettera ensuite")
        void counts_everyRow() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));
            source.insert(new V3SourceDatabase.ItemRow().id("not-a-uuid").seller(ALICE));
            source.insert(V3SourceDatabase.transaction(ALICE, BOB));

            V3SqlDataReader reader = reader();

            assertEquals(3, reader.getItemCount().join().intValue());
            assertEquals(1, reader.getTransactionCount().join().intValue());
        }

        @Test
        @DisplayName("rendent 0 sur une base vide")
        void counts_emptyDatabase_zero() {
            V3SqlDataReader reader = reader();

            assertEquals(0, reader.getItemCount().join().intValue());
            assertEquals(0, reader.getTransactionCount().join().intValue());
        }

        @Test
        @DisplayName("rendent 0 et journalisent en SEVERE quand les tables n'existent pas (mauvais prefixe)")
        void counts_missingTables_zeroAndLogged() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));
            V3SqlDataReader reader = reader(source.path(), "wrong_");

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                assertEquals(0, reader.getItemCount().join().intValue());
                assertEquals(0, reader.getTransactionCount().join().intValue());
                assertTrue(logs.contains(Level.SEVERE, "Failed to count V3 items"));
                assertTrue(logs.contains(Level.SEVERE, "Failed to count V3 transactions"));
            }
        }
    }

    @Nested
    @DisplayName("readItems")
    class ReadItems {

        @Test
        @DisplayName("mappe chaque colonne de la table items V3")
        void mapsEveryColumn() throws Exception {
            UUID id = UUID.randomUUID();
            source.insert(new V3SourceDatabase.ItemRow().id(id).itemstack("stack-1").price(1500L).seller(ALICE).buyer(BOB)
                    .economy("PLAYER_POINTS").auctionType("INVENTORY").expireAt(1_800_000_000_000L).storageType("BUY")
                    .sellerName("Alice").serverName("lobby").priority(3));

            List<V3AuctionItem> items = reader().readItems().join();

            assertEquals(1, items.size());
            V3AuctionItem item = items.getFirst();
            assertEquals(id, item.getId());
            assertEquals("stack-1", item.getItemstack());
            assertEquals(1500L, item.getPrice());
            assertEquals(ALICE, item.getSeller());
            assertEquals(BOB, item.getBuyer());
            assertEquals("PLAYER_POINTS", item.getEconomy());
            assertEquals("INVENTORY", item.getAuctionType());
            assertTrue(item.isInventoryType());
            assertEquals(1_800_000_000_000L, item.getExpireAt());
            assertEquals(V3StorageType.BUY, item.getStorageType());
            assertEquals("Alice", item.getSellerName());
            assertEquals("lobby", item.getServerName());
            assertEquals(3, item.getPriority());
        }

        @ParameterizedTest(name = "storage_type=\"{0}\" -> {1}")
        @CsvSource({"STORAGE,STORAGE", "BUY,BUY", "EXPIRE,EXPIRE", "buy,BUY", "expire,EXPIRE", "garbage,STORAGE"})
        @DisplayName("storage_type est converti par V3StorageType.fromString")
        void storageType_mapping(String stored, V3StorageType expected) throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").storageType(stored));

            assertEquals(expected, reader().readItems().join().getFirst().getStorageType());
        }

        @Test
        @DisplayName("storage_type NULL retombe sur STORAGE")
        void storageType_null_defaultsToStorage() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").storageType(null));

            assertEquals(V3StorageType.STORAGE, reader().readItems().join().getFirst().getStorageType());
        }

        @Test
        @DisplayName("un acheteur NULL ou la chaine \"null\" donne un buyer null")
        void buyer_nullOrLiteralNull_mapsToNull() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").buyer((UUID) null));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").buyer("null"));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").buyer(""));

            List<V3AuctionItem> items = reader().readItems().join();

            assertEquals(3, items.size());
            items.forEach(item -> assertNull(item.getBuyer()));
        }

        @Test
        @DisplayName("un identifiant d'item invalide est ignore et journalise, les autres lignes sont lues")
        void invalidId_skippedAndLogged() throws Exception {
            source.insert(new V3SourceDatabase.ItemRow().id("not-a-uuid").seller(ALICE));
            source.insert(new V3SourceDatabase.ItemRow().id("").seller(ALICE));
            UUID valid = UUID.randomUUID();
            source.insert(V3SourceDatabase.item(ALICE, "Alice").id(valid));

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                List<V3AuctionItem> items = reader().readItems().join();

                assertEquals(1, items.size());
                assertEquals(valid, items.getFirst().getId());
                assertTrue(logs.contains(Level.WARNING, "Invalid UUID for item: not-a-uuid"));
                assertEquals(2, logs.count(Level.WARNING));
            }
        }

        @Test
        @DisplayName("un vendeur invalide n'est PAS filtre par le lecteur : la ligne sort avec seller null")
        void invalidSeller_keptWithNullSeller() throws Exception {
            source.insert(new V3SourceDatabase.ItemRow().seller("corrupted"));

            List<V3AuctionItem> items = reader().readItems().join();

            assertEquals(1, items.size());
            assertNull(items.getFirst().getSeller());
        }

        @Test
        @DisplayName("une charge utile NULL n'est PAS filtree par le lecteur")
        void nullItemstack_kept() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack(null));

            List<V3AuctionItem> items = reader().readItems().join();

            assertEquals(1, items.size());
            assertNull(items.getFirst().getItemstack());
        }

        @Test
        @DisplayName("lit la table prefixee par le prefixe fourni")
        void customPrefix() throws Exception {
            V3SourceDatabase custom = V3SourceDatabase.create(new File(tempDir, "custom/database.db"), "old_zah_");
            custom.insert(V3SourceDatabase.item(ALICE, "Alice"));

            assertEquals(1, reader(custom.path(), "old_zah_").readItems().join().size());
            assertEquals(1, reader(custom.path(), "old_zah_").getItemCount().join().intValue());
        }

        @Test
        @DisplayName("un prefixe null retombe sur \"zauctionhouse_\"")
        void nullPrefix_defaultsToZauctionhouse() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));

            assertEquals(1, reader(source.path(), null).readItems().join().size());
        }

        @Test
        @DisplayName("rend une liste vide et journalise en SEVERE quand la table n'existe pas")
        void missingTable_emptyAndLogged() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                assertTrue(reader(source.path(), "wrong_").readItems().join().isEmpty());
                assertTrue(logs.contains(Level.SEVERE, "Failed to read V3 items"));
            }
        }

        @Test
        @DisplayName("lit toutes les lignes, dans n'importe quel ordre, sans doublon")
        void manyRows_allRead() throws Exception {
            List<UUID> ids = new ArrayList<>();
            for (int index = 0; index < 60; index++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                source.insert(V3SourceDatabase.item(ALICE, "Alice").id(id).price(index));
            }

            List<V3AuctionItem> items = reader().readItems().join();

            assertEquals(60, items.size());
            assertEquals(60, items.stream().map(V3AuctionItem::getId).distinct().count());
            assertTrue(items.stream().map(V3AuctionItem::getId).toList().containsAll(ids));
        }

        @Test
        @Disabled("BUG: V3SqlDataReader.readItems (V3SqlDataReader.java:~78) nomme server_name et priority dans le SELECT ; sur une V3 jamais mise a jour (sans UpdateItemAddServerNameMigration/UpdateItemAddPriority) la requete entiere echoue et ZERO item est lu, alors que le try/catch de parseItem sur priority laisse croire que l'ancien schema est supporte")
        @DisplayName("une V3 au schema historique (sans server_name ni priority) est lue avec priority 0")
        void legacySchema_readWithDefaultPriority() throws Exception {
            V3SourceDatabase legacy = V3SourceDatabase.createLegacy(new File(tempDir, "legacy/database.db"));
            legacy.insert(V3SourceDatabase.item(ALICE, "Alice"));

            List<V3AuctionItem> items = reader(legacy.path(), null).readItems().join();

            assertEquals(1, items.size());
            assertEquals(0, items.getFirst().getPriority());
            assertNull(items.getFirst().getServerName());
        }
    }

    @Nested
    @DisplayName("readTransactions")
    class ReadTransactions {

        @Test
        @DisplayName("mappe chaque colonne de la table transactions V3, booleens inclus")
        void mapsEveryColumn() throws Exception {
            int id = source.insert(V3SourceDatabase.transaction(ALICE, BOB).itemstack("tx-stack").date(1_600_000_000_000L)
                    .price(99L).economy("VAULT").read(true).needMoney(false));

            List<V3Transaction> transactions = reader().readTransactions().join();

            assertEquals(1, transactions.size());
            V3Transaction transaction = transactions.getFirst();
            assertEquals(id, transaction.getId());
            assertEquals(ALICE, transaction.getSeller());
            assertEquals(BOB, transaction.getBuyer());
            assertEquals("tx-stack", transaction.getItemstack());
            assertEquals(1_600_000_000_000L, transaction.getTransactionDate());
            assertEquals(99L, transaction.getPrice());
            assertEquals("VAULT", transaction.getEconomy());
            assertTrue(transaction.isRead());
            assertFalse(transaction.isNeedMoney());
        }

        @Test
        @DisplayName("is_read=0 et need_money=1 sont lus comme false et true")
        void booleans_zeroAndOne() throws Exception {
            source.insert(V3SourceDatabase.transaction(ALICE, BOB).read(false).needMoney(true));

            V3Transaction transaction = reader().readTransactions().join().getFirst();

            assertFalse(transaction.isRead());
            assertTrue(transaction.isNeedMoney());
        }

        @Test
        @DisplayName("un vendeur ou un acheteur NULL/invalide n'est PAS filtre par le lecteur")
        void nullOrInvalidParties_keptAsNull() throws Exception {
            source.insert(V3SourceDatabase.transaction(null, BOB));
            source.insert(new V3SourceDatabase.TransactionRow().seller(ALICE).buyer("broken"));

            List<V3Transaction> transactions = reader().readTransactions().join();

            assertEquals(2, transactions.size());
            assertNull(transactions.get(0).getSeller());
            assertEquals(BOB, transactions.get(0).getBuyer());
            assertEquals(ALICE, transactions.get(1).getSeller());
            assertNull(transactions.get(1).getBuyer());
        }

        @Test
        @DisplayName("rend une liste vide et journalise en SEVERE quand la table n'existe pas")
        void missingTable_emptyAndLogged() {
            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                assertTrue(reader(source.path(), "wrong_").readTransactions().join().isEmpty());
                assertTrue(logs.contains(Level.SEVERE, "Failed to read V3 transactions"));
            }
        }

        @Test
        @DisplayName("rend une liste vide sur une table vide")
        void emptyTable_empty() {
            assertTrue(reader().readTransactions().join().isEmpty());
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("close() sans connexion ouverte ne fait rien")
        void close_withoutConnection_noop() {
            V3SqlDataReader reader = new V3SqlDataReader(plugin, source.path(), null);

            reader.close();
            reader.close();
        }

        @Test
        @DisplayName("apres close(), une nouvelle lecture rouvre la connexion")
        void close_thenRead_reopens() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));
            V3SqlDataReader reader = reader();
            assertTrue(reader.testConnection().join());

            reader.close();

            assertEquals(1, reader.readItems().join().size());
            assertTrue(reader.testConnection().join());
        }
    }
}
