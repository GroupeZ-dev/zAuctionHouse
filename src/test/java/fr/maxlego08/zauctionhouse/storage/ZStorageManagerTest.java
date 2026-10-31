package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager.ItemLookupResult;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager.LookupState;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.OptionRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.TransactionRepository;
import fr.maxlego08.zauctionhouse.testutil.Base64Stubs;
import fr.maxlego08.zauctionhouse.testutil.ListingFixtures;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * {@link ZStorageManager} de bout en bout sur une base SQLite reelle : demarrage, cycle de vie
 * d'une annonce (reservation, engagement, publication, annulation), relectures, journalisation.
 * <p>
 * L'executor du plugin est synchrone : chaque future est deja complete au retour.
 */
class ZStorageManagerTest extends StorageManagerTestBase {

    private MockedStatic<Base64ItemStack> base64;
    private CategoryManager categoryManager;
    private UUID seller;
    private Player sellerPlayer;

    @BeforeEach
    void setUpManagerTest() {
        this.base64 = Base64Stubs.open();
        this.categoryManager = mock(CategoryManager.class);
        lenient().when(this.plugin.getCategoryManager()).thenReturn(this.categoryManager);
        this.seller = newPlayer("Seller");
        this.sellerPlayer = MockPlugins.player(this.seller, "Seller");
    }

    @AfterEach
    void tearDownManagerTest() {
        this.base64.close();
    }

    private int listed(String... payloads) {
        return ListingFixtures.listed(items(), auctionItems(), this.economy, this.seller, payloads);
    }

    private int listing(StorageType storageType, String... payloads) {
        return ListingFixtures.listing(items(), auctionItems(), this.economy, this.seller, storageType, payloads);
    }

    private String storageTypeOf(int itemId) throws Exception {
        return queryString("SELECT storage_type FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private long pendingOf(int itemId) throws Exception {
        return queryLong("SELECT pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private ZAuctionItem memoryItem(int itemId, long expiredAt) {
        return TestItems.auctionItem(this.plugin, itemId, this.seller, this.economy, expiredAt);
    }

    // ------------------------------------------------------------------------------------------
    // onEnable / onDisable
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("onEnable cree les tables, enregistre les sept repositories et ouvre la connexion")
    void onEnable_createsTablesAndRegistersRepositories() throws Exception {
        assertNotNull(this.storageManager.getDatabaseConnection());
        assertTrue(this.storageManager.getDatabaseConnection().isValid());

        for (String tableName : List.of(Tables.PLAYERS, Tables.ITEMS, Tables.AUCTION_ITEMS, Tables.TRANSACTIONS, Tables.LOGS, Tables.OPTIONS, Tables.MIGRATION_STATE)) {
            assertTrue(tableExists(tableName), "table manquante : " + tableName);
        }
        assertTrue(columnsOf(Tables.ITEMS).contains("pending_publish"), "la colonne pending_publish doit exister avant tout INSERT");
        assertEquals(11, count(MIGRATION_TABLE));

        assertNotNull(this.storageManager.with(PlayerRepository.class));
        assertNotNull(this.storageManager.with(ItemRepository.class));
        assertNotNull(this.storageManager.with(AuctionItemRepository.class));
        assertNotNull(this.storageManager.with(LogRepository.class));
        assertNotNull(this.storageManager.with(TransactionRepository.class));
        assertNotNull(this.storageManager.with(OptionRepository.class));
        assertNotNull(this.storageManager.with(MigrationStateRepository.class));
        assertSame(this.storageManager.with(ItemRepository.class), this.storageManager.with(ItemRepository.class));
    }

    @Test
    @DisplayName("connexion invalide (dossier de donnees impossible a creer) => false et le plugin est desactive")
    void onEnable_invalidConnection_returnsFalseAndDisablesPlugin() throws Exception {
        // Le "dossier" de donnees est un fichier ordinaire : SqliteConnection ne peut ni creer le
        // repertoire ni ouvrir la base, isValid() rend false.
        File notAFolder = new File(this.dataFolder, "not-a-folder.txt");
        Files.writeString(notAFolder.toPath(), "x");
        AuctionPlugin brokenPlugin = MockPlugins.plugin(notAFolder);
        PluginManager pluginManager = mock(PluginManager.class);

        boolean enabled;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); LogCapture logs = LogCapture.of(brokenPlugin.getLogger())) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            enabled = new ZStorageManager(brokenPlugin).onEnable();

            assertTrue(logs.contains(Level.SEVERE, "Unable to connect to database"));
        }

