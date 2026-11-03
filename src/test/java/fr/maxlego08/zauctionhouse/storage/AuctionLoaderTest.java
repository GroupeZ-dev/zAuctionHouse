package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.testutil.Base64Stubs;
import fr.maxlego08.zauctionhouse.testutil.ListingFixtures;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AuctionLoader} : chargement du catalogue depuis la base puis bascule atomique dans les
 * conteneurs memoire de l'{@link AuctionManager} (ici une doublure).
 * <p>
 * Invariant central : CHARGER D'ABORD, REMPLACER ENSUITE. Un echec de lecture ne doit modifier
 * aucun conteneur.
 */
class AuctionLoaderTest extends StorageManagerTestBase {

    private MockedStatic<Base64ItemStack> base64;
    private AuctionManager auctionManager;
    private CategoryManager categoryManager;
    private UUID seller;

    @BeforeEach
    void setUpLoaderTest() {
        this.base64 = Base64Stubs.open();
        this.auctionManager = mock(AuctionManager.class);
        this.categoryManager = mock(CategoryManager.class);
        lenient().when(this.plugin.getAuctionManager()).thenReturn(this.auctionManager);
        lenient().when(this.plugin.getCategoryManager()).thenReturn(this.categoryManager);
        // Par defaut, aucun item en memoire et aucun detenteur vivant.
        lenient().when(this.auctionManager.getItems(any(StorageType.class))).thenReturn(List.of());
        lenient().when(this.auctionManager.getItem(any(StorageType.class), anyInt())).thenReturn(null);
        this.seller = newPlayer("Seller");
    }

    @AfterEach
    void tearDownLoaderTest() {
        this.base64.close();
    }

    private int listing(StorageType storageType, String... payloads) {
        return ListingFixtures.listing(items(), auctionItems(), this.economy, this.seller, storageType, payloads);
    }

    private AuctionLoader loader() {
        return new AuctionLoader(this.plugin, this.storageManager);
    }

