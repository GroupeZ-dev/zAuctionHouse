package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.zauctionhouse.ZAuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.component.ComponentMessage;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ActionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

/**
 * Socle des tests de services : un VRAI {@code ZStorageManager} (SQLite) herite du socle, un VRAI
 * {@link ZAuctionManager} (espionne pour neutraliser uniquement les appels zMenu : ouverture et
 * rafraichissement d'inventaires), un VRAI {@link LocalAuctionClusterBridge}, et les statiques
 * Bukkit / Base64ItemStack / PlaceholderAPI mockees.
 * <p>
 * Tout est SYNCHRONE : executor et ordonnanceur executent immediatement sur le thread du test,
 * les futures rendus par les services sont donc deja completes au retour.
 * <p>
 * Les messages envoyes aux joueurs transitent par {@link #componentMessage} (une doublure
 * substituee a {@code ComponentMessageHelper.componentMessage} AVANT la construction des
 * services, qui en capturent la reference dans {@code MessageUtils}).
 */
abstract class ServiceTestBase extends StorageManagerTestBase {

    protected static final BigDecimal PRICE = BigDecimal.TEN;
    /** Expiration par defaut (secondes) servie par les ExpirationConfiguration de test. */
    protected static final long DEFAULT_EXPIRATION_SECONDS = 3600L;
    protected static final int INVENTORY_SIZE = 36;

    protected MockedStatic<Bukkit> bukkit;
    protected MockedStatic<Base64ItemStack> base64;
    protected MockedStatic<PlaceholderAPI> placeholderApi;
    protected PluginManager pluginManager;
    protected Server server;
    protected ComponentMessage componentMessage;
    private ComponentMessage previousComponentMessage;
    protected InventoryManager inventoryManager;
    protected CategoryManager categoryManager;
    protected LocalAuctionClusterBridge localBridge;
    protected ZAuctionManager manager;