        assertFalse(enabled);
        verify(pluginManager).disablePlugin(brokenPlugin);
    }

    @Test
    @DisplayName("onDisable puis redemarrage : les donnees survivent, les migrations ne sont pas rejouees")
    void onDisable_thenRestart_keepsData() throws Exception {
        int itemId = listed("payload-1");

        restartStorageManager();

        assertEquals(1, count(Tables.ITEMS, "id = ?", itemId));
        assertEquals(11, count(MIGRATION_TABLE));
        assertTrue(this.storageManager.with(ItemRepository.class).select(itemId).isPresent());
    }

    // ------------------------------------------------------------------------------------------
    // Joueurs
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("upsertPlayer(Player) ecrit la ligne et getPlayerName la relit")
    void upsertPlayer_fromPlayer_writesRow() throws Exception {
        Player alice = MockPlugins.player("Alice");

        this.storageManager.upsertPlayer(alice);

        assertEquals("Alice", queryString("SELECT name FROM " + Tables.PLAYERS + " WHERE unique_id = ?", alice.getUniqueId().toString()));
        assertEquals("Alice", this.storageManager.getPlayerName(alice.getUniqueId()));
    }

    @Test
    @DisplayName("upsertPlayer(uuid, nom) insere puis renomme sans dupliquer")
    void upsertPlayer_fromUuid_insertsThenRenames() throws Exception {
        UUID bob = UUID.randomUUID();

        this.storageManager.upsertPlayer(bob, "Bob");
        this.storageManager.upsertPlayer(bob, "Bob_renamed");

        assertEquals(1, count(Tables.PLAYERS, "unique_id = ?", bob.toString()));
        assertEquals("Bob_renamed", this.storageManager.getPlayerName(bob));
    }

    @Test
    @DisplayName("getPlayerName inconnu => null")
    void getPlayerName_unknown_returnsNull() {
        assertNull(this.storageManager.getPlayerName(UUID.randomUUID()));
    }

    @Test
    @DisplayName("findUniqueId : connu => UUID, inconnu => null")
    void findUniqueId_knownAndUnknown() {
        assertEquals(this.seller, this.storageManager.findUniqueId("Seller").join());
        assertNull(this.storageManager.findUniqueId("nobody").join());
    }

    @Test
    @DisplayName("selectPlayers rend la table uuid -> nom des identifiants connus, ignore les inconnus")
    void selectPlayers_mapsKnownUuids() {
        UUID alice = newPlayer("Alice");
        UUID bob = newPlayer("Bob");
        UUID unknown = UUID.randomUUID();

        Map<UUID, String> players = this.storageManager.selectPlayers(List.of(alice.toString(), bob.toString(), unknown.toString()));

        assertEquals(Map.of(alice, "Alice", bob, "Bob"), players);
        assertTrue(this.storageManager.selectPlayers(List.of()).isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // Reservation / engagement / publication / annulation
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("reserveAuctionItem : ligne DELETED pending_publish=1, contenus inseres, invisible de tous")
    void reserveAuctionItem_createsInvisibleReservation() throws Exception {
        long expiredAt = ListingFixtures.inOneHour();
        List<ItemStack> stacks = List.of(TestItems.diamond(1), TestItems.diamond(2));

        AuctionItem reserved = this.storageManager.reserveAuctionItem(this.sellerPlayer, BigDecimal.TEN, expiredAt, stacks, List.of("payload-1", "payload-2"), this.economy).join();

        assertTrue(reserved.getId() > 0);
        assertEquals("Seller", reserved.getSellerName());
        assertEquals(this.seller, reserved.getSellerUniqueId());
        assertEquals(2, reserved.getItemStacks().size());
        assertEquals(expiredAt, reserved.getExpiredAt().getTime());

        assertEquals("DELETED", storageTypeOf(reserved.getId()));
        assertEquals(ItemRepository.PENDING_RESERVED, pendingOf(reserved.getId()));
        assertEquals(MockPlugins.SERVER_NAME, queryString("SELECT server_name FROM " + Tables.ITEMS + " WHERE id = ?", reserved.getId()));
        assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE id = ?", reserved.getId()));
        assertEquals(2, count(Tables.AUCTION_ITEMS, "item_id = ?", reserved.getId()));
        assertEquals(List.of("payload-1", "payload-2"), queryRows("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " WHERE item_id = ? ORDER BY id", reserved.getId()).stream().map(row -> (String) row[0]).toList());

        // invisible : ni par id, ni dans le chargement de masse, ni pour selectItemState
        assertTrue(items().select(reserved.getId()).isEmpty());
        assertTrue(items().select().isEmpty());
        assertEquals(LookupState.GONE, this.storageManager.selectItemState(reserved.getId()).join().state());
    }

    @Test
    @DisplayName("reserveAuctionItem : charges utiles incoherentes => le future echoue, aucun contenu ecrit, rien de visible")
    void reserveAuctionItem_payloadMismatch_failsWithoutPublishing() throws Exception {
        CompletableFuture<AuctionItem> future = this.storageManager.reserveAuctionItem(this.sellerPlayer, BigDecimal.TEN, ListingFixtures.inOneHour(), List.of(TestItems.diamond(1), TestItems.diamond(2)), List.of("payload-1"), this.economy);

        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals(0, count(Tables.AUCTION_ITEMS));
        assertTrue(items().select().isEmpty(), "aucune annonce ne doit etre visible");
    }

    @Test
    @DisplayName("publishAuctionItem sur une reservation NON engagee => 0, la ligne reste DELETED pending 1")
    void publishAuctionItem_uncommittedReservation_doesNothing() throws Exception {
        AuctionItem reserved = this.storageManager.reserveAuctionItem(this.sellerPlayer, BigDecimal.TEN, ListingFixtures.inOneHour(), List.of(TestItems.diamond(1)), List.of("payload-1"), this.economy).join();

        assertEquals(0, this.storageManager.publishAuctionItem(reserved).join());

        assertEquals("DELETED", storageTypeOf(reserved.getId()));
        assertEquals(ItemRepository.PENDING_RESERVED, pendingOf(reserved.getId()));
        assertTrue(items().select(reserved.getId()).isEmpty());
    }

    @Test
    @DisplayName("markReservationCommitted puis publishAuctionItem => LISTED pending 0, visible ; republier => 0")
    void publishAuctionItem_committedReservation_publishesOnce() throws Exception {
        AuctionItem reserved = this.storageManager.reserveAuctionItem(this.sellerPlayer, BigDecimal.TEN, ListingFixtures.inOneHour(), List.of(TestItems.diamond(1)), List.of("payload-1"), this.economy).join();

        assertEquals(1, items().markReservationCommitted(reserved.getId()));
        assertEquals(ItemRepository.PENDING_COMMITTED, pendingOf(reserved.getId()));
        assertEquals("DELETED", storageTypeOf(reserved.getId()), "engagee mais pas encore publiee");

        assertEquals(1, this.storageManager.publishAuctionItem(reserved).join());

        assertEquals("LISTED", storageTypeOf(reserved.getId()));
        assertEquals(0, pendingOf(reserved.getId()));
        assertTrue(items().select(reserved.getId()).isPresent());
        assertEquals(1, items().select().size());

        ItemLookupResult lookup = this.storageManager.selectItemState(reserved.getId()).join();
        assertEquals(LookupState.FOUND, lookup.state());
        assertEquals(ItemStatus.AVAILABLE, lookup.item().getStatus());

        assertEquals(0, this.storageManager.publishAuctionItem(reserved).join(), "une annonce deja publiee ne se republie pas");
    }

    @Test
    @DisplayName("publishAuctionItem sur un identifiant inconnu => 0")
    void publishAuctionItem_unknownId_returnsZero() {
        assertEquals(0, this.storageManager.publishAuctionItem(memoryItem(99_999, ListingFixtures.inOneHour())).join());
    }

    @Test
    @DisplayName("cancelReservation sur une reservation NON engagee supprime la ligne ET ses contenus")
    void cancelReservation_uncommitted_deletesRowAndContents() throws Exception {
        AuctionItem reserved = this.storageManager.reserveAuctionItem(this.sellerPlayer, BigDecimal.TEN, ListingFixtures.inOneHour(), List.of(TestItems.diamond(1), TestItems.diamond(2)), List.of("payload-1", "payload-2"), this.economy).join();
        int other = listed("payload-3"); // une autre annonce ne doit pas etre touchee

        this.storageManager.cancelReservation(reserved).join();

        assertEquals(0, count(Tables.ITEMS, "id = ?", reserved.getId()));
        assertEquals(0, count(Tables.AUCTION_ITEMS, "item_id = ?", reserved.getId()));
        assertEquals(1, count(Tables.ITEMS, "id = ?", other));
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", other));
    }

    @Test
    @DisplayName("cancelReservation sur une reservation ENGAGEE ne supprime rien (le vendeur a perdu son lot) et avertit")
    void cancelReservation_committed_keepsRowAndWarns() throws Exception {
        AuctionItem reserved = this.storageManager.reserveAuctionItem(this.sellerPlayer, BigDecimal.TEN, ListingFixtures.inOneHour(), List.of(TestItems.diamond(1)), List.of("payload-1"), this.economy).join();
        items().markReservationCommitted(reserved.getId());

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            this.storageManager.cancelReservation(reserved).join();
            assertTrue(logs.contains(Level.WARNING, "Reservation " + reserved.getId() + " was already gone (or no longer cancellable)"));
        }

        assertEquals(1, count(Tables.ITEMS, "id = ?", reserved.getId()));
        assertEquals(ItemRepository.PENDING_COMMITTED, pendingOf(reserved.getId()));
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", reserved.getId()));
    }

    @Test
    @DisplayName("cancelReservation sur une annonce publiee ou inconnue : rien ne bouge, pas d'exception")
    void cancelReservation_publishedOrUnknown_isNoOp() throws Exception {
        int published = listed("payload-1");

        assertDoesNotThrow(() -> this.storageManager.cancelReservation(memoryItem(published, ListingFixtures.inOneHour())).join());
        assertDoesNotThrow(() -> this.storageManager.cancelReservation(memoryItem(99_999, ListingFixtures.inOneHour())).join());

        assertEquals("LISTED", storageTypeOf(published));
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", published));
    }

    // ------------------------------------------------------------------------------------------
    // createAuctionItem (chemin deprecie, encode a l'ecriture)
    // ------------------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("createAuctionItem(Player) encode les contenus et publie directement en LISTED")
    void createAuctionItem_fromPlayer_publishesListed() throws Exception {
        long expiredAt = ListingFixtures.inOneHour();

        AuctionItem created = this.storageManager.createAuctionItem(this.sellerPlayer, new BigDecimal("42.00"), expiredAt, List.of(TestItems.diamond(3), TestItems.diamond(2)), this.economy).join();

        assertTrue(created.getId() > 0);
        assertEquals("Seller", created.getSellerName());
        assertEquals("LISTED", storageTypeOf(created.getId()));
        assertEquals(0, pendingOf(created.getId()));
        assertEquals(List.of("payload-3", "payload-2"), queryRows("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " WHERE item_id = ? ORDER BY id", created.getId()).stream().map(row -> (String) row[0]).toList());

        var dto = items().select(created.getId()).orElseThrow();
        assertEquals(StorageType.LISTED, dto.storage_type());
        assertEquals(ItemType.AUCTION, dto.item_type());
        assertEquals(0, new BigDecimal("42.00").compareTo(dto.price()));
        assertEquals(expiredAt, dto.expired_at().getTime());
        assertEquals(LookupState.FOUND, this.storageManager.selectItemState(created.getId()).join().state());
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("createAuctionItem(uuid, nom) publie en LISTED pour un vendeur hors ligne")
    void createAuctionItem_fromUuid_publishesListed() throws Exception {
        UUID offline = newPlayer("Offline");

        AuctionItem created = this.storageManager.createAuctionItem(offline, "Offline", BigDecimal.ONE, ListingFixtures.inOneHour(), List.of(TestItems.diamond(7)), this.economy).join();

        assertEquals(offline, created.getSellerUniqueId());
        assertEquals("Offline", created.getSellerName());
        assertEquals("LISTED", storageTypeOf(created.getId()));
        assertEquals(offline.toString(), queryString("SELECT seller_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", created.getId()));
        assertEquals("payload-7", queryString("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " WHERE item_id = ?", created.getId()));
    }

    // ------------------------------------------------------------------------------------------
    // updateItem / updateItems
    // ------------------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("updateItem(item, type) deprecie : ecrit le conteneur et expired_at sans garde de transition")
    void updateItem_legacy_writesWithoutGuard() throws Exception {
        int itemId = listed("payload-1");
        UUID buyer = newPlayer("Buyer");
        long newExpiredAt = ListingFixtures.inOneHour() + 60_000L;
        ZAuctionItem item = memoryItem(itemId, newExpiredAt);
        item.setBuyer(buyer, "Buyer");

        this.storageManager.updateItem(item, StorageType.PURCHASED).join();

        assertEquals("PURCHASED", storageTypeOf(itemId));
        assertEquals(buyer.toString(), queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
        assertEquals(newExpiredAt, queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("updateItem(item, EXPIRED) deprecie : la garde historique refuse d'expirer une ligne PURCHASED")
    void updateItem_legacyToExpired_keepsPurchasedRow() throws Exception {
        int itemId = listing(StorageType.PURCHASED, "payload-1");

        this.storageManager.updateItem(memoryItem(itemId, 0L), StorageType.EXPIRED).join();

        assertEquals("PURCHASED", storageTypeOf(itemId));
    }

    @Test
    @DisplayName("updateItem CAS : LISTED -> PURCHASED reussit, la seconde tentative echoue en StaleItemException")
    void updateItem_cas_succeedsOnceThenStale() throws Exception {
        int itemId = listed("payload-1");
        UUID buyer = newPlayer("Buyer");
        ZAuctionItem item = memoryItem(itemId, ListingFixtures.inOneHour());
        item.setBuyer(buyer, "Buyer");

        this.storageManager.updateItem(item, StorageType.LISTED, StorageType.PURCHASED).join();

        assertEquals("PURCHASED", storageTypeOf(itemId));
        assertEquals(buyer.toString(), queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId));

        CompletableFuture<Void> second = this.storageManager.updateItem(item, StorageType.LISTED, StorageType.PURCHASED);
        CompletionException exception = assertThrows(CompletionException.class, second::join);
        StaleItemException stale = StaleItemException.unwrap(exception);
        assertNotNull(stale);
        assertEquals(itemId, stale.getItemId());
        assertEquals(StorageType.LISTED, stale.getExpectedFrom());
        assertEquals(StorageType.PURCHASED, stale.getDestination());
        assertEquals("PURCHASED", storageTypeOf(itemId), "la course perdue ne modifie rien");
    }

    @Test
    @DisplayName("updateItem CAS vers PURCHASED refuse une ligne qui porte deja un acheteur")
    void updateItem_cas_toPurchased_refusesRowWithBuyer() throws Exception {
        int itemId = listed("payload-1");
        UUID firstBuyer = newPlayer("First");
        execute("UPDATE " + Tables.ITEMS + " SET buyer_unique_id = ? WHERE id = ?", firstBuyer.toString(), itemId);
        ZAuctionItem item = memoryItem(itemId, ListingFixtures.inOneHour());
        item.setBuyer(newPlayer("Second"), "Second");

        CompletionException exception = assertThrows(CompletionException.class, () -> this.storageManager.updateItem(item, StorageType.LISTED, StorageType.PURCHASED).join());

        assertNotNull(StaleItemException.unwrap(exception));
        assertEquals(firstBuyer.toString(), queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
        assertEquals("LISTED", storageTypeOf(itemId));
    }

    @Test
    @DisplayName("updateItems(map) legacy : chaque item rejoint son conteneur de destination")
    void updateItems_legacy_movesEveryItem() throws Exception {
        int toExpire = listed("payload-1");
        int toDelete = listed("payload-2");
        Map<StorageType, List<Item>> moves = new EnumMap<>(StorageType.class);
        moves.put(StorageType.EXPIRED, List.of(memoryItem(toExpire, 0L)));
        moves.put(StorageType.DELETED, List.of(memoryItem(toDelete, 0L)));
        moves.put(StorageType.PURCHASED, List.of()); // liste vide ignoree

        this.storageManager.updateItems(moves).join();

        assertEquals("EXPIRED", storageTypeOf(toExpire));
        assertEquals("DELETED", storageTypeOf(toDelete));
    }

    @Test
    @DisplayName("updateItems(map, from) CAS : rend les identifiants qui ont perdu la course")
    void updateItems_cas_returnsStaleIds() throws Exception {
        int first = listed("payload-1");
        int second = listed("payload-2");
        int alreadySold = listed("payload-3");
        execute("UPDATE " + Tables.ITEMS + " SET storage_type = ? WHERE id = ?", "PURCHASED", alreadySold);
        Map<StorageType, List<Item>> moves = new EnumMap<>(StorageType.class);
        moves.put(StorageType.EXPIRED, List.of(memoryItem(first, 0L), memoryItem(second, 0L), memoryItem(alreadySold, 0L)));

        List<Integer> stale = this.storageManager.updateItems(moves, StorageType.LISTED).join();

        assertEquals(List.of(alreadySold), stale);
        assertEquals("EXPIRED", storageTypeOf(first));
        assertEquals("EXPIRED", storageTypeOf(second));
        assertEquals("PURCHASED", storageTypeOf(alreadySold), "la ligne vendue ailleurs n'est pas ressuscitee");
    }

    @Test
    @DisplayName("updateItems(map, from) CAS : tout bouge => liste vide")
    void updateItems_cas_allMoved_returnsEmpty() throws Exception {
        int first = listed("payload-1");
        int second = listed("payload-2");
        Map<StorageType, List<Item>> moves = new EnumMap<>(StorageType.class);
        moves.put(StorageType.EXPIRED, List.of(memoryItem(first, 0L), memoryItem(second, 0L)));

        assertTrue(this.storageManager.updateItems(moves, StorageType.LISTED).join().isEmpty());
        assertEquals(2, count(Tables.ITEMS, "storage_type = ?", "EXPIRED"));
    }

    // ------------------------------------------------------------------------------------------
    // Journal et transactions
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("log ecrit une ligne complete dans logs")
    void log_writesRow() throws Exception {
        int itemId = listed("payload-1");
        UUID buyer = newPlayer("Buyer");
        Player buyerPlayer = MockPlugins.player(buyer, "Buyer");
        Date readedAt = new Date(1_700_000_000_000L);

        this.storageManager.log(LogType.PURCHASE, itemId, buyerPlayer, this.seller, "payload-1", new BigDecimal("12.50"), "vault", "extra", readedAt);

        assertEquals(1, count(Tables.LOGS));
        Object[] row = queryRows("SELECT log_type, item_id, player_unique_id, target_unique_id, itemstack, price, economy_name, additional_data, readed_at FROM " + Tables.LOGS).getFirst();
        assertEquals("PURCHASE", row[0]);
        assertEquals(itemId, ((Number) row[1]).intValue());
        assertEquals(buyer.toString(), row[2]);
        assertEquals(this.seller.toString(), row[3]);
        assertEquals("payload-1", row[4]);
        assertEquals(0, new BigDecimal("12.50").compareTo(new BigDecimal(String.valueOf(row[5]))));
        assertEquals("vault", row[6]);
        assertEquals("extra", row[7]);
        assertNotNull(row[8]);
    }

    @Test
    @DisplayName("log avec prix null et champs optionnels absents => prix 0, colonnes NULL")
    void log_nullOptionalFields_writesDefaults() throws Exception {
        int itemId = listed("payload-1");

        this.storageManager.log(LogType.REMOVE_LISTED, itemId, this.sellerPlayer, null, null, null, null, null, null);

        assertEquals(1, count(Tables.LOGS, "target_unique_id IS NULL AND itemstack IS NULL AND economy_name IS NULL AND additional_data IS NULL AND readed_at IS NULL"));
        assertEquals(0, new BigDecimal(queryString("SELECT price FROM " + Tables.LOGS)).compareTo(BigDecimal.ZERO));
        assertEquals("REMOVE_LISTED", queryString("SELECT log_type FROM " + Tables.LOGS));
    }

    @Test
    @DisplayName("createTransaction ecrit une ligne PENDING avec les montants")
    void createTransaction_writesRow() throws Exception {
        int itemId = listed("payload-1");

        this.storageManager.createTransaction(memoryItem(itemId, 0L), this.seller, "vault", new BigDecimal("100"), new BigDecimal("112.50"), new BigDecimal("12.50"), TransactionStatus.PENDING);

        assertEquals(1, count(Tables.TRANSACTIONS));
        Object[] row = queryRows("SELECT item_id, player_unique_id, economy_name, before, after, value, status, claim_token FROM " + Tables.TRANSACTIONS).getFirst();
        assertEquals(itemId, ((Number) row[0]).intValue());
        assertEquals(this.seller.toString(), row[1]);
        assertEquals("vault", row[2]);
        assertEquals(0, new BigDecimal("100").compareTo(new BigDecimal(String.valueOf(row[3]))));
        assertEquals(0, new BigDecimal("112.50").compareTo(new BigDecimal(String.valueOf(row[4]))));
        assertEquals(0, new BigDecimal("12.50").compareTo(new BigDecimal(String.valueOf(row[5]))));
        assertEquals("PENDING", row[6]);
        assertNull(row[7], "aucune reservation de claim a la creation");

        var pending = transactions().selectByPlayerAndStatus(this.seller, TransactionStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals(0, new BigDecimal("12.50").compareTo(pending.getFirst().value()));
    }

    @Test
    @DisplayName("markPurchaseLogAsRead ne marque que les logs PURCHASE non lus de cet item pour ce vendeur")
    void markPurchaseLogAsRead_marksOnlyMatchingRows() throws Exception {
        int itemId = listed("payload-1");
        int otherItem = listed("payload-2");
        UUID buyer = newPlayer("Buyer");
        UUID otherSeller = newPlayer("Other");
        Date alreadyRead = new Date(1_600_000_000_000L);
        logs().createLog(LogType.PURCHASE, itemId, buyer, this.seller, "payload-1", BigDecimal.TEN, "vault", null, null);      // cible
        logs().createLog(LogType.PURCHASE, itemId, buyer, this.seller, "payload-1", BigDecimal.TEN, "vault", null, alreadyRead); // deja lu
        logs().createLog(LogType.SALE, itemId, this.seller, buyer, "payload-1", BigDecimal.TEN, "vault", null, null);          // autre type
        logs().createLog(LogType.PURCHASE, otherItem, buyer, this.seller, "payload-2", BigDecimal.TEN, "vault", null, null);  // autre item
        logs().createLog(LogType.PURCHASE, itemId, buyer, otherSeller, "payload-1", BigDecimal.TEN, "vault", null, null);     // autre vendeur
        assertEquals(4, count(Tables.LOGS, "readed_at IS NULL"));

        this.storageManager.markPurchaseLogAsRead(itemId, this.seller);

        assertEquals(3, count(Tables.LOGS, "readed_at IS NULL"));
        assertEquals(0, count(Tables.LOGS, "readed_at IS NULL AND log_type = ? AND item_id = ? AND target_unique_id = ?", "PURCHASE", itemId, this.seller.toString()));
        assertEquals(1, count(Tables.LOGS, "readed_at IS NULL AND log_type = ?", "SALE"));
        assertEquals(1, count(Tables.LOGS, "readed_at IS NULL AND item_id = ?", otherItem));
        assertEquals(1, count(Tables.LOGS, "readed_at IS NULL AND target_unique_id = ?", otherSeller.toString()));
        assertTrue(logs().selectUnreadSales(this.seller).stream().noneMatch(log -> log.item_id() == itemId));
    }

    @Test
    @DisplayName("selectSalesHistory : seulement les PURCHASE dont le joueur est la cible et item_id > 0")
    void selectSalesHistory_filtersPurchaseLogsOfSeller() {
        int itemId = listed("payload-1");
        UUID buyer = newPlayer("Buyer");
        logs().createLog(LogType.PURCHASE, itemId, buyer, this.seller, "payload-1", BigDecimal.TEN, "vault", null, null); // retenu
        logs().createLog(LogType.PURCHASE, 0, buyer, this.seller, "payload-1", BigDecimal.TEN, "vault", null, null);      // item_id 0 exclu
        logs().createLog(LogType.SALE, itemId, this.seller, buyer, "payload-1", BigDecimal.TEN, "vault", null, null);     // SALE exclu
        logs().createLog(LogType.PURCHASE, itemId, this.seller, buyer, "payload-1", BigDecimal.TEN, "vault", null, null); // autre cible exclu

        List<LogDTO> history = this.storageManager.selectSalesHistory(this.seller, 0L);

        assertEquals(1, history.size());
        assertEquals(itemId, history.getFirst().item_id());
        assertEquals(LogType.PURCHASE, history.getFirst().log_type());
        assertEquals(this.seller, history.getFirst().target_unique_id());
        assertEquals(buyer, history.getFirst().player_unique_id());

        // Le quatrieme log a l'acheteur pour cible : il forme SON historique de ventes, pas celui du vendeur.
        List<LogDTO> buyerHistory = this.storageManager.selectSalesHistory(buyer, 0L);
        assertEquals(1, buyerHistory.size());
        assertEquals(this.seller, buyerHistory.getFirst().player_unique_id());
        assertTrue(this.storageManager.selectSalesHistory(UUID.randomUUID(), 0L).isEmpty());
    }

    @Test
    @Disabled("BUG: LogRepository.selectSalesHistory compare created_at (TEXT, DEFAULT CURRENT_TIMESTAMP) a une java.util.Date liee en INTEGER ; sous SQLite INTEGER se classe avant TEXT donc 'created_at > ?' est toujours vrai et le filtre d'age est inoperant (LogRepository.java, selectSalesHistory)")
    @DisplayName("selectSalesHistory avec expireAfterMs exclut les ventes plus anciennes que la fenetre")
    void selectSalesHistory_expireAfter_excludesOldSales() throws Exception {
        int itemId = listed("payload-1");
        UUID buyer = newPlayer("Buyer");
        logs().createLog(LogType.PURCHASE, itemId, buyer, this.seller, "payload-1", BigDecimal.TEN, "vault", null, null);
        execute("UPDATE " + Tables.LOGS + " SET created_at = ?", "2020-01-01 00:00:00");

        List<LogDTO> history = this.storageManager.selectSalesHistory(this.seller, 24L * 3_600_000L);

        assertTrue(history.isEmpty(), "une vente de 2020 ne doit pas apparaitre dans une fenetre de 24 h");
    }

    // ------------------------------------------------------------------------------------------
    // selectItem / selectItemState
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("selectItemState : LISTED valide => FOUND, annonce complete, statut AVAILABLE")
    void selectItemState_listed_found() {
        int itemId = listed("payload-1", "payload-2");

        ItemLookupResult result = this.storageManager.selectItemState(itemId).join();

        assertEquals(LookupState.FOUND, result.state());
        assertTrue(result.isFound());
        AuctionItem item = (AuctionItem) result.item();
        assertEquals(itemId, item.getId());
        assertEquals("Seller", item.getSellerName());
        assertEquals(this.seller, item.getSellerUniqueId());
        assertSame(this.economy, item.getAuctionEconomy());
        assertEquals(ItemStatus.AVAILABLE, item.getStatus());
        assertEquals(2, item.getItemStacks().size());
        assertNull(item.getBuyerUniqueId());

        // selectItem conserve le contrat historique : l'annonce elle-meme quand elle est FOUND
        Item viaSelectItem = this.storageManager.selectItem(itemId).join();
        assertNotNull(viaSelectItem);
        assertEquals(itemId, viaSelectItem.getId());
        assertEquals(ItemStatus.AVAILABLE, viaSelectItem.getStatus());
    }

    @Test
    @DisplayName("selectItemState : identifiant inconnu => GONE, item null ; selectItem => null")
    void selectItemState_unknownId_gone() {
        ItemLookupResult result = this.storageManager.selectItemState(424_242).join();

        assertEquals(LookupState.GONE, result.state());
        assertNull(result.item());
        assertFalse(result.isFound());
        assertNull(this.storageManager.selectItem(424_242).join());
    }

    @Test
    @DisplayName("selectItemState : ligne DELETED => GONE (filtree par ItemRepository.select)")
    void selectItemState_deletedRow_gone() {
        int itemId = listing(StorageType.DELETED, "payload-1");

        assertEquals(LookupState.GONE, this.storageManager.selectItemState(itemId).join().state());
        assertNull(this.storageManager.selectItem(itemId).join());
    }

    @Test
    @DisplayName("selectItemState : ligne PURCHASED avec acheteur => FOUND, statut PURCHASED, acheteur resolu")
    void selectItemState_purchasedRow_foundWithBuyer() throws Exception {
        UUID buyer = newPlayer("Buyer");
        int itemId = listing(StorageType.PURCHASED, "payload-1");
        execute("UPDATE " + Tables.ITEMS + " SET buyer_unique_id = ? WHERE id = ?", buyer.toString(), itemId);

        ItemLookupResult result = this.storageManager.selectItemState(itemId).join();

        assertEquals(LookupState.FOUND, result.state());
        assertEquals(ItemStatus.PURCHASED, result.item().getStatus());
        assertEquals(buyer, result.item().getBuyerUniqueId());
        assertEquals("Buyer", result.item().getBuyerName());
    }

    @Test
    @DisplayName("selectItemState : ligne EXPIRED => FOUND, statut REMOVED")
    void selectItemState_expiredRow_foundRemoved() {
        int itemId = listing(StorageType.EXPIRED, "payload-1");

        ItemLookupResult result = this.storageManager.selectItemState(itemId).join();

        assertEquals(LookupState.FOUND, result.state());
        assertEquals(ItemStatus.REMOVED, result.item().getStatus());
    }

    @Test
    @DisplayName("selectItemState : aucune ligne de contenu => UNAVAILABLE (quarantaine), ligne intacte")
    void selectItemState_noContent_unavailable() throws Exception {
        int itemId = listed(); // pas de contenu

        ItemLookupResult result = this.storageManager.selectItemState(itemId).join();

        assertEquals(LookupState.UNAVAILABLE, result.state());
        assertNull(result.item());
        assertFalse(result.isFound());
        assertNull(this.storageManager.selectItem(itemId).join(), "contrat historique : null aussi pour UNAVAILABLE");
        assertEquals(1, count(Tables.ITEMS, "id = ?", itemId));
    }

    @Test
    @DisplayName("selectItemState : charge utile indecodable => UNAVAILABLE, lignes intactes")
    void selectItemState_undecodablePayload_unavailable() throws Exception {
        int itemId = listed("payload-1", Base64Stubs.BROKEN);

        ItemLookupResult result = this.storageManager.selectItemState(itemId).join();

        assertEquals(LookupState.UNAVAILABLE, result.state());
        assertEquals(1, count(Tables.ITEMS, "id = ? AND storage_type = ?", itemId, "LISTED"));
        assertEquals(2, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId));
    }

    @Test
    @DisplayName("selectItemState : economie inconnue => UNAVAILABLE et SEVERE explicite")
    void selectItemState_unknownEconomy_unavailable() throws Exception {
        int itemId = listed("payload-1");
        execute("UPDATE " + Tables.ITEMS + " SET economy_name = ? WHERE id = ?", "dogecoin", itemId);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            assertEquals(LookupState.UNAVAILABLE, this.storageManager.selectItemState(itemId).join().state());
            assertTrue(logs.contains(Level.SEVERE, "Impossible to find the economy dogecoin"));
        }
    }

    @Test
    @DisplayName("selectItemState : type BID non implemente => UNAVAILABLE")
    void selectItemState_bidType_unavailable() {
        int bidId = items().create(this.seller, ItemType.BID, BigDecimal.ONE, ListingFixtures.inOneHour(), this.economy);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            assertEquals(LookupState.UNAVAILABLE, this.storageManager.selectItemState(bidId).join().state());
            assertTrue(logs.contains(Level.SEVERE, "Item type BID is not implemented"));
        }
    }

    // ------------------------------------------------------------------------------------------
    // selectItems
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("selectItems : liste vide => liste vide, identifiants inconnus => liste vide")
    void selectItems_emptyOrUnknown_returnsEmpty() {
        assertTrue(this.storageManager.selectItems(List.of()).isEmpty());
        assertTrue(this.storageManager.selectItems(List.of(424_242, 424_243)).isEmpty());
    }

    @Test
    @DisplayName("selectItems : relit chaque conteneur (DELETED compris), ignore les inconnus et met en quarantaine les lignes cassees")
    void selectItems_mixedIds_rebuildsEveryReadableRow() throws Exception {
        UUID buyer = newPlayer("Buyer");
        int listed = listed("payload-1");
        int purchased = listing(StorageType.PURCHASED, "payload-2");
        execute("UPDATE " + Tables.ITEMS + " SET buyer_unique_id = ? WHERE id = ?", buyer.toString(), purchased);
        int deleted = listing(StorageType.DELETED, "payload-3");
        int broken = listed(Base64Stubs.BROKEN);

        List<Item> items = this.storageManager.selectItems(List.of(listed, purchased, deleted, broken, 424_242));

        assertEquals(List.of(listed, purchased, deleted), items.stream().map(Item::getId).toList());
        assertEquals(ItemStatus.AVAILABLE, items.get(0).getStatus());
        assertEquals(ItemStatus.PURCHASED, items.get(1).getStatus());
        assertEquals("Buyer", items.get(1).getBuyerName());
        assertEquals(ItemStatus.DELETED, items.get(2).getStatus());
        verify(this.categoryManager).applyCategories(items.get(0));
        verify(this.categoryManager).applyCategories(items.get(1));
        verify(this.categoryManager).applyCategories(items.get(2));
    }

    @Test
    @DisplayName("selectItems : 501 identifiants (deux lots IN) => aucune annonce perdue")
    void selectItems_501Ids_crossesChunkBoundaryWithoutLoss() {
        List<Integer> ids = new ArrayList<>();
        for (int index = 0; index < 501; index++) ids.add(listed("payload-1"));

        List<Item> items = this.storageManager.selectItems(ids);

        assertEquals(501, items.size());
        assertEquals(501, items.stream().map(Item::getId).distinct().count());
        assertTrue(items.stream().allMatch(item -> item.getStatus() == ItemStatus.AVAILABLE));
    }
}
