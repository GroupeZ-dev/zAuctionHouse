package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.testutil.Base64Stubs;
import fr.maxlego08.zauctionhouse.testutil.ListingFixtures;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ItemLoaderUtils} : reconstruction des annonces a partir des lignes {@code items} et
 * {@code auction_items}, et REGLES DE QUARANTAINE (C-001 / C-011 / C-038).
 * <p>
 * Les methodes sont protegees : une sous-classe de test les expose. Les lignes sont de vraies
 * lignes SQLite ; seul {@link Base64ItemStack} est double (voir {@link Base64Stubs}).
 */
class ItemLoaderUtilsTest extends StorageManagerTestBase {

    /**
     * Sous-classe minimale qui expose les methodes protegees sans en changer le comportement.
     */
    private static final class Loader extends ItemLoaderUtils {

        Result load(AuctionPlugin plugin, Map<UUID, String> players, List<ItemDTO> items, PerformanceDebug performanceDebug, BiConsumer<StorageType, Item> consumer) {
            return createItems(plugin, players, items, performanceDebug, consumer);
        }

        AuctionItem build(AuctionPlugin plugin, ItemDTO dto, String sellerName, List<AuctionItemDTO> contents, AuctionEconomy economy) {
            return createAuctionItem(plugin, dto, sellerName, contents, economy);
        }

        String name(Map<UUID, String> players, UUID uniqueId) {
            return getPlayerName(players, uniqueId);
        }

        List<String> ids(List<ItemDTO> dtos, ItemType itemType) {
            return getIDS(dtos, itemType);
        }
    }

    /**
     * Collecte ce que {@code createItems} publie, dans l'ordre.
     */
    private static final class Published {
        final List<StorageType> storageTypes = new ArrayList<>();
        final List<Item> items = new ArrayList<>();

        void accept(StorageType storageType, Item item) {
            this.storageTypes.add(storageType);
            this.items.add(item);
        }

        Item single() {
            assertEquals(1, this.items.size(), "exactement une annonce publiee attendue");
            return this.items.getFirst();
        }
    }

    private final Loader loader = new Loader();
    private MockedStatic<Base64ItemStack> base64;
    private CategoryManager categoryManager;
    private PerformanceDebug performanceDebug;
    private UUID seller;
    private Map<UUID, String> players;

    @BeforeEach
    void setUpLoader() {
        this.base64 = Base64Stubs.open();
        this.categoryManager = mock(CategoryManager.class);
        lenient().when(this.plugin.getCategoryManager()).thenReturn(this.categoryManager);
        this.performanceDebug = new PerformanceDebug(this.plugin);
        this.seller = newPlayer("Seller");
        this.players = new HashMap<>();
        this.players.put(this.seller, "Seller");
    }

    @AfterEach
    void tearDownLoader() {
        this.base64.close();
    }

    private int listing(StorageType storageType, String... payloads) {
        return ListingFixtures.listing(items(), auctionItems(), this.economy, this.seller, storageType, payloads);
    }

    /**
     * Relit les lignes parentes SANS filtrer DELETED (contrairement a {@code ItemRepository.select()}).
     */
    private List<ItemDTO> dtos(int... ids) {
        List<String> keys = new ArrayList<>();
        for (int id : ids) keys.add(String.valueOf(id));
        return items().select(keys);
    }

    private ItemLoaderUtils.Result load(Published published, int... ids) {
        return this.loader.load(this.plugin, this.players, dtos(ids), this.performanceDebug, published::accept);
    }

    // ------------------------------------------------------------------------------------------
    // Cas nominal
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("une annonce LISTED valide est publiee avec son StorageType, ses contenus et ses categories")
    void createItems_validListedItem_isPublished() {
        int itemId = listing(StorageType.LISTED, "payload-1", "payload-2");
        Published published = new Published();

        ItemLoaderUtils.Result result = load(published, itemId);

        assertEquals(new ItemLoaderUtils.Result(1, 2, 0, 0, 0), result);
        assertEquals(List.of(StorageType.LISTED), published.storageTypes);
        AuctionItem item = (AuctionItem) published.single();
        assertEquals(itemId, item.getId());
        assertEquals(this.seller, item.getSellerUniqueId());
        assertEquals("Seller", item.getSellerName());
        assertEquals(MockPlugins.SERVER_NAME, item.getServerName());
        assertEquals(0, ListingFixtures.PRICE.compareTo(item.getPrice()));
        assertSame(this.economy, item.getAuctionEconomy());
        assertEquals(ItemStatus.AVAILABLE, item.getStatus());
        assertNull(item.getBuyerUniqueId());
        assertEquals(2, item.getItemStacks().size());
        // l'ordre des contenus est celui des lignes auction_items
        assertEquals(1, item.getItemStacks().get(0).getAmount());
        assertEquals(2, item.getItemStacks().get(1).getAmount());
        verify(this.categoryManager).applyCategories(item);
    }