    @BeforeEach
    void setUpServices() {
        this.pluginManager = mock(PluginManager.class);
        this.server = mock(Server.class);

        this.bukkit = mockStatic(Bukkit.class);
        this.bukkit.when(Bukkit::getPluginManager).thenReturn(this.pluginManager);
        this.bukkit.when(Bukkit::getServer).thenReturn(this.server);

        ItemStack decoded = stack(Material.DIAMOND, 1);
        this.base64 = mockStatic(Base64ItemStack.class);
        this.base64.when(() -> Base64ItemStack.encode(any())).thenReturn("encoded-stack");
        this.base64.when(() -> Base64ItemStack.decode(anyString())).thenReturn(decoded);

        this.placeholderApi = mockStatic(PlaceholderAPI.class);
        this.placeholderApi.when(() -> PlaceholderAPI.setPlaceholders(any(Player.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        this.previousComponentMessage = ComponentMessageHelper.componentMessage;
        this.componentMessage = mock(ComponentMessage.class);
        ComponentMessageHelper.componentMessage = this.componentMessage;

        this.inventoryManager = mock(InventoryManager.class);
        InventoriesLoader inventoriesLoader = mock(InventoriesLoader.class);
        lenient().when(inventoriesLoader.getInventoryManager()).thenReturn(this.inventoryManager);
        this.categoryManager = mock(CategoryManager.class);
        this.localBridge = new LocalAuctionClusterBridge();

        lenient().when(this.plugin.getServer()).thenReturn(this.server);
        lenient().when(this.plugin.getInventoriesLoader()).thenReturn(inventoriesLoader);
        lenient().when(this.plugin.getCategoryManager()).thenReturn(this.categoryManager);
        lenient().when(this.plugin.getAuctionClusterBridge()).thenReturn(this.localBridge);

        Configuration configuration = this.plugin.getConfiguration();
        PerformanceConfiguration performance = new PerformanceConfiguration(10_000, 5_000, 1, 5_000L, 5_000L, 3_000L, 3_000L, 5_000L);
        ExpirationConfiguration expiration = new ExpirationConfiguration(DEFAULT_EXPIRATION_SECONDS, false, Map.of());
        lenient().when(configuration.getPerformance()).thenReturn(performance);
        lenient().when(configuration.getExpireExpiration()).thenReturn(expiration);
        lenient().when(configuration.getPurchaseExpiration()).thenReturn(expiration);
        configureActions(false, false, false, false);

        EconomyManager economyManager = this.plugin.getEconomyManager();
        lenient().when(economyManager.format(any(AuctionEconomy.class), any())).thenReturn("10$");
        lenient().when(economyManager.format(any(PriceFormat.class), any())).thenReturn("10$");

        lenient().when(this.economy.withdrawChecked(any(UUID.class), any(BigDecimal.class), any())).thenReturn(true);
        lenient().when(this.economy.depositChecked(any(UUID.class), any(BigDecimal.class), any())).thenReturn(true);
        lenient().when(this.economy.getWithdrawReason()).thenReturn("withdraw %seller% %items%");
        lenient().when(this.economy.getDepositReason()).thenReturn("deposit %buyer% %items%");
        lenient().when(this.economy.isAutoClaim()).thenReturn(false);

        // Le manager est REEL (store memoire, index, transitions, ecritures base). Seuls les
        // points de contact avec zMenu sont neutralises.
        this.manager = spy(new ZAuctionManager(this.plugin));
        doNothing().when(this.manager).updateListedItems(any(), anyBoolean(), any());
        doNothing().when(this.manager).openMainAuction(any(Player.class));
        doNothing().when(this.manager).openMainAuction(any(Player.class), anyInt());
        doNothing().when(this.manager).updateInventory(any(Player.class));
        this.manager.setupSortedItemsCache();
        lenient().when(this.plugin.getAuctionManager()).thenReturn(this.manager);
    }

    @AfterEach
    void tearDownServices() {
        try {
            if (this.manager != null) this.manager.shutdown();
        } finally {
            ComponentMessageHelper.componentMessage = this.previousComponentMessage;
            if (this.placeholderApi != null) this.placeholderApi.close();
            if (this.base64 != null) this.base64.close();
            if (this.bukkit != null) this.bukkit.close();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------------------------

    /**
     * Rejoue la configuration des actions.
     *
     * @param listedGiveItem     remove-listed-item.give-item
     * @param purchasedGiveItem  purchased-item.give-item
     * @param purchasedFreeSpace purchased-item.player-inventory-must-have-free-space
     * @param claimFreeSpace     selling/expired.player-inventory-must-have-free-space
     */
    protected void configureActions(boolean listedGiveItem, boolean purchasedGiveItem, boolean purchasedFreeSpace, boolean claimFreeSpace) {
        ActionConfiguration actions = new ActionConfiguration(
                false, true, true,
                new ActionConfiguration.ListedConfiguration(listedGiveItem, false, false),
                new ActionConfiguration.PurchasedConfiguration(purchasedGiveItem, false, null, false, null, purchasedFreeSpace),
                new ActionConfiguration.SellingConfiguration(false, claimFreeSpace),
                new ActionConfiguration.ExpiredConfiguration(false, claimFreeSpace),
                new ActionConfiguration.ContainerPreviewConfiguration(false, ClickType.RIGHT));
        lenient().when(this.plugin.getConfiguration().getActions()).thenReturn(actions);
    }

    protected void configureExpireExpiration(long seconds) {
        lenient().when(this.plugin.getConfiguration().getExpireExpiration()).thenReturn(new ExpirationConfiguration(seconds, false, Map.of()));
    }

    /**
     * Fait repondre {@code setCancelled(true)} au PluginManager pour tout evenement du type donne.
     */
    protected void cancelEvents(Class<? extends Event> eventType) {
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event instanceof Cancellable cancellable) cancellable.setCancelled(true);
            return null;
        }).when(this.pluginManager).callEvent(any(eventType));
    }

    // ------------------------------------------------------------------------------------------
    // Bridge cluster mocke
    // ------------------------------------------------------------------------------------------

    /**
     * Bridge entierement mocke dont tous les futures sont completes.
     *
     * @param distributed valeur de {@code isDistributed()}
     * @param lockOutcome jeton rendu par {@code lockItem} : {@code null} pour un jeton acquis
     *                    frais, sinon la sentinelle voulue ({@code LockToken.noop()} ...)
     */
    protected AuctionClusterBridge mockBridge(boolean distributed, LockToken lockOutcome) {
        AuctionClusterBridge bridge = mock(AuctionClusterBridge.class);
        lenient().when(bridge.isDistributed()).thenReturn(distributed);
        lenient().when(bridge.checkAvailability(any(Item.class))).thenReturn(CompletableFuture.completedFuture(true));
        lenient().when(bridge.lockItem(any(), any(), any())).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            UUID locker = invocation.getArgument(1);
            return CompletableFuture.completedFuture(lockOutcome != null ? lockOutcome : LockToken.issue(item, locker));
        });
        lenient().when(bridge.unlockItem(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(bridge.releaseLock(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        lenient().when(bridge.isHeldBy(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        lenient().when(bridge.notifyItemBought(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(bridge.notifyItemListed(any())).thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(bridge.notifyItemStatusChange(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(bridge.removeItem(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(bridge.removeItem(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(bridge.renewLock(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        return bridge;
    }

    protected void useBridge(AuctionClusterBridge bridge) {
        lenient().when(this.plugin.getAuctionClusterBridge()).thenReturn(bridge);
    }

    protected static <T> CompletableFuture<T> failed(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    // ------------------------------------------------------------------------------------------
    // Joueurs, inventaires, ItemStack
    // ------------------------------------------------------------------------------------------

    /**
     * ItemStack mocke suffisant pour {@code ZAuctionManager.giveItem / fitsInInventory}.
     */
    protected static ItemStack stack(Material material, int amount) {
        ItemStack itemStack = mock(ItemStack.class);
        lenient().when(itemStack.getType()).thenReturn(material);
        lenient().when(itemStack.getAmount()).thenReturn(amount);
        lenient().when(itemStack.getMaxStackSize()).thenReturn(64);
        lenient().when(itemStack.clone()).thenReturn(itemStack);
        lenient().when(itemStack.translationKey()).thenReturn("item.minecraft." + material.name().toLowerCase());
        lenient().when(itemStack.hasItemMeta()).thenReturn(false);
        lenient().when(itemStack.isSimilar(any())).thenReturn(false);
        return itemStack;
    }

    /**
     * Inventaire mocke : vide (36 cases libres) ou plein (36 piles de pierre non fusionnables).
     */
    protected static PlayerInventory inventory(boolean hasSpace) {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack[] contents = new ItemStack[INVENTORY_SIZE];
        if (!hasSpace) {
            for (int index = 0; index < contents.length; index++) contents[index] = stack(Material.STONE, 64);
        }
        lenient().when(inventory.firstEmpty()).thenReturn(hasSpace ? 0 : -1);
        lenient().when(inventory.getStorageContents()).thenReturn(contents);
        lenient().when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        return inventory;
    }

    protected static Player player(String name, boolean hasSpace) {
        Player player = MockPlugins.player(name);
        PlayerInventory inventory = inventory(hasSpace);
        lenient().when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    /**
     * Vendeur hors ligne (ou en ligne si {@code onlinePlayer} est fourni).
     */
    protected static OfflinePlayer offlinePlayer(UUID uniqueId, String name, Player onlinePlayer) {
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        lenient().when(offlinePlayer.getUniqueId()).thenReturn(uniqueId);
        lenient().when(offlinePlayer.getName()).thenReturn(name);
        lenient().when(offlinePlayer.isOnline()).thenReturn(onlinePlayer != null);
        lenient().when(offlinePlayer.getPlayer()).thenReturn(onlinePlayer);
        return offlinePlayer;
    }

    // ------------------------------------------------------------------------------------------
    // Annonces
    // ------------------------------------------------------------------------------------------

    /**
     * Annonce de test : identique a {@link ZAuctionItem} sauf le vendeur (rendu sans passer par
     * {@code Bukkit.getOfflinePlayer}) et l'affichage (sans MiniMessage ni ItemMeta).
     */
    protected static final class TestAuctionItem extends ZAuctionItem {

        private OfflinePlayer seller;

        TestAuctionItem(AuctionPlugin plugin, int id, UUID sellerUniqueId, String sellerName, BigDecimal price, AuctionEconomy economy, Date expiredAt, List<ItemStack> itemStacks, OfflinePlayer seller) {
            super(plugin, id, MockPlugins.SERVER_NAME, sellerUniqueId, sellerName, price, economy, new Date(), expiredAt, itemStacks);
            this.seller = seller;
        }

        @Override
        public OfflinePlayer getSeller() {
            return this.seller;
        }

        void setSeller(OfflinePlayer seller) {
            this.seller = seller;
        }

        @Override
        public String getItemDisplay() {
            return getItemsAsString();
        }
    }

    protected long farFuture() {
        return System.currentTimeMillis() + 86_400_000L;
    }

    /**
     * Insere une annonce LISTED en base (ligne parente + un contenu), construit l'objet memoire
     * et l'enregistre dans le store LISTED du manager.
     *
     * @param sellerUniqueId vendeur (deja present dans la table players)
     * @param sellerName     son nom
     * @param expiredAt      expiration en millis
     * @param seller         representation OfflinePlayer du vendeur
     */
    protected TestAuctionItem listItem(UUID sellerUniqueId, String sellerName, long expiredAt, OfflinePlayer seller) {
        int itemId = items().create(sellerUniqueId, ItemType.AUCTION, PRICE, expiredAt, this.economy);
        List<ItemStack> stacks = List.of(stack(Material.DIAMOND, 1));
        auctionItems().create(sellerUniqueId, sellerName, itemId, PRICE, expiredAt, stacks, List.of("payload-" + itemId), this.economy);
        TestAuctionItem item = new TestAuctionItem(this.plugin, itemId, sellerUniqueId, sellerName, PRICE, this.economy, new Date(expiredAt), stacks, seller);
        this.manager.addItem(StorageType.LISTED, item);
        return item;
    }

    protected TestAuctionItem listItem(UUID sellerUniqueId, String sellerName, OfflinePlayer seller) {
        return listItem(sellerUniqueId, sellerName, farFuture(), seller);
    }

    /**
     * Deplace une annonce (base + store memoire + statut) vers un autre conteneur, comme si elle
     * y avait ete chargee au demarrage.
     */
    protected void moveTo(TestAuctionItem item, StorageType from, StorageType to, ItemStatus status, UUID buyer) throws SQLException {
        setRow(item.getId(), to, buyer);
        this.manager.removeItem(from, item);
        item.setStatus(status);
        if (buyer != null) item.setBuyer(buyer, "Buyer");
        this.manager.addItem(to, item);
    }

    // ------------------------------------------------------------------------------------------
    // SQL brut
    // ------------------------------------------------------------------------------------------

    /**
     * Simule l'ecriture d'un AUTRE serveur : la ligne change d'etat sans que la memoire locale
     * ne le sache.
     */
    protected void setRow(int itemId, StorageType storageType, UUID buyer) throws SQLException {
        execute("UPDATE " + Tables.ITEMS + " SET storage_type = ?, buyer_unique_id = ? WHERE id = ?",
                storageType.name(), buyer == null ? null : buyer.toString(), itemId);
    }

    protected String storageTypeOf(int itemId) throws SQLException {
        return queryString("SELECT storage_type FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    protected String buyerOf(int itemId) throws SQLException {
        return queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    protected long expiredAtOf(int itemId) throws SQLException {
        return queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    protected long transactionCount(int itemId) throws SQLException {
        return count(Tables.TRANSACTIONS, "item_id = ?", itemId);
    }

    protected String transactionStatus(int itemId, UUID playerUniqueId) throws SQLException {
        return queryString("SELECT status FROM " + Tables.TRANSACTIONS + " WHERE item_id = ? AND player_unique_id = ?", itemId, playerUniqueId.toString());
    }

    protected BigDecimal transactionValue(int itemId, UUID playerUniqueId) throws SQLException {
        String value = queryString("SELECT value FROM " + Tables.TRANSACTIONS + " WHERE item_id = ? AND player_unique_id = ?", itemId, playerUniqueId.toString());
        return value == null ? null : new BigDecimal(value);
    }

    protected long logCount(int itemId, String logType) throws SQLException {
        return count(Tables.LOGS, "item_id = ? AND log_type = ?", itemId, logType);
    }

    protected String logAdditionalData(int itemId, String logType) throws SQLException {
        return queryString("SELECT additional_data FROM " + Tables.LOGS + " WHERE item_id = ? AND log_type = ?", itemId, logType);
    }
}
