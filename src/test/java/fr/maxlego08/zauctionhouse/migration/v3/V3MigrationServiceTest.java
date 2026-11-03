package fr.maxlego08.zauctionhouse.migration.v3;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;
import fr.maxlego08.zauctionhouse.migration.v3.reader.V3DataReader;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.V3SourceDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Import V3 -> V4 sur une base V4 SQLite REELLE (vrai {@code ZStorageManager}), depuis une base
 * V3 SQLite FABRIQUEE ou depuis un lecteur mocke pour les lignes corrompues.
 */
class V3MigrationServiceTest extends StorageManagerTestBase {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());
    private static final UUID CAROL = UUID.nameUUIDFromBytes("carol".getBytes());
    private static final UUID DAVE = UUID.nameUUIDFromBytes("dave".getBytes());

    private static final long EXPIRE_AT = 1_800_000_000_000L;
    private static final long TX_DATE = 1_600_000_000_000L;

    private V3SourceDatabase source;
    private V3MigrationService service;
    private List<String> progress;

    @BeforeEach
    void setUpSource() throws Exception {
        this.source = V3SourceDatabase.create(new File(this.dataFolder, "v3/database.db"));
        this.progress = new ArrayList<>();
        this.service = new V3MigrationService(this.plugin).onProgress(this.progress::add);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private V3MigrationResult migrateSqlite() {
        return this.service.migrateFromSqlite(this.source.path(), V3SourceDatabase.DEFAULT_PREFIX).join();
    }

    private static V3AuctionItem v3Item(UUID seller, String sellerName, UUID buyer, String payload, String auctionType, V3StorageType storageType, String economy) {
        return new V3AuctionItem(UUID.randomUUID(), payload, 100L, seller, buyer, economy, auctionType, EXPIRE_AT, storageType, sellerName, null, 0);
    }

    private static V3AuctionItem v3Item(UUID seller, String sellerName, String payload) {
        return v3Item(seller, sellerName, null, payload, "DEFAULT", V3StorageType.STORAGE, "VAULT");
    }

    private static V3Transaction v3Transaction(int id, UUID seller, UUID buyer, long price, boolean needMoney) {
        return new V3Transaction(id, seller, buyer, "tx-" + id, TX_DATE, price, "VAULT", false, needMoney);
    }

    /**
     * Lecteur mocke : les futures sont construits AVANT les when(...) (regle Mockito).
     */
    private static V3DataReader readerOf(List<V3AuctionItem> items, List<V3Transaction> transactions) {
        CompletableFuture<Boolean> connected = CompletableFuture.completedFuture(true);
        CompletableFuture<Integer> itemCount = CompletableFuture.completedFuture(items.size());
        CompletableFuture<Integer> transactionCount = CompletableFuture.completedFuture(transactions.size());
        CompletableFuture<List<V3AuctionItem>> itemsFuture = CompletableFuture.completedFuture(items);
        CompletableFuture<List<V3Transaction>> transactionsFuture = CompletableFuture.completedFuture(transactions);

        V3DataReader reader = mock(V3DataReader.class);
        when(reader.testConnection()).thenReturn(connected);
        when(reader.getItemCount()).thenReturn(itemCount);
        when(reader.getTransactionCount()).thenReturn(transactionCount);
        when(reader.readItems()).thenReturn(itemsFuture);
        when(reader.readTransactions()).thenReturn(transactionsFuture);
        return reader;
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private static void assertDecimalEquals(long expected, Object actual) {
        assertNotNull(actual);
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(decimal(actual)), "expected " + expected + " but was " + actual);
    }

    private long itemId(UUID seller, StorageType storageType) throws SQLException {
        return queryLong("SELECT id FROM " + Tables.ITEMS + " WHERE seller_unique_id = ? AND storage_type = ?", seller.toString(), storageType.name());
    }

    private long nonDeletedItems() throws SQLException {
        return count(Tables.ITEMS, "storage_type != ?", StorageType.DELETED.name());
    }

    private long sentinels() throws SQLException {
        return count(Tables.ITEMS, "storage_type = ?", StorageType.DELETED.name());
    }

    // ------------------------------------------------------------------------------------------
    // Import complet depuis SQLite
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("import complet depuis une base SQLite V3")
    class FullSqliteImport {

        private V3MigrationResult result;

        @BeforeEach
        void migrateFixture() throws Exception {
            // i1 : en vente, vendeur Alice, serveur "lobby"
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("stack-1").price(1500L).storageType("STORAGE").serverName("lobby").priority(2));
            // i2 : achete par Bob (V3 "BUY" -> V4 PURCHASED), economie PlayerPoints, serveur inconnu
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("stack-2").price(250L).buyer(BOB).storageType("BUY").economy("PLAYER_POINTS"));
            // i3 : expire (V3 "EXPIRE" -> V4 EXPIRED), lot INVENTORY de 3 contenus
            source.insert(V3SourceDatabase.item(CAROL, "Carol").itemstack("a;b; c ;;").auctionType("INVENTORY").storageType("EXPIRE").expireAt(1_700_000_000_000L));
            // t1 : Bob a achete a Alice, argent en attente, non lu
            source.insert(V3SourceDatabase.transaction(ALICE, BOB).itemstack("tx-stack").date(TX_DATE).price(99L).read(false).needMoney(true));
            // t2 : Dave (inconnu ailleurs) a achete a Carol, deja paye, lu
            source.insert(V3SourceDatabase.transaction(CAROL, DAVE).itemstack("tx-stack-2").date(TX_DATE + 1000).price(10L).read(true).needMoney(false));

            this.result = migrateSqlite();
        }

        @Test
        @DisplayName("le resultat est un succes et porte les compteurs exacts")
        void result_countersMatch() {
            assertTrue(result.isSuccess(), result.toString());
            assertEquals(4, result.getPlayersImported());
            assertEquals(3, result.getItemsImported());
            assertEquals(2, result.getTransactionsImported());
            assertEquals(0, result.getErrors());
            assertNull(result.getErrorMessage());
            assertTrue(result.getDurationMs() >= 0);
        }

        @Test
        @DisplayName("les joueurs sont inseres une seule fois, avec le pseudo connu ou \"Unknown\"")
        void players_upserted() throws Exception {
            assertEquals(4, count(Tables.PLAYERS));
            assertEquals("Alice", players().select(ALICE));
            assertEquals("Unknown", players().select(BOB));
            assertEquals("Carol", players().select(CAROL));
            assertEquals("Unknown", players().select(DAVE));
        }

        @Test
        @DisplayName("chaque item V3 part dans le conteneur V4 correspondant avec tous ses champs")
        void items_storedInRightBucket() throws Exception {
            assertEquals(3, nonDeletedItems());

            long listed = itemId(ALICE, StorageType.LISTED);
            assertTrue(listed > 0);
            List<Object[]> rows = queryRows("SELECT item_type, buyer_unique_id, price, economy_name, server_name, expired_at, pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", listed);
            Object[] row = rows.getFirst();
            assertEquals(ItemType.AUCTION.name(), row[0]);
            assertNull(row[1]);
            assertDecimalEquals(1500L, row[2]);
            assertEquals("VAULT", row[3]);
            assertEquals("lobby", row[4]);
            assertEquals(EXPIRE_AT, ((Number) row[5]).longValue());
            assertNull(row[6], "pending_publish doit rester NULL sur une ligne migree");

            long purchased = itemId(ALICE, StorageType.PURCHASED);
            assertTrue(purchased > 0);
            assertEquals(BOB.toString(), queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", purchased));
            assertEquals("PLAYER_POINTS", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE id = ?", purchased));
            assertEquals("test-server", queryString("SELECT server_name FROM " + Tables.ITEMS + " WHERE id = ?", purchased), "server_name V3 null -> nom du serveur courant");
            assertDecimalEquals(250L, queryObject("SELECT price FROM " + Tables.ITEMS + " WHERE id = ?", purchased));

            long expired = itemId(CAROL, StorageType.EXPIRED);
            assertTrue(expired > 0);
            assertEquals(1_700_000_000_000L, queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", expired));
        }

        @Test
        @DisplayName("les items sont visibles par ItemRepository.select() avec les bons types")
        void items_visibleThroughRepository() {
            List<ItemDTO> items = items().select();

            assertEquals(3, items.size());
            assertEquals(1, items.stream().filter(item -> item.storage_type() == StorageType.LISTED).count());
            assertEquals(1, items.stream().filter(item -> item.storage_type() == StorageType.PURCHASED).count());
            assertEquals(1, items.stream().filter(item -> item.storage_type() == StorageType.EXPIRED).count());
            items.forEach(item -> assertEquals(ItemType.AUCTION, item.item_type()));
            ItemDTO listed = items.stream().filter(item -> item.storage_type() == StorageType.LISTED).findFirst().orElseThrow();
            assertEquals(ALICE, listed.seller_unique_id());
            assertEquals(EXPIRE_AT, listed.expired_at().getTime());
            assertEquals(0, BigDecimal.valueOf(1500).compareTo(listed.price()));
        }

        @Test
        @DisplayName("un item simple donne un contenu, un lot INVENTORY est decoupe sur ';' et nettoye")
        void auctionItems_contents() throws Exception {
            assertEquals(5, count(Tables.AUCTION_ITEMS));

            long listed = itemId(ALICE, StorageType.LISTED);
            assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", listed));
            assertEquals("stack-1", queryString("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " WHERE item_id = ?", listed));

            long expired = itemId(CAROL, StorageType.EXPIRED);
            List<Object[]> contents = queryRows("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " WHERE item_id = ? ORDER BY id", expired);
            assertEquals(3, contents.size());
            assertEquals("a", contents.get(0)[0]);
            assertEquals("b", contents.get(1)[0]);
            assertEquals("c", contents.get(2)[0]);
        }

        @Test
        @DisplayName("chaque transaction V3 obtient une ligne items sentinelle DELETED, invisible pour ItemRepository")
        void transactions_sentinelRows() throws Exception {
            assertEquals(2, sentinels());
            assertEquals(5, count(Tables.ITEMS));

            long sentinel = queryLong("SELECT id FROM " + Tables.ITEMS + " WHERE storage_type = ? AND seller_unique_id = ? AND buyer_unique_id = ?",
                    StorageType.DELETED.name(), ALICE.toString(), BOB.toString());
            assertTrue(sentinel > 0);
            assertTrue(items().select((int) sentinel).isEmpty(), "une sentinelle ne doit jamais etre chargee");
            assertEquals(TX_DATE, queryLong("SELECT created_at FROM " + Tables.ITEMS + " WHERE id = ?", sentinel));
            assertEquals(TX_DATE, queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", sentinel));
            assertDecimalEquals(99L, queryObject("SELECT price FROM " + Tables.ITEMS + " WHERE id = ?", sentinel));
            assertEquals("VAULT", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE id = ?", sentinel));
            assertEquals("test-server", queryString("SELECT server_name FROM " + Tables.ITEMS + " WHERE id = ?", sentinel));
        }

        @Test
        @DisplayName("l'argent en attente devient une transaction PENDING du VENDEUR, rattachee a la sentinelle")
        void pendingMoney_becomesPendingTransaction() throws Exception {
            assertEquals(1, count(Tables.TRANSACTIONS), "seule t1 (need_money) produit une ligne");

            long sentinel = queryLong("SELECT id FROM " + Tables.ITEMS + " WHERE storage_type = ? AND seller_unique_id = ? AND buyer_unique_id = ?",
                    StorageType.DELETED.name(), ALICE.toString(), BOB.toString());
            List<Object[]> rows = queryRows("SELECT item_id, player_unique_id, economy_name, before, after, value, status, created_at, claim_token FROM " + Tables.TRANSACTIONS);
            Object[] row = rows.getFirst();
            assertEquals(sentinel, ((Number) row[0]).longValue());
            assertEquals(ALICE.toString(), row[1]);
            assertEquals("VAULT", row[2]);
            assertDecimalEquals(0L, row[3]);
            assertDecimalEquals(0L, row[4]);
            assertDecimalEquals(99L, row[5]);
            assertEquals(TransactionStatus.PENDING.name(), row[6]);
            assertEquals(TX_DATE, ((Number) row[7]).longValue());
            assertNull(row[8]);

            List<TransactionDTO> pending = transactions().selectByPlayerAndStatus(ALICE, TransactionStatus.PENDING);
            assertEquals(1, pending.size());
            assertEquals(0, BigDecimal.valueOf(99).compareTo(pending.getFirst().value()));
            assertTrue(transactions().selectByPlayer(CAROL).isEmpty());
            assertTrue(transactions().selectByPlayer(BOB).isEmpty());
        }

        @Test
        @DisplayName("chaque transaction V3 produit un log PURCHASE marque migrated_from_v3")
        void transactions_becomeMigratedLogs() throws Exception {
            assertEquals(2, count(Tables.LOGS));
            assertEquals(2, count(Tables.LOGS, "additional_data = ?", LogRepository.MIGRATED_FROM_V3));

            List<Object[]> rows = queryRows("SELECT log_type, item_id, player_unique_id, target_unique_id, itemstack, price, economy_name, readed_at, created_at FROM "
                    + Tables.LOGS + " WHERE target_unique_id = ?", ALICE.toString());
            assertEquals(1, rows.size());
            Object[] row = rows.getFirst();
            assertEquals(LogType.PURCHASE.name(), row[0]);
            long sentinel = queryLong("SELECT id FROM " + Tables.ITEMS + " WHERE storage_type = ? AND seller_unique_id = ? AND buyer_unique_id = ?",
                    StorageType.DELETED.name(), ALICE.toString(), BOB.toString());
            assertEquals(sentinel, ((Number) row[1]).longValue());
            assertEquals(BOB.toString(), row[2], "player = acheteur");
            assertEquals(ALICE.toString(), row[3], "target = vendeur");
            assertEquals("tx-stack", row[4]);
            assertDecimalEquals(99L, row[5]);
            assertEquals("VAULT", row[6]);
            assertNull(row[7], "t1 n'est pas lue");
            assertEquals(TX_DATE, ((Number) row[8]).longValue());

            Object readedAt = queryObject("SELECT readed_at FROM " + Tables.LOGS + " WHERE target_unique_id = ?", CAROL.toString());
            assertNotNull(readedAt, "t2 est lue : readed_at est renseigne");
            assertEquals(TX_DATE + 1000, ((Number) readedAt).longValue());
        }

        @Test
        @DisplayName("les logs migres sont lisibles par LogRepository (ventes non lues d'Alice, historique de Carol)")
        void logs_readableThroughRepository() {
            List<LogDTO> aliceUnread = logs().selectUnreadSales(ALICE);
            assertEquals(1, aliceUnread.size());
            assertFalse(aliceUnread.getFirst().isRead());
            assertEquals(LogRepository.MIGRATED_FROM_V3, aliceUnread.getFirst().additional_data());
            assertEquals(TX_DATE, aliceUnread.getFirst().created_at().getTime());

            assertTrue(logs().selectUnreadSales(CAROL).isEmpty(), "t2 est deja lue");
            List<LogDTO> carolHistory = logs().selectByTarget(CAROL);
            assertEquals(1, carolHistory.size());
            assertTrue(carolHistory.getFirst().isRead());
        }

        @Test
        @DisplayName("deleteMigrated() purge exactement les logs migres et laisse les logs natifs")
        void deleteMigrated_purgesOnlyMigratedLogs() throws Exception {
            long listed = itemId(ALICE, StorageType.LISTED);
            logs().createLog(LogType.PURCHASE, (int) listed, BOB, ALICE, "native", BigDecimal.ONE, "vault", null, null);
            assertEquals(3, count(Tables.LOGS));

            assertEquals(2, logs().deleteMigrated());

            assertEquals(1, count(Tables.LOGS));
            assertNull(queryString("SELECT additional_data FROM " + Tables.LOGS));
            assertEquals(0, logs().deleteMigrated());
        }

        @Test
        @DisplayName("le service n'ecrit JAMAIS la sentinelle d'idempotence : c'est la commande qui s'en charge")
        void service_doesNotWriteMigrationState() throws Exception {
            assertEquals(0, count(MigrationStateRepository.TABLE_NAME));
            assertTrue(migrationState().select("zauctionhousev3").isEmpty());
        }

        @Test
        @DisplayName("la progression est remontee au callback, dans l'ordre")
        void progress_reported() {
            assertTrue(progress.contains("Testing connection to V3 data source..."));
            assertTrue(progress.contains("Connection successful!"));
            assertTrue(progress.contains("Found 3 items and 2 transactions to migrate"));
            assertTrue(progress.contains("Found 4 unique players"));
            assertTrue(progress.contains("Migrated 4 players"));
            assertTrue(progress.contains("Migrated 3 items"));
            assertTrue(progress.contains("Migrated 2 transactions"));
            assertTrue(progress.stream().anyMatch(message -> message.startsWith("Migration completed in ")));
            assertTrue(progress.indexOf("Connection successful!") < progress.indexOf("Migrated 4 players"));
        }

        @Test
        @DisplayName("rejouer la migration DUPLIQUE tout (aucune cle d'unicite cote V4) : d'ou la sentinelle de la commande")
        void secondRun_duplicatesEverything() throws Exception {
            V3MigrationResult second = migrateSqlite();

            assertTrue(second.isSuccess());
            assertEquals(0, second.getPlayersImported(), "les joueurs existent deja");
            assertEquals(3, second.getItemsImported());
            assertEquals(2, second.getTransactionsImported());
            assertEquals(6, nonDeletedItems());
            assertEquals(4, sentinels());
            assertEquals(10, count(Tables.AUCTION_ITEMS));
            assertEquals(2, count(Tables.TRANSACTIONS), "l'argent en attente est compte deux fois");
            assertEquals(4, count(Tables.LOGS));
            assertEquals(4, count(Tables.PLAYERS));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Echecs globaux
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("echecs globaux")
    class GlobalFailures {

        @Test
        @DisplayName("une base V3 vide est un echec explicite, rien n'est ecrit")
        void emptySource_failsWithNoData() throws Exception {
            V3MigrationResult result = migrateSqlite();

            assertFalse(result.isSuccess());
            assertEquals("No data found to migrate", result.getErrorMessage());
            assertEquals(0, count(Tables.PLAYERS));
            assertEquals(0, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("une source injoignable est un echec de connexion explicite")
        void unreachableSource_failsWithConnectionError() {
            String path = new File(dataFolder, "missing/dir/database.db").getAbsolutePath();

            V3MigrationResult result = service.migrateFromSqlite(path, null).join();

            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to V3 data source", result.getErrorMessage());
        }

        @Test
        @DisplayName("un lecteur qui refuse la connexion donne un echec, et le lecteur est ferme")
        void readerRefusesConnection_failsAndCloses() {
            CompletableFuture<Boolean> refused = CompletableFuture.completedFuture(false);
            V3DataReader reader = mock(V3DataReader.class);
            when(reader.testConnection()).thenReturn(refused);

            V3MigrationResult result = service.migrate(reader).join();

            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to V3 data source", result.getErrorMessage());
            verify(reader).close();
        }

        @Test
        @DisplayName("un lecteur qui leve une exception donne un echec 'Migration failed: ...' et le lecteur est ferme")
        void readerThrows_failsAndCloses() {
            CompletableFuture<Boolean> failed = CompletableFuture.failedFuture(new IllegalStateException("disk on fire"));
            V3DataReader reader = mock(V3DataReader.class);
            when(reader.testConnection()).thenReturn(failed);

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                V3MigrationResult result = service.migrate(reader).join();

                assertFalse(result.isSuccess());
                assertTrue(result.getErrorMessage().startsWith("Migration failed: "));
                assertTrue(result.getErrorMessage().contains("disk on fire"));
                assertTrue(logs.contains(Level.SEVERE, "Migration failed"));
            }
            verify(reader).close();
        }

        @Test
        @DisplayName("un lecteur sans item mais avec des transactions est accepte")
        void transactionsOnly_isNotEmpty() throws Exception {
            V3DataReader reader = readerOf(List.of(), List.of(v3Transaction(1, ALICE, BOB, 5L, true)));

            V3MigrationResult result = service.migrate(reader).join();

            assertTrue(result.isSuccess());
            assertEquals(0, result.getItemsImported());
            assertEquals(1, result.getTransactionsImported());
            assertEquals(2, result.getPlayersImported());
            assertEquals(1, count(Tables.TRANSACTIONS));
            verify(reader).close();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Items corrompus
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("items corrompus")
    class CorruptedItems {

        @Test
        @DisplayName("un item sans vendeur est saute, compte en erreur, et rien n'est ecrit")
        void itemWithoutSeller_skipped() throws Exception {
            V3AuctionItem corrupted = v3Item(null, null, "payload");
            V3DataReader reader = readerOf(List.of(corrupted, v3Item(ALICE, "Alice", "ok")), List.of());

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                V3MigrationResult result = service.migrate(reader).join();

                assertTrue(result.isSuccess());
                assertEquals(1, result.getItemsImported());
                assertEquals(1, result.getErrors());
                assertTrue(logs.contains(Level.WARNING, "Skipping V3 item " + corrupted.getId() + ": no seller"));
            }
            assertEquals(1, count(Tables.ITEMS));
            assertEquals(1, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("un item a la charge utile NULL ou blanche est saute et compte en erreur")
        void itemWithBlankPayload_skipped() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack(null));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("   "));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("ok"));

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                V3MigrationResult result = migrateSqlite();

                assertTrue(result.isSuccess());
                assertEquals(1, result.getItemsImported());
                assertEquals(2, result.getErrors());
                assertEquals(2, logs.messages(Level.WARNING).stream().filter(message -> message.contains("empty itemstack payload")).count());
            }
            assertEquals(1, count(Tables.ITEMS));
            assertEquals("ok", queryString("SELECT itemstack FROM " + Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("un lot INVENTORY dont la charge utile ne contient que des separateurs est annule : la ligne parente est supprimee")
        void inventoryPayloadOnlySeparators_rolledBack() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack(";").auctionType("INVENTORY"));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack(" ; ;").auctionType("INVENTORY"));

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                V3MigrationResult result = migrateSqlite();

                assertTrue(result.isSuccess());
                assertEquals(0, result.getItemsImported());
                assertEquals(2, result.getErrors());
                assertEquals(2, logs.messages(Level.SEVERE).stream().filter(message -> message.contains("Failed to migrate V3 item")).count());
                assertTrue(logs.contains(Level.SEVERE, "no readable ItemStack"));
                assertEquals(2, logs.messages(Level.WARNING).stream().filter(message -> message.contains("Rolled back the orphan V4 item row #")).count());
            }
            assertEquals(0, count(Tables.ITEMS), "aucune annonce vide ne doit rester vendable");
            assertEquals(0, count(Tables.AUCTION_ITEMS));
            assertEquals(1, count(Tables.PLAYERS), "le joueur reste insere");
        }

        @Test
        @DisplayName("un lot INVENTORY sans separateur donne un seul contenu")
        void inventoryWithoutSeparator_singleContent() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("single").auctionType("INVENTORY"));

            assertEquals(1, migrateSqlite().getItemsImported());
            assertEquals(1, count(Tables.AUCTION_ITEMS));
            assertEquals("single", queryString("SELECT itemstack FROM " + Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("un item DEFAULT n'est jamais decoupe, meme si sa charge utile contient ';'")
        void defaultTypeWithSemicolon_notSplit() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("x;y").auctionType("DEFAULT"));

            assertEquals(1, migrateSqlite().getItemsImported());
            assertEquals(1, count(Tables.AUCTION_ITEMS));
            assertEquals("x;y", queryString("SELECT itemstack FROM " + Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("un lot INVENTORY aux separateurs redondants ne garde que les contenus non vides")
        void inventoryWithEmptySegments_keepsNonEmpty() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack(";;first;; second ;").auctionType("INVENTORY"));

            migrateSqlite();

            List<Object[]> rows = queryRows("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " ORDER BY id");
            assertEquals(2, rows.size());
            assertEquals("first", rows.get(0)[0]);
            assertEquals("second", rows.get(1)[0]);
        }

        @Test
        @DisplayName("une economie NULL retombe sur \"vault\", une economie inconnue est stockee telle quelle")
        void economy_nullDefaultsToVault_unknownStoredVerbatim() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("one").economy(null));
            source.insert(V3SourceDatabase.item(BOB, "Bob").itemstack("two").economy("COINS_FROM_MARS"));

            V3MigrationResult result = migrateSqlite();

            assertTrue(result.isSuccess());
            assertEquals(0, result.getErrors(), "le service ne valide pas l'economie : c'est le chargement qui tranchera");
            assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE seller_unique_id = ?", ALICE.toString()));
            assertEquals("COINS_FROM_MARS", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE seller_unique_id = ?", BOB.toString()));
        }

        @Test
        @DisplayName("un acheteur renseigne sur un item en vente est conserve")
        void buyer_preserved() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").buyer(BOB).storageType("BUY"));

            migrateSqlite();

            assertEquals(BOB.toString(), queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS));
            assertEquals(2, count(Tables.PLAYERS));
        }

        @Test
        @DisplayName("au-dela de 100 items, une ligne de progression intermediaire est emise")
        void manyItems_progressEveryHundred() throws Exception {
            List<V3AuctionItem> items = new ArrayList<>();
            for (int index = 0; index < 120; index++) items.add(v3Item(ALICE, "Alice", "payload-" + index));

            V3MigrationResult result = service.migrate(readerOf(items, List.of())).join();

            assertEquals(120, result.getItemsImported());
            assertEquals(120, count(Tables.ITEMS));
            assertEquals(120, count(Tables.AUCTION_ITEMS));
            assertTrue(progress.contains("Migrated 100/120 items..."));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Joueurs
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("joueurs")
    class Players {

        @Test
        @DisplayName("le pseudo deja connu de la V4 n'est JAMAIS ecrase par la migration")
        void existingPlayerName_notOverwritten() throws Exception {
            players().upsertPlayer(ALICE, "AliceLive");
            source.insert(V3SourceDatabase.item(ALICE, "OldAlice"));

            V3MigrationResult result = migrateSqlite();

            assertEquals(0, result.getPlayersImported());
            assertEquals("AliceLive", players().select(ALICE));
            assertEquals(1, count(Tables.PLAYERS));
        }

        @Test
        @DisplayName("un pseudo deja connu en V4 sous \"Unknown\" n'est pas non plus remplace (seul l'INSERT est fait)")
        void existingUnknownName_notOverwritten() throws Exception {
            players().upsertPlayer(ALICE, "Unknown");
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));

            migrateSqlite();

            assertEquals("Unknown", players().select(ALICE));
        }

        @Test
        @DisplayName("un vrai pseudo l'emporte sur \"Unknown\" quel que soit l'ordre de rencontre")
        void realNameWinsOverUnknown_anyOrder() throws Exception {
            // Alice est d'abord rencontree comme ACHETEUSE (sans pseudo), puis comme vendeuse.
            V3AuctionItem asBuyer = v3Item(BOB, "Bob", ALICE, "p1", "DEFAULT", V3StorageType.BUY, "VAULT");
            V3AuctionItem asSeller = v3Item(ALICE, "Alice", "p2");

            service.migrate(readerOf(List.of(asBuyer, asSeller), List.of())).join();

            assertEquals("Alice", players().select(ALICE));
            assertEquals("Bob", players().select(BOB));
        }

        @Test
        @DisplayName("un pseudo blanc compte comme inconnu")
        void blankName_isUnknown() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "   "));

            migrateSqlite();

            assertEquals("Unknown", players().select(ALICE));
        }

        @Test
        @DisplayName("un joueur present dans plusieurs items et transactions n'est insere qu'une fois")
        void duplicatePlayers_insertedOnce() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").buyer(BOB));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").buyer(BOB));
            source.insert(V3SourceDatabase.item(BOB, "Bob").buyer(ALICE));
            source.insert(V3SourceDatabase.transaction(ALICE, BOB));
            source.insert(V3SourceDatabase.transaction(BOB, ALICE));

            V3MigrationResult result = migrateSqlite();

            assertEquals(2, result.getPlayersImported());
            assertEquals(2, count(Tables.PLAYERS));
            assertEquals("Alice", players().select(ALICE));
            assertEquals("Bob", players().select(BOB));
        }

        @Test
        @DisplayName("les parties d'une transaction sont inserees meme si elles n'ont aucun item")
        void transactionParties_inserted() throws Exception {
            source.insert(V3SourceDatabase.transaction(CAROL, DAVE));

            V3MigrationResult result = migrateSqlite();

            assertEquals(2, result.getPlayersImported());
            assertEquals("Unknown", players().select(CAROL));
            assertEquals("Unknown", players().select(DAVE));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Transactions corrompues
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("transactions corrompues")
    class CorruptedTransactions {

        @Test
        @DisplayName("une transaction sans vendeur ou sans acheteur est sautee : ni sentinelle, ni log, ni argent")
        void missingParty_skipped() throws Exception {
            V3DataReader reader = readerOf(List.of(), List.of(
                    v3Transaction(1, null, BOB, 10L, true),
                    v3Transaction(2, ALICE, null, 10L, true),
                    v3Transaction(3, ALICE, BOB, 10L, true)));

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                V3MigrationResult result = service.migrate(reader).join();

                assertTrue(result.isSuccess());
                assertEquals(1, result.getTransactionsImported());
                assertEquals(2, result.getErrors());
                assertTrue(logs.contains(Level.WARNING, "Skipping V3 transaction 1: missing seller or buyer"));
                assertTrue(logs.contains(Level.WARNING, "Skipping V3 transaction 2: missing seller or buyer"));
            }
            assertEquals(1, sentinels());
            assertEquals(1, count(Tables.LOGS));
            assertEquals(1, count(Tables.TRANSACTIONS));
        }

        @Test
        @DisplayName("sans argent en attente, aucune ligne transactions n'est creee mais le log et la sentinelle le sont")
        void noPendingMoney_logOnly() throws Exception {
            source.insert(V3SourceDatabase.transaction(ALICE, BOB).needMoney(false));

            V3MigrationResult result = migrateSqlite();

            assertEquals(1, result.getTransactionsImported());
            assertEquals(0, count(Tables.TRANSACTIONS));
            assertEquals(1, count(Tables.LOGS));
            assertEquals(1, sentinels());
        }

        @Test
        @DisplayName("une transaction a l'economie NULL retombe sur \"vault\" pour la sentinelle, le log et l'argent")
        void nullEconomy_defaultsToVault() throws Exception {
            source.insert(V3SourceDatabase.transaction(ALICE, BOB).economy(null).needMoney(true));

            migrateSqlite();

            assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.ITEMS));
            assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.LOGS));
            assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.TRANSACTIONS));
        }

        @Test
        @DisplayName("une transaction a la charge utile NULL est importee avec un log sans itemstack")
        void nullItemstack_logWithoutPayload() throws Exception {
            source.insert(V3SourceDatabase.transaction(ALICE, BOB).itemstack(null));

            V3MigrationResult result = migrateSqlite();

            assertEquals(1, result.getTransactionsImported());
            assertEquals(0, result.getErrors());
            assertNull(queryString("SELECT itemstack FROM " + Tables.LOGS));
        }

        @Test
        @DisplayName("au-dela de 100 transactions, une ligne de progression intermediaire est emise")
        void manyTransactions_progressEveryHundred() throws Exception {
            List<V3Transaction> transactions = new ArrayList<>();
            for (int index = 0; index < 101; index++) transactions.add(v3Transaction(index, ALICE, BOB, 1L, index % 2 == 0));

            V3MigrationResult result = service.migrate(readerOf(List.of(), transactions)).join();

            assertEquals(101, result.getTransactionsImported());
            assertEquals(101, sentinels());
            assertEquals(101, count(Tables.LOGS));
            assertEquals(51, count(Tables.TRANSACTIONS));
            assertTrue(progress.contains("Migrated 100/101 transactions..."));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Source JSON
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("source JSON")
    class JsonSource {

        @Test
        @DisplayName("migrateFromJson importe items et transactions depuis les fichiers V3")
        void migrateFromJson_imports() throws Exception {
            File folder = new File(dataFolder, "v3json");
            assertTrue(folder.mkdirs());
            Files.writeString(new File(folder, "items.json").toPath(), """
                    [{"id":"%s","itemstack":"json-stack","price":77,"seller":"%s","sellerName":"Alice","economy":"VAULT","expireAt":%d}]
                    """.formatted(UUID.randomUUID(), ALICE, EXPIRE_AT));
            Files.writeString(new File(folder, "expired_items.json").toPath(), """
                    [{"id":"%s","itemstack":"a;b","auctionType":"INVENTORY","seller":"%s","sellerName":"Alice"}]
                    """.formatted(UUID.randomUUID(), ALICE));
            Files.writeString(new File(folder, "transactions.json").toPath(), """
                    [{"id":1,"seller":"%s","buyer":"%s","itemstack":"tx","transactionDate":%d,"price":9,"needMoney":true}]
                    """.formatted(ALICE, BOB, TX_DATE));

            V3MigrationResult result = service.migrateFromJson(folder).join();

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(2, result.getPlayersImported());
            assertEquals(2, result.getItemsImported());
            assertEquals(1, result.getTransactionsImported());
            assertEquals(0, result.getErrors());
            assertEquals(2, nonDeletedItems());
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", StorageType.LISTED.name()));
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", StorageType.EXPIRED.name()));
            assertEquals(3, count(Tables.AUCTION_ITEMS));
            assertEquals(1, count(Tables.TRANSACTIONS));
            assertEquals(1, count(Tables.LOGS, "additional_data = ?", LogRepository.MIGRATED_FROM_V3));
        }

        @Test
        @DisplayName("un dossier sans items.json est une source injoignable")
        void missingItemsFile_connectionFailure() {
            File folder = new File(dataFolder, "empty-json");
            assertTrue(folder.mkdirs());

            V3MigrationResult result = service.migrateFromJson(folder).join();

            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to V3 data source", result.getErrorMessage());
        }

        @Test
        @DisplayName("un items.json vide sans transactions est un echec 'No data found'")
        void emptyItemsFile_noData() throws Exception {
            File folder = new File(dataFolder, "blank-json");
            assertTrue(folder.mkdirs());
            Files.writeString(new File(folder, "items.json").toPath(), "[]");

            V3MigrationResult result = service.migrateFromJson(folder).join();

            assertFalse(result.isSuccess());
            assertEquals("No data found to migrate", result.getErrorMessage());
        }

        @Test
        @DisplayName("un items.json malforme fait echouer la migration avec un message explicite")
        void malformedItemsFile_fails() throws Exception {
            File folder = new File(dataFolder, "broken-json");
            assertTrue(folder.mkdirs());
            Files.writeString(new File(folder, "items.json").toPath(), "[{\"id\": ");

            V3MigrationResult result = service.migrateFromJson(folder).join();

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().startsWith("Migration failed: "), result.getErrorMessage());
        }
    }
}