    @ParameterizedTest(name = "storage_type {0} => statut {1}")
    @CsvSource({"LISTED, AVAILABLE", "PURCHASED, PURCHASED", "EXPIRED, REMOVED", "DELETED, DELETED"})
    @DisplayName("le statut memoire derive du conteneur de la ligne")
    void createItems_mapsStorageTypeToStatus(StorageType storageType, ItemStatus expectedStatus) {
        int itemId = listing(storageType, "payload-1");
        Published published = new Published();

        load(published, itemId);

        assertEquals(List.of(storageType), published.storageTypes);
        assertEquals(expectedStatus, published.single().getStatus());
    }

    @Test
    @DisplayName("plusieurs annonces : chacune recoit SES contenus (regroupement par item_id)")
    void createItems_severalListings_eachGetsItsOwnContents() {
        int first = listing(StorageType.LISTED, "payload-1");
        int second = listing(StorageType.EXPIRED, "payload-2", "payload-3", "payload-4");
        Published published = new Published();

        ItemLoaderUtils.Result result = load(published, first, second);

        assertEquals(new ItemLoaderUtils.Result(2, 4, 0, 0, 0), result);
        assertEquals(2, published.items.size());
        AuctionItem firstItem = (AuctionItem) published.items.get(0);
        AuctionItem secondItem = (AuctionItem) published.items.get(1);
        assertEquals(first, firstItem.getId());
        assertEquals(1, firstItem.getItemStacks().size());
        assertEquals(second, secondItem.getId());
        assertEquals(3, secondItem.getItemStacks().size());
        assertEquals(List.of(StorageType.LISTED, StorageType.EXPIRED), published.storageTypes);
    }

    @Test
    @DisplayName("liste vide => Result(0,0,0,0,0), rien publie, aucune categorie appliquee")
    void createItems_emptyList_publishesNothing() {
        Published published = new Published();

        ItemLoaderUtils.Result result = this.loader.load(this.plugin, this.players, List.of(), this.performanceDebug, published::accept);

        assertEquals(new ItemLoaderUtils.Result(0, 0, 0, 0, 0), result);
        assertTrue(published.items.isEmpty());
        verify(this.categoryManager, never()).applyCategories(any());
    }

    // ------------------------------------------------------------------------------------------
    // Acheteur
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("acheteur connu => setBuyer avec son nom")
    void createItems_knownBuyer_setsBuyerName() throws Exception {
        UUID buyer = newPlayer("Buyer");
        this.players.put(buyer, "Buyer");
        int itemId = listing(StorageType.PURCHASED, "payload-1");
        execute("UPDATE " + Tables.ITEMS + " SET buyer_unique_id = ? WHERE id = ?", buyer.toString(), itemId);
        Published published = new Published();

        load(published, itemId);

        Item item = published.single();
        assertEquals(buyer, item.getBuyerUniqueId());
        assertEquals("Buyer", item.getBuyerName());
        assertEquals(ItemStatus.PURCHASED, item.getStatus());
    }

