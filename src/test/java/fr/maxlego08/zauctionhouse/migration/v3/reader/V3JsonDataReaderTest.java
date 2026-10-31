package fr.maxlego08.zauctionhouse.migration.v3.reader;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.migration.v3.V3StorageType;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lecteur JSON V3 teste contre des fichiers ecrits dans le dossier temporaire, au format que le
 * lecteur attend : {@code items.json}, {@code buying_items.json}, {@code expired_items.json},
 * {@code transactions.json} (tableaux), ou {@code data.json} (objet a trois tableaux).
 */
class V3JsonDataReaderTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";
    private static final String ITEM_1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String ITEM_2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String ITEM_3 = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;
    private File folder;
    private V3JsonDataReader reader;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.tempDir);
        this.folder = new File(this.tempDir, "v3json");
        assertTrue(this.folder.mkdirs());
        this.reader = new V3JsonDataReader(this.plugin, this.folder);
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(new File(this.folder, fileName).toPath(), content);
    }

    private static String fullItem(String id) {
        return """
                {"id":"%s","itemstack":"stack-1","price":1500,"seller":"%s","buyer":"%s","economy":"PLAYER_POINTS",
                 "auctionType":"INVENTORY","expireAt":1800000000000,"sellerName":"Alice","serverName":"lobby","priority":3}
                """.formatted(id, ALICE, BOB);
    }

    @Nested
    @DisplayName("testConnection")
    class TestConnection {

        @Test
        @DisplayName("rend true quand items.json existe")
        void itemsFilePresent_true() throws Exception {
            write("items.json", "[]");

            assertTrue(reader.testConnection().join());
        }

        @Test
        @DisplayName("rend false quand items.json manque")
        void itemsFileMissing_false() {
            assertFalse(reader.testConnection().join());
        }

        @Test
        @DisplayName("rend false quand le dossier lui-meme n'existe pas")
        void folderMissing_false() {
            V3JsonDataReader missing = new V3JsonDataReader(plugin, new File(tempDir, "nope"));

            assertFalse(missing.testConnection().join());
        }

        @Test
        @Disabled("BUG: V3JsonDataReader.testConnection n'accepte que items.json alors que readItems sait lire le format data.json ; une installation V3 au format data.json seul est declaree injoignable par V3MigrationService et ne peut jamais etre migree")
        @DisplayName("rend true quand seul data.json (format alternatif supporte par readItems) existe")
        void dataJsonOnly_true() throws Exception {
            write("data.json", "{\"items\":[" + fullItem(ITEM_1) + "]}");

            assertTrue(reader.testConnection().join());
        }
    }

    @Nested
    @DisplayName("readItems : fichiers separes")
    class ReadItemsSplitFiles {

        @Test
        @DisplayName("mappe chaque champ d'un item au format canonique")
        void mapsEveryCanonicalField() throws Exception {
            write("items.json", "[" + fullItem(ITEM_1) + "]");

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(1, items.size());
            V3AuctionItem item = items.getFirst();
            assertEquals(UUID.fromString(ITEM_1), item.getId());
            assertEquals("stack-1", item.getItemstack());
            assertEquals(1500L, item.getPrice());
            assertEquals(UUID.fromString(ALICE), item.getSeller());
            assertEquals(UUID.fromString(BOB), item.getBuyer());
            assertEquals("PLAYER_POINTS", item.getEconomy());
            assertEquals("INVENTORY", item.getAuctionType());
            assertEquals(1_800_000_000_000L, item.getExpireAt());
            assertEquals(V3StorageType.STORAGE, item.getStorageType());
            assertEquals("Alice", item.getSellerName());
            assertEquals("lobby", item.getServerName());
            assertEquals(3, item.getPriority());
        }

        @Test
        @DisplayName("accepte les cles alternatives (uniqueId, itemStack, sellerUniqueId, buyerUniqueId, auction_type, expire_at, server_name)")
        void acceptsAlternativeKeys() throws Exception {
            write("items.json", """
                    [{"uniqueId":"%s","itemStack":"alt-stack","price":"42","sellerUniqueId":"%s","buyerUniqueId":"%s",
                      "auction_type":"inventory","expire_at":1700000000000,"server_name":"survival"}]
                    """.formatted(ITEM_2, ALICE, BOB));

            V3AuctionItem item = reader.readItems().join().getFirst();

            assertEquals(UUID.fromString(ITEM_2), item.getId());
            assertEquals("alt-stack", item.getItemstack());
            assertEquals(42L, item.getPrice());
            assertEquals(UUID.fromString(ALICE), item.getSeller());
            assertEquals(UUID.fromString(BOB), item.getBuyer());
            assertEquals("inventory", item.getAuctionType());
            assertTrue(item.isInventoryType());
            assertEquals(1_700_000_000_000L, item.getExpireAt());
            assertEquals("survival", item.getServerName());
        }

        @Test
        @DisplayName("les champs absents prennent leurs valeurs par defaut (VAULT, DEFAULT, 0, null)")
        void missingFields_defaults() throws Exception {
            write("items.json", "[{\"id\":\"" + ITEM_1 + "\"}]");

            V3AuctionItem item = reader.readItems().join().getFirst();

            assertNull(item.getItemstack());
            assertEquals(0L, item.getPrice());
            assertNull(item.getSeller());
            assertNull(item.getBuyer());
            assertEquals("VAULT", item.getEconomy());
            assertEquals("DEFAULT", item.getAuctionType());
            assertFalse(item.isInventoryType());
            assertEquals(0L, item.getExpireAt());
            assertEquals(V3StorageType.STORAGE, item.getStorageType());
            assertNull(item.getSellerName());
            assertNull(item.getServerName());
            assertEquals(0, item.getPriority());
        }

        @Test
        @DisplayName("le fichier impose le type de stockage par defaut : items=STORAGE, buying=BUY, expired=EXPIRE")
        void fileName_setsDefaultStorageType() throws Exception {
            write("items.json", "[{\"id\":\"" + ITEM_1 + "\"}]");
            write("buying_items.json", "[{\"id\":\"" + ITEM_2 + "\"}]");
            write("expired_items.json", "[{\"id\":\"" + ITEM_3 + "\"}]");

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(3, items.size());
            assertEquals(V3StorageType.STORAGE, byId(items, ITEM_1).getStorageType());
            assertEquals(V3StorageType.BUY, byId(items, ITEM_2).getStorageType());
            assertEquals(V3StorageType.EXPIRE, byId(items, ITEM_3).getStorageType());
        }

        @Test
        @DisplayName("un storageType/storage_type explicite dans l'item l'emporte sur le fichier")
        void explicitStorageType_overridesFile() throws Exception {
            write("expired_items.json", "[{\"id\":\"" + ITEM_1 + "\",\"storageType\":\"BUY\"},{\"id\":\"" + ITEM_2 + "\",\"storage_type\":\"storage\"}]");

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(V3StorageType.BUY, byId(items, ITEM_1).getStorageType());
            assertEquals(V3StorageType.STORAGE, byId(items, ITEM_2).getStorageType());
        }

        @Test
        @DisplayName("les champs JSON null sont traites comme absents")
        void jsonNull_treatedAsAbsent() throws Exception {
            write("items.json", "[{\"id\":\"" + ITEM_1 + "\",\"buyer\":null,\"economy\":null,\"price\":null,\"sellerName\":null}]");

            V3AuctionItem item = reader.readItems().join().getFirst();

            assertNull(item.getBuyer());
            assertEquals("VAULT", item.getEconomy());
            assertEquals(0L, item.getPrice());
            assertNull(item.getSellerName());
        }

        @Test
        @DisplayName("un item sans id (ni uniqueId) ou avec un id invalide est ignore")
        void missingOrInvalidId_skipped() throws Exception {
            write("items.json", "[{\"price\":10},{\"id\":\"not-a-uuid\"},{\"id\":\"\"},{\"id\":\"null\"}," + fullItem(ITEM_1) + "]");

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(1, items.size());
            assertEquals(UUID.fromString(ITEM_1), items.getFirst().getId());
        }

        @Test
        @DisplayName("un identifiant de joueur invalide donne null, l'item est conserve")
        void invalidPlayerUuid_null() throws Exception {
            write("items.json", "[{\"id\":\"" + ITEM_1 + "\",\"seller\":\"broken\",\"buyer\":\"null\"}]");

            V3AuctionItem item = reader.readItems().join().getFirst();

            assertNull(item.getSeller());
            assertNull(item.getBuyer());
        }

        @Test
        @DisplayName("un champ numerique corrompu fait ignorer l'item, avec un avertissement, sans bloquer les autres")
        void corruptedNumber_skippedAndLogged() throws Exception {
            write("items.json", "[{\"id\":\"" + ITEM_1 + "\",\"price\":\"abc\"}," + fullItem(ITEM_2) + "]");

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                List<V3AuctionItem> items = reader.readItems().join();

                assertEquals(1, items.size());
                assertEquals(UUID.fromString(ITEM_2), items.getFirst().getId());
                assertTrue(logs.contains(Level.WARNING, "Failed to parse V3 item from JSON"));
            }
        }

        @Test
        @DisplayName("un fichier qui contient un objet au lieu d'un tableau est ignore")
        void objectInsteadOfArray_ignored() throws Exception {
            write("items.json", "{\"id\":\"" + ITEM_1 + "\"}");

            assertTrue(reader.readItems().join().isEmpty());
        }

        @Test
        @DisplayName("aucun fichier : liste vide, aucune exception")
        void noFiles_empty() {
            assertTrue(reader.readItems().join().isEmpty());
            assertEquals(0, reader.getItemCount().join().intValue());
        }

        @Test
        @Disabled("BUG: V3JsonDataReader.readItemsFromFile appelle itemElement.getAsJsonObject() hors du try/catch de parseItemFromJson ; un element non-objet dans le tableau (chaine, nombre) leve IllegalStateException et fait echouer TOUTE la lecture au lieu d'ignorer la ligne corrompue")
        @DisplayName("un element du tableau qui n'est pas un objet est ignore, les autres items sont lus")
        void nonObjectElement_skipped() throws Exception {
            write("items.json", "[" + fullItem(ITEM_1) + ", \"garbage\", 42]");

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(1, items.size());
        }

        @Test
        @Disabled("BUG: V3JsonDataReader.readItemsFromFile ne rattrape que IOException ; un fichier JSON malforme leve JsonSyntaxException (RuntimeException) qui remonte au lieu d'etre journalisee, et la migration entiere echoue sur 'Migration failed: ...' sans designer le fichier fautif")
        @DisplayName("un fichier JSON malforme est journalise et donne une liste vide")
        void malformedFile_emptyAndLogged() throws Exception {
            write("items.json", "[{\"id\": ");

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                assertTrue(reader.readItems().join().isEmpty());
                assertTrue(logs.contains(Level.WARNING, "items.json"));
            }
        }
    }

    @Nested
    @DisplayName("readItems : fichier unique data.json")
    class ReadItemsDataJson {

        private String dataJson() {
            return "{\"items\":[{\"id\":\"" + ITEM_1 + "\"}],\"buyingItems\":[{\"id\":\"" + ITEM_2 + "\"}],\"expiredItems\":[{\"id\":\"" + ITEM_3 + "\"}]}";
        }

        @Test
        @DisplayName("sans fichiers separes, data.json est lu et chaque tableau impose son type")
        void fallback_readsThreeArrays() throws Exception {
            write("data.json", dataJson());

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(3, items.size());
            assertEquals(V3StorageType.STORAGE, byId(items, ITEM_1).getStorageType());
            assertEquals(V3StorageType.BUY, byId(items, ITEM_2).getStorageType());
            assertEquals(V3StorageType.EXPIRE, byId(items, ITEM_3).getStorageType());
            assertEquals(3, reader.getItemCount().join().intValue());
        }

        @Test
        @DisplayName("data.json est aussi lu quand items.json existe mais est vide")
        void fallback_whenSplitFilesEmpty() throws Exception {
            write("items.json", "[]");
            write("data.json", dataJson());

            assertEquals(3, reader.readItems().join().size());
        }

        @Test
        @DisplayName("data.json est IGNORE des qu'un fichier separe contient au moins un item")
        void noFallback_whenSplitFilesHaveContent() throws Exception {
            write("buying_items.json", "[" + fullItem(ITEM_1) + "]");
            write("data.json", dataJson());

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(1, items.size());
            assertEquals(UUID.fromString(ITEM_1), items.getFirst().getId());
        }

        @Test
        @DisplayName("un data.json partiel (un seul tableau) est accepte")
        void partialDataJson() throws Exception {
            write("data.json", "{\"expiredItems\":[{\"id\":\"" + ITEM_3 + "\"}]}");

            List<V3AuctionItem> items = reader.readItems().join();

            assertEquals(1, items.size());
            assertEquals(V3StorageType.EXPIRE, items.getFirst().getStorageType());
        }

        @Test
        @DisplayName("un data.json qui est un tableau (et non un objet) est ignore")
        void dataJsonArray_ignored() throws Exception {
            write("data.json", "[{\"id\":\"" + ITEM_1 + "\"}]");

            assertTrue(reader.readItems().join().isEmpty());
        }
    }

    @Nested
    @DisplayName("readTransactions")
    class ReadTransactions {

        @Test
        @DisplayName("mappe chaque champ d'une transaction au format canonique")
        void mapsEveryCanonicalField() throws Exception {
            write("transactions.json", """
                    [{"id":7,"seller":"%s","buyer":"%s","itemstack":"tx-stack","transactionDate":1600000000000,
                      "price":99,"economy":"VAULT","isRead":true,"needMoney":true}]
                    """.formatted(ALICE, BOB));

            List<V3Transaction> transactions = reader.readTransactions().join();

            assertEquals(1, transactions.size());
            V3Transaction transaction = transactions.getFirst();
            assertEquals(7, transaction.getId());
            assertEquals(UUID.fromString(ALICE), transaction.getSeller());
            assertEquals(UUID.fromString(BOB), transaction.getBuyer());
            assertEquals("tx-stack", transaction.getItemstack());
            assertEquals(1_600_000_000_000L, transaction.getTransactionDate());
            assertEquals(99L, transaction.getPrice());
            assertEquals("VAULT", transaction.getEconomy());
            assertTrue(transaction.isRead());
            assertTrue(transaction.isNeedMoney());
        }

        @Test
        @DisplayName("accepte les cles alternatives (sellerUniqueId, buyerUniqueId, itemStack, transaction_date, is_read, need_money)")
        void acceptsAlternativeKeys() throws Exception {
            write("transactions.json", """
                    [{"sellerUniqueId":"%s","buyerUniqueId":"%s","itemStack":"alt","transaction_date":1500000000000,
                      "price":5,"is_read":true,"need_money":true}]
                    """.formatted(ALICE, BOB));

            V3Transaction transaction = reader.readTransactions().join().getFirst();

            assertEquals(UUID.fromString(ALICE), transaction.getSeller());
            assertEquals(UUID.fromString(BOB), transaction.getBuyer());
            assertEquals("alt", transaction.getItemstack());
            assertEquals(1_500_000_000_000L, transaction.getTransactionDate());
            assertEquals("VAULT", transaction.getEconomy());
            assertTrue(transaction.isRead());
            assertTrue(transaction.isNeedMoney());
        }

        @Test
        @DisplayName("la cle \"date\" est le dernier recours pour la date, les booleens absents valent false")
        void dateFallbackAndDefaults() throws Exception {
            write("transactions.json", "[{\"seller\":\"" + ALICE + "\",\"buyer\":\"" + BOB + "\",\"date\":1400000000000}]");

            V3Transaction transaction = reader.readTransactions().join().getFirst();

            assertEquals(0, transaction.getId());
            assertEquals(1_400_000_000_000L, transaction.getTransactionDate());
            assertEquals(0L, transaction.getPrice());
            assertNull(transaction.getItemstack());
            assertFalse(transaction.isRead());
            assertFalse(transaction.isNeedMoney());
        }

        @Test
        @DisplayName("un vendeur ou un acheteur invalide donne null, la transaction est conservee (le service filtrera)")
        void invalidParties_null() throws Exception {
            write("transactions.json", "[{\"seller\":\"broken\",\"buyer\":\"" + BOB + "\"}]");

            V3Transaction transaction = reader.readTransactions().join().getFirst();

            assertNull(transaction.getSeller());
            assertEquals(UUID.fromString(BOB), transaction.getBuyer());
        }

        @Test
        @DisplayName("une transaction au champ numerique corrompu est ignoree avec un avertissement")
        void corruptedNumber_skippedAndLogged() throws Exception {
            write("transactions.json", "[{\"seller\":\"" + ALICE + "\",\"buyer\":\"" + BOB + "\",\"price\":\"lots\"},{\"seller\":\"" + ALICE + "\",\"buyer\":\"" + BOB + "\",\"price\":1}]");

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                List<V3Transaction> transactions = reader.readTransactions().join();

                assertEquals(1, transactions.size());
                assertEquals(1L, transactions.getFirst().getPrice());
                assertTrue(logs.contains(Level.WARNING, "Failed to parse V3 transaction from JSON"));
            }
        }

        @Test
        @DisplayName("fichier absent : liste vide et compteur a 0")
        void missingFile_empty() {
            assertTrue(reader.readTransactions().join().isEmpty());
            assertEquals(0, reader.getTransactionCount().join().intValue());
        }

        @Test
        @DisplayName("getTransactionCount compte les transactions lues")
        void count_matchesRead() throws Exception {
            write("transactions.json", "[{\"seller\":\"" + ALICE + "\",\"buyer\":\"" + BOB + "\"},{\"seller\":\"" + ALICE + "\",\"buyer\":\"" + BOB + "\"}]");

            assertEquals(2, reader.getTransactionCount().join().intValue());
        }
    }

    @Test
    @DisplayName("close() est sans effet et rejouable")
    void close_noop() {
        reader.close();
        reader.close();
    }

    private static V3AuctionItem byId(List<V3AuctionItem> items, String id) {
        return items.stream().filter(item -> item.getId().equals(UUID.fromString(id))).findFirst().orElseThrow();
    }
}