    @Test
    @DisplayName("loadItems publie chaque ligne non DELETED dans son conteneur, puis reconstruit le cache trie")
    void loadItems_publishesEveryLiveRowInItsBucket() {
        int listed = listing(StorageType.LISTED, "payload-1");
        int purchased = listing(StorageType.PURCHASED, "payload-2");
        int expired = listing(StorageType.EXPIRED, "payload-3");
        int deleted = listing(StorageType.DELETED, "payload-4");

        loader().loadItems();

        ArgumentCaptor<Item> listedCaptor = ArgumentCaptor.forClass(Item.class);
        verify(this.auctionManager).addItem(eq(StorageType.LISTED), listedCaptor.capture());
        assertEquals(listed, listedCaptor.getValue().getId());
        assertEquals(ItemStatus.AVAILABLE, listedCaptor.getValue().getStatus());
        verify(this.auctionManager).addItem(eq(StorageType.PURCHASED), argThat(item -> item.getId() == purchased));
        verify(this.auctionManager).addItem(eq(StorageType.EXPIRED), argThat(item -> item.getId() == expired));
        verify(this.auctionManager, never()).addItem(eq(StorageType.DELETED), any());
        verify(this.auctionManager, never()).addItem(any(), argThat(item -> item.getId() == deleted));
        verify(this.auctionManager, times(3)).addItem(any(), any());
        verify(this.auctionManager, never()).removeItem(any(StorageType.class), anyInt());
        verify(this.auctionManager).clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEM_SHOW, PlayerCacheKey.CURRENT_PAGE);
        verify(this.auctionManager).rebuildSortedItemsCache();
        verify(this.categoryManager, times(3)).applyCategories(any());
    }

    @Test
    @DisplayName("base vide : rien n'est publie, aucun cache joueur purge, le cache trie est tout de meme reconstruit")
    void loadItems_emptyDatabase_publishesNothing() {
        loader().loadItems();

        verify(this.auctionManager, never()).addItem(any(), any());
        verify(this.auctionManager, never()).clearPlayersCache(any(PlayerCacheKey[].class));
        verify(this.auctionManager).rebuildSortedItemsCache();
    }

    @Test
    @DisplayName("les annonces en quarantaine ne sont pas publiees, les autres le sont")
    void loadItems_quarantinedRowsAreSkipped() {
        int valid = listing(StorageType.LISTED, "payload-1");
        listing(StorageType.LISTED); // sans contenu
        listing(StorageType.LISTED, Base64Stubs.BROKEN);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            loader().loadItems();
            assertTrue(logs.contains(Level.SEVERE, "2 auction item(s) were NOT published"));
            assertTrue(logs.contains(Level.INFO, "Loaded 1 items successfully (2 total)"), () -> "INFO attendu, recu : " + logs.messages(Level.INFO));
        }

        verify(this.auctionManager, times(1)).addItem(any(), any());
        verify(this.auctionManager).addItem(eq(StorageType.LISTED), argThat(item -> item.getId() == valid));
    }

    @Test
    @DisplayName("echec de lecture : exception propagee, AUCUN conteneur modifie, pas de reconstruction de cache")
    void loadItems_databaseFailure_leavesStoragesUntouched() throws Exception {
        listing(StorageType.LISTED, "payload-1");
        ZAuctionItem live = TestItems.auctionItem(this.plugin, 777, this.seller, this.economy, ListingFixtures.inOneHour());
        lenient().when(this.auctionManager.getItems(StorageType.LISTED)).thenReturn(List.of(live));
        lenient().when(this.auctionManager.getItem(StorageType.LISTED, 777)).thenReturn(live);
        // La table players disparait : PlayerRepository.select() (selectAllOrFail) doit lever.
        execute("DROP TABLE " + Tables.PLAYERS);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            assertThrows(IllegalStateException.class, () -> loader().loadItems());
            assertTrue(logs.contains(Level.SEVERE, "left UNTOUCHED"), () -> "SEVERE attendu, recu : " + logs.messages(Level.SEVERE));
        }

        verify(this.auctionManager, never()).removeItem(any(StorageType.class), anyInt());
        verify(this.auctionManager, never()).removeItem(any(StorageType.class), any(Item.class));
        verify(this.auctionManager, never()).addItem(any(), any());
        verify(this.auctionManager, never()).clearPlayersCache(any(PlayerCacheKey[].class));
        verify(this.auctionManager, never()).rebuildSortedItemsCache();
    }

    @Test
    @DisplayName("rechargement a chaud : les entrees photographiees sont purgees AVANT la publication des lignes relues")
    void loadItems_hotReload_purgesPreviousEntriesThenPublishes() {
        int itemId = listing(StorageType.LISTED, "payload-1");
        ZAuctionItem stale = TestItems.auctionItem(this.plugin, itemId, this.seller, this.economy, ListingFixtures.inOneHour());
        lenient().when(this.auctionManager.getItems(StorageType.LISTED)).thenReturn(List.of(stale));
        // Detenue AVANT la purge, plus detenue APRES : la ligne relue est publiee.
        lenient().when(this.auctionManager.getItem(StorageType.LISTED, itemId)).thenReturn(stale, (Item) null);

        loader().loadItems();

        InOrder inOrder = inOrder(this.auctionManager);
        inOrder.verify(this.auctionManager).removeItem(StorageType.LISTED, itemId);
        inOrder.verify(this.auctionManager).addItem(eq(StorageType.LISTED), argThat(item -> item.getId() == itemId));
        inOrder.verify(this.auctionManager).clearPlayersCache(any(PlayerCacheKey[].class));
        inOrder.verify(this.auctionManager).rebuildSortedItemsCache();
    }

    @Test
    @DisplayName("rechargement a chaud : une entree deja partie de son conteneur pendant la lecture n'est pas retiree")
    void loadItems_hotReload_entryGoneDuringRead_isNotRemoved() {
        ZAuctionItem gone = TestItems.auctionItem(this.plugin, 555, this.seller, this.economy, ListingFixtures.inOneHour());
        lenient().when(this.auctionManager.getItems(StorageType.LISTED)).thenReturn(List.of(gone));
        // getItem rend null : l'entree a quitte le conteneur entre la photo et la bascule.

        loader().loadItems();

        verify(this.auctionManager, never()).removeItem(any(StorageType.class), anyInt());
    }

    @Test
    @DisplayName("rechargement a chaud : une instance vivante encore detenue apres la purge gagne sur la ligne relue")
    void loadItems_hotReload_liveInstanceWinsOverStaleRow() {
        int itemId = listing(StorageType.LISTED, "payload-1");
        ZAuctionItem live = TestItems.auctionItem(this.plugin, itemId, this.seller, this.economy, ListingFixtures.inOneHour());
        // Absente de la photo (aucun getItems), mais detenue en PURCHASED au moment de la bascule :
        // elle a ete achetee PENDANT la lecture, l'etat vivant est plus recent que la ligne relue.
        lenient().when(this.auctionManager.getItem(StorageType.PURCHASED, itemId)).thenReturn(live);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            loader().loadItems();
            assertTrue(logs.contains(Level.WARNING, "Auction item #" + itemId + " changed while the auction house was reloading"));
        }

        verify(this.auctionManager, never()).addItem(any(), any());
        verify(this.auctionManager, never()).removeItem(any(StorageType.class), anyInt());
        verify(this.auctionManager, never()).clearPlayersCache(any(PlayerCacheKey[].class));
        verify(this.auctionManager).rebuildSortedItemsCache();
    }

    @Test
    @DisplayName("ZStorageManager.loadItems delegue au chargeur : les lignes arrivent dans l'AuctionManager")
    void storageManagerLoadItems_delegatesToLoader() {
        int itemId = listing(StorageType.EXPIRED, "payload-1");

        this.storageManager.loadItems();

        verify(this.auctionManager).addItem(eq(StorageType.EXPIRED), argThat(item -> item.getId() == itemId));
        verify(this.auctionManager).rebuildSortedItemsCache();
    }
}