    @Test
    @DisplayName("acheteur inconnu => publie quand meme, affiche 'Unknown', pas de quarantaine")
    void createItems_unknownBuyer_publishesWithUnknownName() throws Exception {
        UUID ghostBuyer = UUID.randomUUID();
        int itemId = listing(StorageType.PURCHASED, "payload-1");
        execute("UPDATE " + Tables.ITEMS + " SET buyer_unique_id = ? WHERE id = ?", ghostBuyer.toString(), itemId);
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, itemId);
            assertTrue(logs.contains(Level.WARNING, "unknown buyer " + ghostBuyer));
        }

        assertEquals(new ItemLoaderUtils.Result(1, 1, 0, 0, 0), result);
        Item item = published.single();
        assertEquals(ghostBuyer, item.getBuyerUniqueId());
        assertEquals("Unknown", item.getBuyerName());
    }

    // ------------------------------------------------------------------------------------------
    // Quarantaine
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("vendeur absent de la table players => quarantaine, ligne intacte")
    void createItems_unknownSeller_quarantined() throws Exception {
        int itemId = listing(StorageType.LISTED, "payload-1");
        this.players.clear(); // le vendeur n'est plus connu
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, itemId);
            assertTrue(logs.contains(Level.SEVERE, "unknown seller " + this.seller));
        }

        assertEquals(new ItemLoaderUtils.Result(0, 1, 0, 0, 1), result);
        assertTrue(published.items.isEmpty());
        verify(this.categoryManager, never()).applyCategories(any());
        assertEquals(1, count(Tables.ITEMS, "id = ?", itemId));
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId));
    }

    @Test
    @DisplayName("economie inconnue => quarantaine, ligne intacte")
    void createItems_unknownEconomy_quarantined() throws Exception {
        int itemId = listing(StorageType.LISTED, "payload-1");
        execute("UPDATE " + Tables.ITEMS + " SET economy_name = ? WHERE id = ?", "dogecoin", itemId);
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, itemId);
            assertTrue(logs.contains(Level.SEVERE, "Impossible to find the economy dogecoin"));
        }

        assertEquals(new ItemLoaderUtils.Result(0, 1, 0, 0, 1), result);
        assertTrue(published.items.isEmpty());
        assertEquals("dogecoin", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
    }

    @Test
    @DisplayName("annonce AUCTION sans aucune ligne de contenu => quarantaine, ligne intacte")
    void createItems_noContentRow_quarantined() throws Exception {
        int itemId = listing(StorageType.LISTED); // aucun contenu
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, itemId);
            assertTrue(logs.contains(Level.SEVERE, "has NO content row"));
        }

        assertEquals(new ItemLoaderUtils.Result(0, 0, 0, 0, 1), result);
        assertTrue(published.items.isEmpty());
        assertEquals(1, count(Tables.ITEMS, "id = ?", itemId));
    }

    @Test
    @DisplayName("un seul ItemStack illisible => toute l'annonce en quarantaine, contenus intacts en base")
    void createItems_undecodablePayload_quarantined() throws Exception {
        int itemId = listing(StorageType.LISTED, "payload-1", Base64Stubs.BROKEN, "payload-3");
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, itemId);
            assertTrue(logs.contains(Level.SEVERE, "has an unreadable ItemStack"));
        }

        assertEquals(new ItemLoaderUtils.Result(0, 3, 0, 0, 1), result);
        assertTrue(published.items.isEmpty());
        verify(this.categoryManager, never()).applyCategories(any());
        assertEquals(3, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId));
        assertEquals(1, count(Tables.ITEMS, "id = ? AND storage_type = ?", itemId, "LISTED"));
    }

    @Test
    @DisplayName("decode qui LEVE (Error NMS) => quarantaine, pas de propagation")
    void createItems_decodeThrows_quarantinedWithoutPropagating() throws Exception {
        int itemId = listing(StorageType.LISTED, Base64Stubs.EXPLODING);
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, itemId);
            assertTrue(logs.contains(Level.SEVERE, "Unable to decode content"));
        }

        assertEquals(new ItemLoaderUtils.Result(0, 1, 0, 0, 1), result);
        assertTrue(published.items.isEmpty());
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId));
    }

    @Test
    @DisplayName("lot mixte : les annonces valides sont publiees, les autres comptees en quarantaine")
    void createItems_mixedBatch_publishesValidAndCountsQuarantined() {
        int valid = listing(StorageType.LISTED, "payload-1");
        int empty = listing(StorageType.LISTED);
        int broken = listing(StorageType.EXPIRED, Base64Stubs.BROKEN);
        int alsoValid = listing(StorageType.PURCHASED, "payload-2");
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, valid, empty, broken, alsoValid);
            assertTrue(logs.contains(Level.SEVERE, "2 auction item(s) were NOT published"));
        }

        assertEquals(new ItemLoaderUtils.Result(2, 3, 0, 0, 2), result);
        assertEquals(List.of(valid, alsoValid), published.items.stream().map(Item::getId).toList());
        assertEquals(List.of(StorageType.LISTED, StorageType.PURCHASED), published.storageTypes);
    }

    @Test
    @DisplayName("aucune quarantaine => aucun recapitulatif SEVERE")
    void createItems_noQuarantine_noSummaryLogged() {
        int itemId = listing(StorageType.LISTED, "payload-1");

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            load(new Published(), itemId);
            assertEquals(0, logs.count(Level.SEVERE));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Types non implementes
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("un item BID n'est ni publie ni compte en quarantaine")
    void createItems_bidItem_notPublishedNotQuarantined() {
        int bidId = items().create(this.seller, ItemType.BID, BigDecimal.ONE, ListingFixtures.inOneHour(), this.economy);
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, bidId);
            assertTrue(logs.contains(Level.SEVERE, "Bid items not implemented"));
        }

        assertEquals(new ItemLoaderUtils.Result(0, 0, 0, 0, 0), result);
        assertTrue(published.items.isEmpty());
    }

    @Test
    @DisplayName("un item RENT n'est ni publie ni compte en quarantaine")
    void createItems_rentItem_notPublishedNotQuarantined() {
        int rentId = items().create(this.seller, ItemType.RENT, BigDecimal.ONE, ListingFixtures.inOneHour(), this.economy);
        Published published = new Published();

        ItemLoaderUtils.Result result;
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            result = load(published, rentId);
            assertTrue(logs.contains(Level.SEVERE, "Rent items not implemented"));
        }

        assertEquals(new ItemLoaderUtils.Result(0, 0, 0, 0, 0), result);
        assertTrue(published.items.isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // createAuctionItem seul
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("createAuctionItem : contenus null ou vides => null")
    void createAuctionItem_nullOrEmptyContents_returnsNull() {
        int itemId = listing(StorageType.LISTED);
        ItemDTO dto = dtos(itemId).getFirst();

        assertNull(this.loader.build(this.plugin, dto, "Seller", null, this.economy));
        assertNull(this.loader.build(this.plugin, dto, "Seller", List.of(), this.economy));
    }

    @Test
    @DisplayName("createAuctionItem : contenus lisibles => annonce construite depuis la ligne (dates, prix, serveur)")
    void createAuctionItem_readableContents_buildsFromRow() {
        long expiredAt = ListingFixtures.inOneHour();
        int itemId = ListingFixtures.listing(items(), auctionItems(), this.economy, this.seller, "Seller", StorageType.EXPIRED, expiredAt, "payload-5");
        ItemDTO dto = dtos(itemId).getFirst();
        List<AuctionItemDTO> contents = auctionItems().select(List.of(String.valueOf(itemId)));

        AuctionItem item = this.loader.build(this.plugin, dto, "Seller", contents, this.economy);

        assertNotNull(item);
        assertEquals(itemId, item.getId());
        assertEquals(ItemStatus.REMOVED, item.getStatus());
        assertEquals(expiredAt, item.getExpiredAt().getTime());
        assertNotNull(item.getCreatedAt());
        assertEquals(1, item.getItemStacks().size());
        assertEquals(5, item.getItemStacks().getFirst().getAmount());
        assertEquals(5, item.getAmount());
    }

    @Test
    @DisplayName("createAuctionItem : un contenu illisible au milieu => null")
    void createAuctionItem_brokenContentInMiddle_returnsNull() {
        int itemId = listing(StorageType.LISTED, "payload-1", Base64Stubs.BROKEN);
        ItemDTO dto = dtos(itemId).getFirst();
        List<AuctionItemDTO> contents = auctionItems().select(List.of(String.valueOf(itemId)));

        assertNull(this.loader.build(this.plugin, dto, "Seller", contents, this.economy));
    }

    // ------------------------------------------------------------------------------------------
    // Utilitaires proteges
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getPlayerName : joueur connu => nom, inconnu => IllegalStateException")
    void getPlayerName_knownAndUnknown() {
        assertEquals("Seller", this.loader.name(this.players, this.seller));

        UUID unknown = UUID.randomUUID();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> this.loader.name(this.players, unknown));
        assertTrue(exception.getMessage().contains(unknown.toString()));
    }

    @Test
    @DisplayName("getIDS ne garde que les identifiants du type demande, en chaines")
    void getIDS_filtersByType() {
        int auction = listing(StorageType.LISTED, "payload-1");
        int bid = items().create(this.seller, ItemType.BID, BigDecimal.ONE, ListingFixtures.inOneHour(), this.economy);
        int otherAuction = listing(StorageType.DELETED);
        List<ItemDTO> dtos = dtos(auction, bid, otherAuction);

        assertEquals(List.of(String.valueOf(auction), String.valueOf(otherAuction)), this.loader.ids(dtos, ItemType.AUCTION));
        assertEquals(List.of(String.valueOf(bid)), this.loader.ids(dtos, ItemType.BID));
        assertTrue(this.loader.ids(dtos, ItemType.RENT).isEmpty());
        assertTrue(this.loader.ids(List.of(), ItemType.AUCTION).isEmpty());
    }

    @Test
    @DisplayName("Result est un record a egalite structurelle")
    void result_recordEquality() {
        assertEquals(new ItemLoaderUtils.Result(1, 2, 3, 4, 5), new ItemLoaderUtils.Result(1, 2, 3, 4, 5));
        assertEquals(5, new ItemLoaderUtils.Result(1, 2, 3, 4, 5).quarantined());
        assertEquals(2, new ItemLoaderUtils.Result(1, 2, 3, 4, 5).auctionItems());
    }
}
