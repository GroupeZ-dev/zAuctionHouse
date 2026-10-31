package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.enums.EntityTaskResult;
import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemDisplayConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PermissionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.WorldConfiguration;
import fr.maxlego08.zauctionhouse.api.event.events.sell.AuctionPreSellEvent;
import fr.maxlego08.zauctionhouse.api.event.events.stats.AuctionItemListedEvent;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleManager;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.services.result.SellFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.SellResult;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.tax.TaxAmountType;
import fr.maxlego08.zauctionhouse.api.tax.TaxConfiguration;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.storage.ZStorageManager;
import fr.maxlego08.zauctionhouse.tax.ZItemTaxRule;
import fr.maxlego08.zauctionhouse.tax.ZTaxConfiguration;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link SellService} sur une base SQLite REELLE (vrai {@link ZStorageManager}).
 * <p>
 * Le chemin de vente est le plus sensible a la duplication : chaque test asserte l'etat FINAL des
 * lignes {@code items} / {@code auction_items} (storage_type, pending_publish, contenus) en plus du
 * {@link SellResult} rendu et des mouvements d'inventaire.
 * <p>
 * Particularites du montage :
 * <ul>
 *   <li>pas de serveur Bukkit : {@code Bukkit.getPluginManager()} est mocke statiquement pour que
 *   {@code Event.callEvent()} fonctionne, et {@code Base64ItemStack.encode} rend une charge utile
 *   deterministe par ItemStack ;</li>
 *   <li>{@code Material.isAir()} passe par le registre Paper, indisponible hors serveur : les
 *   materiaux des ItemStack vendus sont des doublures d'enum (Mockito inline) ;</li>
 *   <li>l'ordonnanceur execute tout immediatement et {@code runAtEntity} rend
 *   {@code EntityTaskResult.SUCCESS}, comme le ferait un serveur dont l'entite est vivante ;</li>
 *   <li>{@code message(...)} est intercepte dans une sous-classe pour etre asserte sans passer par
 *   MiniMessage / PlaceholderAPI.</li>
 * </ul>
 */
class SellServiceTest extends StorageManagerTestBase {

    private static final int SLOT = 3;
    private static final int OTHER_SLOT = 7;
    private static final String SELLER_NAME = "Seller";
    private static final String SELL_PERMISSION = "zauctionhouse.sell.5";
    private static final String BANNED_WORLD = "banned_world";
    private static final String SELL_TAX_REASON = "Sell tax (zAuctionHouse)";

    private final Map<ItemStack, String> payloads = new IdentityHashMap<>();
    private final AtomicBoolean online = new AtomicBoolean(true);
    private final AtomicBoolean entityTasksRun = new AtomicBoolean(true);

    private long expiredAt;
    private UUID sellerId;
    private AuctionManager manager;
    private ItemRuleManager ruleManager;
    private CategoryManager categoryManager;
    private PluginManager pluginManager;
    private Configuration configuration;
    private Player player;
    private PlayerInventory inventory;
    private World world;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<Base64ItemStack> base64;
    private RecordingSellService service;

    // ------------------------------------------------------------------------------------------
    // Montage
    // ------------------------------------------------------------------------------------------

    @BeforeEach
    void setUpSellService() {
        this.expiredAt = System.currentTimeMillis() + 3_600_000L;
        this.configuration = this.plugin.getConfiguration();

        // Collaborateurs construits AVANT tout when(...) : Mockito refuse la creation d'un mock
        // pendant un stub en cours.
        this.manager = mock(AuctionManager.class);
        this.ruleManager = mock(ItemRuleManager.class);
        this.categoryManager = mock(CategoryManager.class);
        this.pluginManager = mock(PluginManager.class);
        AuctionClusterBridge bridge = new LocalAuctionClusterBridge();
        PlatformScheduler scheduler = entityScheduler(this.entityTasksRun);
        PermissionConfiguration permissionConfiguration = permissions(SELL_PERMISSION, 5);
        WorldConfiguration worldConfiguration = bannedWorlds(BANNED_WORLD);
        ItemDisplayConfiguration itemDisplayConfiguration = new ItemDisplayConfiguration(
                "x%amount% <lang:%item-translation-key%>", "x%amount% %item-name%", " and ", ", ", false);

        when(this.plugin.getAuctionManager()).thenReturn(this.manager);
        when(this.plugin.getItemRuleManager()).thenReturn(this.ruleManager);
        when(this.plugin.getCategoryManager()).thenReturn(this.categoryManager);
        when(this.plugin.getAuctionClusterBridge()).thenReturn(bridge);
        when(this.plugin.getScheduler()).thenReturn(scheduler);

        when(this.configuration.getPermission()).thenReturn(permissionConfiguration);
        when(this.configuration.getWorld()).thenReturn(worldConfiguration);
        // Record REEL : en deep stubs, une chaine rendue par un record mocke vaut null, et
        // ZAuctionItem.getItemDisplay (appele par postSell) ferait un replace(...) sur null.
        when(this.configuration.getItemDisplay()).thenReturn(itemDisplayConfiguration);
        when(this.configuration.isAllowDecimalPrices()).thenReturn(true);
        when(this.configuration.isSaveProfileOnSell()).thenReturn(false);

        when(this.economy.getMaxPrice(any())).thenReturn(new BigDecimal("1000000"));
        when(this.economy.getMinPrice(any())).thenReturn(BigDecimal.ONE);
        when(this.economy.withdrawChecked(any(), any(), anyString())).thenReturn(true);
        when(this.economy.depositChecked(any(), any(), anyString())).thenReturn(true);
        useTax(ZTaxConfiguration.disabled());

        // Le joueur existe dans l'annuaire : la FK logs.player_unique_id -> players reste coherente.
        this.sellerId = newPlayer(SELLER_NAME);
        this.player = MockPlugins.player(SELLER_NAME);
        this.inventory = mock(PlayerInventory.class);
        this.world = mock(World.class);
        when(this.player.isOnline()).thenAnswer(invocation -> this.online.get());
        when(this.player.getInventory()).thenReturn(this.inventory);
        when(this.player.getWorld()).thenReturn(this.world);
        when(this.player.hasPermission(SELL_PERMISSION)).thenReturn(true);
        when(this.world.getName()).thenReturn("world");

        this.bukkit = mockStatic(Bukkit.class);
        this.bukkit.when(Bukkit::getPluginManager).thenReturn(this.pluginManager);
        this.base64 = mockStatic(Base64ItemStack.class);
        this.base64.when(() -> Base64ItemStack.encode(any())).thenAnswer(invocation -> payloadOf(invocation.getArgument(0)));

        this.service = new RecordingSellService(this.plugin, this.manager);
    }

    @AfterEach
    void closeStaticMocks() {
        if (this.bukkit != null) this.bukkit.close();
        if (this.base64 != null) this.base64.close();
    }

    /**
     * {@link SellService} dont les messages au joueur sont enregistres au lieu d'etre envoyes :
     * l'envoi reel passe par MiniMessage et PlaceholderAPI, indisponibles hors serveur.
     */
    private static final class RecordingSellService extends SellService {

        private final List<Message> messages = new ArrayList<>();

        RecordingSellService(AuctionPlugin plugin, AuctionManager manager) {
            super(plugin, manager);
        }

        @Override
        protected void message(AuctionPlugin plugin, CommandSender sender, Message message, Object... args) {
            this.messages.add(message);
        }
    }

    /**
     * Ordonnanceur FoliaLib immediat dont {@code runAtEntity} rend {@link EntityTaskResult#SUCCESS}
     * quand la tache a ete executee, et {@link EntityTaskResult#ENTITY_RETIRED} SANS executer la
     * tache quand {@code runEntityTasks} est a false (entite retiree sous Folia).
     */
    private static PlatformScheduler entityScheduler(AtomicBoolean runEntityTasks) {
        WrappedTask wrappedTask = mock(WrappedTask.class);
        Answer<Object> answer = invocation -> {
            boolean entityTask = invocation.getMethod().getName().startsWith("runAtEntity");
            boolean run = !entityTask || runEntityTasks.get();
            if (run) {
                for (Object argument : invocation.getArguments()) {
                    if (argument instanceof Runnable runnable) {
                        runnable.run();
                    } else if (argument instanceof Consumer<?> consumer) {
                        @SuppressWarnings("unchecked")
                        Consumer<WrappedTask> taskConsumer = (Consumer<WrappedTask>) consumer;
                        taskConsumer.accept(wrappedTask);
                    }
                }
            }
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (returnType == CompletableFuture.class) {
                if (entityTask) {
                    return CompletableFuture.completedFuture(run ? EntityTaskResult.SUCCESS : EntityTaskResult.ENTITY_RETIRED);
                }
                return CompletableFuture.completedFuture(null);
            }
            if (returnType == WrappedTask.class) return wrappedTask;
            if (returnType == boolean.class) return true;
            return null;
        };
        return mock(PlatformScheduler.class, answer);
    }

    private static PermissionConfiguration permissions(String permission, int limit) {
        EnumMap<ItemType, Map<String, Integer>> permissions = new EnumMap<>(ItemType.class);
        for (ItemType itemType : ItemType.values()) permissions.put(itemType, new HashMap<>());
        permissions.get(ItemType.AUCTION).put(permission, limit);
        return new PermissionConfiguration(permissions);
    }

    private static WorldConfiguration bannedWorlds(String... worldNames) {
        EnumMap<ItemType, List<String>> worlds = new EnumMap<>(ItemType.class);
        for (ItemType itemType : ItemType.values()) worlds.put(itemType, new ArrayList<>());
        worlds.get(ItemType.AUCTION).addAll(List.of(worldNames));
        return new WorldConfiguration(worlds);
    }

    /**
     * Branche une configuration de taxe REELLE sur l'economie mockee : la methode default
     * {@code calculateSellTax} de l'interface est stubbee pour deleguer a la configuration.
     */
    private void useTax(TaxConfiguration taxConfiguration) {
        when(this.economy.getTaxConfiguration()).thenReturn(taxConfiguration);
        when(this.economy.calculateSellTax(any(), any(), any())).thenAnswer(invocation ->
                taxConfiguration.calculateSellTax(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    private static ZTaxConfiguration sellTax(double percentage) {
        return new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, percentage, null, List.of(), false, List.of());
    }

    /**
     * ItemStack mocke dont le materiau est lui aussi une doublure : {@code Material.isAir()} et
     * {@code translationKey()} passent par le registre Paper, absent hors serveur.
     */
    private static ItemStack stack(String materialName, int amount) {
        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        when(material.name()).thenReturn(materialName);
        ItemStack itemStack = TestItems.itemStack(material, amount);
        when(itemStack.translationKey()).thenReturn("item.minecraft." + materialName.toLowerCase());
        when(itemStack.isSimilar(itemStack)).thenReturn(true);
        return itemStack;
    }

    private static ItemStack air() {
        Material material = mock(Material.class);
        when(material.isAir()).thenReturn(true);
        when(material.name()).thenReturn("AIR");
        return TestItems.itemStack(material, 1);
    }

    private String payloadOf(ItemStack itemStack) {
        return this.payloads.computeIfAbsent(itemStack, ignored -> "payload-" + (this.payloads.size() + 1));
    }

    private ItemStack placeInSlot(int slot, ItemStack itemStack) {
        when(this.inventory.getItem(slot)).thenReturn(itemStack);
        return itemStack;
    }

    private SellResult sell(BigDecimal price, Map<Integer, ItemStack> slotItems) {
        CompletableFuture<SellResult> future = this.service.sellAuctionItems(this.player, price, this.expiredAt, slotItems, this.economy);
        assertTrue(future.isDone(), "toute la chaine de vente doit etre synchrone dans ce montage");
        return future.join();
    }

    private SellResult sellOne(BigDecimal price) {
        ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
        return sell(price, Map.of(SLOT, diamonds));
    }

    /**
     * Remplace le gestionnaire de stockage par un espion dont {@code publishAuctionItem} est force.
     */
    private void failPublishWith(CompletableFuture<Integer> publishResult) {
        ZStorageManager spied = spy(this.storageManager);
        doReturn(publishResult).when(spied).publishAuctionItem(any());
        when(this.plugin.getStorageManager()).thenReturn(spied);
    }

    private List<Event> firedEvents() {
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(this.pluginManager, atLeast(0)).callEvent(captor.capture());
        return captor.getAllValues();
    }

    private void assertNoListedEvent() {
        assertTrue(firedEvents().stream().noneMatch(event -> event instanceof AuctionItemListedEvent),
                "AuctionItemListedEvent ne doit etre tire qu'apres une publication reussie");
    }

    private void assertNothingStored() throws Exception {
        assertEquals(0, count(Tables.ITEMS), "aucune ligne items ne doit exister");
        assertEquals(0, count(Tables.AUCTION_ITEMS), "aucun contenu ne doit exister");
    }

    private void assertInventoryUntouched() {
        verify(this.inventory, never()).setItem(anyInt(), any());
        verify(this.inventory, never()).setItemInMainHand(any());
        verify(this.inventory, never()).addItem(any(ItemStack.class));
    }

    private int singleItemId() throws Exception {
        assertEquals(1, count(Tables.ITEMS), "exactement une ligne items attendue");
        return (int) queryLong("SELECT id FROM " + Tables.ITEMS);
    }

    private String storageTypeOf(int id) throws Exception {
        return queryString("SELECT storage_type FROM " + Tables.ITEMS + " WHERE id = ?", id);
    }

    private int pendingOf(int id) throws Exception {
        Object value = queryObject("SELECT pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", id);
        assertNotNull(value, "pending_publish ne doit jamais etre NULL sur une ligne creee par la vente");
        return ((Number) value).intValue();
    }

    private BigDecimal priceOf(int id) throws Exception {
        Object value = queryObject("SELECT price FROM " + Tables.ITEMS + " WHERE id = ?", id);
        return new BigDecimal(String.valueOf(value));
    }

    private List<String> payloadsOf(int id) throws Exception {
        return queryRows("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " WHERE item_id = ? ORDER BY id", id)
                .stream().map(row -> String.valueOf(row[0])).toList();
    }

    // ------------------------------------------------------------------------------------------
    // Succes
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("vente reussie")
    class Success {

        @Test
        @DisplayName("une vente mono-stack publie une ligne LISTED pending 0 avec son contenu et retire le stack")
        void sell_singleStack_publishesListedRow() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(SellFailReason.NONE, result.getFailReason());
            AuctionItem auctionItem = result.getAuctionItem().orElseThrow();

            int id = singleItemId();
            assertEquals(id, auctionItem.getId());
            assertEquals("LISTED", storageTypeOf(id));
            assertEquals(0, pendingOf(id));
            assertEquals(0, new BigDecimal("100").compareTo(priceOf(id)));
            assertEquals(sellerId.toString(), queryString("SELECT seller_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", id));
            assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE id = ?", id));
            assertEquals(MockPlugins.SERVER_NAME, queryString("SELECT server_name FROM " + Tables.ITEMS + " WHERE id = ?", id));
            assertEquals("AUCTION", queryString("SELECT item_type FROM " + Tables.ITEMS + " WHERE id = ?", id));
            assertEquals(List.of(payloadOf(diamonds)), payloadsOf(id));

            // relecture par le repository : la ligne est visible (select filtre DELETED)
            ItemDTO dto = items().select(id).orElseThrow();
            assertEquals(StorageType.LISTED, dto.storage_type());
            assertEquals(expiredAt, dto.expired_at().getTime());

            // le stack entier est vendu : le slot est vide (pas de decrementation du stack en place)
            verify(inventory).setItem(SLOT, null);
            // chemin mono-stack : la quantite de l'evenement (inchangee, 5) est reportee sur le clone
            verify(diamonds).setAmount(5);
            verify(economy, never()).withdrawChecked(any(), any(), anyString());
        }

        @Test
        @DisplayName("l'evenement Pre est tire avant l'INSERT et l'evenement Post apres la publication")
        void sell_firesPreThenPostEvent() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            List<Event> events = firedEvents();
            assertEquals(2, events.size(), "exactement un Pre et un Post");
            AuctionPreSellEvent pre = assertInstanceOf(AuctionPreSellEvent.class, events.get(0));
            AuctionItemListedEvent post = assertInstanceOf(AuctionItemListedEvent.class, events.get(1));

            assertSame(player, pre.getPlayer());
            assertEquals(5, pre.getAmount());
            assertEquals(0, new BigDecimal("100").compareTo(pre.getPrice()));
            assertEquals(expiredAt, pre.getExpiredAt());
            assertSame(economy, pre.getAuctionEconomy());
            assertSame(diamonds, pre.getItemStack());

            assertSame(player, post.getSeller());
            assertEquals(result.getAuctionItem().orElseThrow().getId(), post.getItem().getId());
            assertNull(post.getSellTax(), "sans taxe, l'evenement Post ne porte pas de TaxResult");

            InOrder inOrder = inOrder(pluginManager);
            inOrder.verify(pluginManager).callEvent(isA(AuctionPreSellEvent.class));
            inOrder.verify(pluginManager).callEvent(isA(AuctionItemListedEvent.class));
        }

        @Test
        @DisplayName("postSell met a jour le manager, les caches, les categories, le journal et le message")
        void sell_postSell_updatesManagerCachesAndLog() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));
            AuctionItem auctionItem = result.getAuctionItem().orElseThrow();
            int id = singleItemId();

            verify(categoryManager).applyCategories(auctionItem);
            verify(manager).addItem(StorageType.LISTED, auctionItem);
            verify(manager).clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            verify(manager).clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING);
            verify(manager).updateListedItems(auctionItem, true, player);

            assertEquals(1, count(Tables.LOGS, "log_type = ? AND item_id = ? AND player_unique_id = ?", "SALE", id, sellerId.toString()));
            assertEquals("added_auction_item_to_listed", queryString("SELECT additional_data FROM " + Tables.LOGS + " WHERE item_id = ?", id));
            assertEquals(payloadOf(diamonds), queryString("SELECT itemstack FROM " + Tables.LOGS + " WHERE item_id = ?", id));

            assertEquals(List.of(Message.ITEM_SOLD), service.messages);
        }

        @Test
        @DisplayName("une vente multi-stack insere les contenus dans l'ordre des slots et vide chaque slot")
        void sell_multiStack_insertsContentsInOrder() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            ItemStack gold = placeInSlot(OTHER_SLOT, stack("GOLD_INGOT", 2));
            Map<Integer, ItemStack> slots = new LinkedHashMap<>();
            slots.put(SLOT, diamonds);
            slots.put(OTHER_SLOT, gold);

            SellResult result = sell(new BigDecimal("100"), slots);

            assertTrue(result.isSuccess(), result.getMessage());
            int id = singleItemId();
            assertEquals("LISTED", storageTypeOf(id));
            assertEquals(2, count(Tables.AUCTION_ITEMS, "item_id = ?", id));
            assertEquals(List.of(payloadOf(diamonds), payloadOf(gold)), payloadsOf(id));
            assertEquals(2, result.getAuctionItem().orElseThrow().getItemStacks().size());

            verify(inventory).setItem(SLOT, null);
            verify(inventory).setItem(OTHER_SLOT, null);

            AuctionPreSellEvent pre = assertInstanceOf(AuctionPreSellEvent.class, firedEvents().get(0));
            assertEquals(7, pre.getAmount(), "la quantite de l'evenement est le total des stacks");
            // multi-stack : les mutations de l'event ne sont pas appliquees, aucun setAmount
            verify(diamonds, never()).setAmount(anyInt());
        }

        @Test
        @DisplayName("vendre une partie d'un stack decremente le stack au lieu de vider le slot")
        void sell_partialAmount_decrementsStack() throws Exception {
            ItemStack toSell = stack("DIAMOND", 4);
            ItemStack inSlot = placeInSlot(SLOT, stack("DIAMOND", 10));
            when(inSlot.isSimilar(toSell)).thenReturn(true);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, toSell));

            assertTrue(result.isSuccess(), result.getMessage());
            verify(inSlot).setAmount(6);
            verify(inventory, never()).setItem(anyInt(), any());
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", "LISTED"));
        }

        @Test
        @DisplayName("MAIN_HAND_SLOT lit et vide la main principale")
        void sell_mainHandSlot_clearsMainHand() throws Exception {
            ItemStack diamonds = stack("DIAMOND", 5);
            when(inventory.getItemInMainHand()).thenReturn(diamonds);

            SellResult result = sell(new BigDecimal("100"), Map.of(AuctionSellService.MAIN_HAND_SLOT, diamonds));

            assertTrue(result.isSuccess(), result.getMessage());
            verify(inventory).setItemInMainHand(null);
            verify(inventory, never()).setItem(anyInt(), any());
            verify(inventory, never()).getItem(anyInt());
            assertEquals("LISTED", storageTypeOf(singleItemId()));
        }

        @Test
        @DisplayName("les slots nuls ou en air sont ignores, seuls les stacks valides sont vendus")
        void sell_ignoresNullAndAirSlots() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            Map<Integer, ItemStack> slots = new HashMap<>();
            slots.put(SLOT, diamonds);
            slots.put(OTHER_SLOT, air());
            slots.put(11, null);

            SellResult result = sell(new BigDecimal("100"), slots);

            assertTrue(result.isSuccess(), result.getMessage());
            int id = singleItemId();
            assertEquals(List.of(payloadOf(diamonds)), payloadsOf(id));
            verify(inventory).setItem(SLOT, null);
            verify(inventory, never()).setItem(eq(OTHER_SLOT), any());
            verify(inventory, never()).setItem(eq(11), any());
        }

        @Test
        @DisplayName("un redemarrage apres la vente conserve l'annonce LISTED (elle n'est pas une reservation orpheline)")
        void sell_thenRestart_keepsListing() throws Exception {
            sellOne(new BigDecimal("100"));
            int id = singleItemId();

            restartStorageManager();

            assertEquals("LISTED", storageTypeOf(id));
            assertEquals(0, pendingOf(id));
            assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", id));
        }

        @Test
        @DisplayName("l'evenement Pre peut modifier le prix, l'expiration et la quantite d'une vente mono-stack")
        void sell_preEventMutations_areHonoured() throws Exception {
            long mutatedExpiredAt = expiredAt + 60_000L;
            doAnswer(invocation -> {
                if (invocation.getArgument(0) instanceof AuctionPreSellEvent pre) {
                    pre.setPrice(new BigDecimal("250"));
                    pre.setExpiredAt(mutatedExpiredAt);
                    pre.setAmount(2);
                }
                return null;
            }).when(pluginManager).callEvent(any());
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertTrue(result.isSuccess(), result.getMessage());
            int id = singleItemId();
            assertEquals(0, new BigDecimal("250").compareTo(priceOf(id)));
            assertEquals(mutatedExpiredAt, items().select(id).orElseThrow().expired_at().getTime());
            // le stack representatif (clone = lui-meme ici) recoit la quantite mutee
            verify(diamonds).setAmount(2);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Refus AVANT toute ecriture
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("refus avant toute ecriture")
    class RefusedBeforeAnyWrite {

        @Test
        @DisplayName("plugin en cours d'arret : INTERNAL_ERROR, rien en base, aucun evenement")
        void sell_whileShuttingDown_isRefused() throws Exception {
            when(plugin.isShuttingDown()).thenReturn(true);

            SellResult result = sellOne(new BigDecimal("100"));

            assertFalse(result.isSuccess());
            assertEquals(SellFailReason.INTERNAL_ERROR, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertTrue(firedEvents().isEmpty());
            assertEquals(List.of(Message.SERVER_SHUTTING_DOWN), service.messages);
        }

        @Test
        @DisplayName("aucun item vendable (air seulement) : INVALID_ITEM sans evenement Pre")
        void sell_onlyAir_isInvalidWithoutEvent() throws Exception {
            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, air()));

            assertEquals(SellFailReason.INVALID_ITEM, result.getFailReason());
            assertNothingStored();
            assertTrue(firedEvents().isEmpty(), "rien de vendable : pas d'AuctionPreSellEvent");
            assertEquals(List.of(Message.SELL_ERROR_AIR), service.messages);
        }

        @Test
        @DisplayName("map de slots vide : INVALID_ITEM")
        void sell_emptySlots_isInvalid() throws Exception {
            SellResult result = sell(new BigDecimal("100"), Map.of());

            assertEquals(SellFailReason.INVALID_ITEM, result.getFailReason());
            assertNothingStored();
        }

        @Test
        @DisplayName("evenement Pre annule : EVENT_CANCELLED, aucune reservation creee, items intacts")
        void sell_preEventCancelled_createsNothing() throws Exception {
            doAnswer(invocation -> {
                if (invocation.getArgument(0) instanceof Cancellable cancellable) cancellable.setCancelled(true);
                return null;
            }).when(pluginManager).callEvent(any());

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.EVENT_CANCELLED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(1, firedEvents().size(), "seul le Pre a ete tire");
            assertTrue(service.messages.isEmpty());
            verify(economy, never()).withdrawChecked(any(), any(), anyString());
        }

        @ParameterizedTest(name = "prix {0} -> {1}")
        @MethodSource("fr.maxlego08.zauctionhouse.services.SellServiceTest#invalidPrices")
        @DisplayName("prix invalide : refus de validation sans INSERT")
        void sell_invalidPrice_isRefused(String price, SellFailReason expected, Message expectedMessage) throws Exception {
            SellResult result = sellOne(new BigDecimal(price));

            assertEquals(expected, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(expectedMessage), service.messages);
        }

        @Test
        @DisplayName("prix decimal refuse quand la configuration l'interdit, accepte sinon")
        void sell_decimalPrice_dependsOnConfiguration() throws Exception {
            when(configuration.isAllowDecimalPrices()).thenReturn(false);

            SellResult refused = sellOne(new BigDecimal("10.50"));
            assertEquals(SellFailReason.PRICE_DECIMAL_NOT_ALLOWED, refused.getFailReason());
            assertEquals(List.of(Message.PRICE_DECIMAL_NOT_ALLOWED), service.messages);
            assertNothingStored();

            // "10.00" n'a pas de partie decimale significative : accepte meme sans decimales
            SellResult accepted = sellOne(new BigDecimal("10.00"));
            assertTrue(accepted.isSuccess(), accepted.getMessage());
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", "LISTED"));
        }

        @Test
        @DisplayName("limite d'annonces atteinte : LISTING_LIMIT_REACHED")
        void sell_listingLimitReached_isRefused() throws Exception {
            List<Item> selling = List.of(mock(Item.class), mock(Item.class), mock(Item.class), mock(Item.class), mock(Item.class));
            when(manager.getPlayerSellingItems(player)).thenReturn(selling);

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.LISTING_LIMIT_REACHED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(Message.LISTED_ITEMS_LIMIT), service.messages);
        }

        @Test
        @DisplayName("sous la limite d'annonces, la vente passe")
        void sell_belowListingLimit_isAccepted() throws Exception {
            when(manager.getPlayerSellingItems(player)).thenReturn(List.of(mock(Item.class), mock(Item.class), mock(Item.class), mock(Item.class)));

            SellResult result = sellOne(new BigDecimal("100"));

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", "LISTED"));
        }

        @Test
        @DisplayName("sans permission de vente, la limite vaut 0 et la premiere vente est refusee")
        void sell_withoutSellPermission_limitIsZero() throws Exception {
            when(player.hasPermission(SELL_PERMISSION)).thenReturn(false);

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.LISTING_LIMIT_REACHED, result.getFailReason());
            assertNothingStored();
        }

        @Test
        @DisplayName("monde interdit : WORLD_RESTRICTED")
        void sell_inBannedWorld_isRefused() throws Exception {
            when(world.getName()).thenReturn(BANNED_WORLD);

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.WORLD_RESTRICTED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(Message.WORLD_BANNED), service.messages);
        }

        @Test
        @DisplayName("item blackliste : BLACKLISTED")
        void sell_blacklistedItem_isRefused() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            when(ruleManager.isBlacklistEnabled()).thenReturn(true);
            when(ruleManager.isBlacklisted(diamonds)).thenReturn(true);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.BLACKLISTED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(Message.ITEM_BLACKLISTED), service.messages);
        }

        @Test
        @DisplayName("blacklist desactivee : un item qui matche la blacklist est quand meme vendu")
        void sell_blacklistDisabled_ignoresRules() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            when(ruleManager.isBlacklistEnabled()).thenReturn(false);
            when(ruleManager.isBlacklisted(diamonds)).thenReturn(true);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertTrue(result.isSuccess(), result.getMessage());
            verify(ruleManager, never()).isBlacklisted(any());
        }

        @Test
        @DisplayName("whitelist active et item absent : NOT_WHITELISTED")
        void sell_notWhitelistedItem_isRefused() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            when(ruleManager.isWhitelistEnabled()).thenReturn(true);
            when(ruleManager.isWhitelisted(diamonds)).thenReturn(false);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.NOT_WHITELISTED, result.getFailReason());
            assertNothingStored();
            assertEquals(List.of(Message.ITEM_WHITELISTED), service.messages);
        }

        @Test
        @DisplayName("multi-stack : un seul stack blackliste refuse tout le lot")
        void sell_multiStackWithOneBlacklisted_refusesWholeLot() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            ItemStack bedrock = placeInSlot(OTHER_SLOT, stack("BEDROCK", 1));
            when(ruleManager.isBlacklistEnabled()).thenReturn(true);
            when(ruleManager.isBlacklisted(bedrock)).thenReturn(true);
            Map<Integer, ItemStack> slots = new LinkedHashMap<>();
            slots.put(SLOT, diamonds);
            slots.put(OTHER_SLOT, bedrock);

            SellResult result = sell(new BigDecimal("100"), slots);

            assertEquals(SellFailReason.BLACKLISTED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
        }

        @Test
        @DisplayName("encodage qui rend null : INVALID_ITEM, aucune reservation n'est meme creee (C-067)")
        void sell_encodeReturnsNull_refusesBeforeReservation() throws Exception {
            base64.when(() -> Base64ItemStack.encode(any())).thenReturn(null);

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.INVALID_ITEM, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(Message.SELL_ERROR_INVALID_ITEM), service.messages);
            assertNoListedEvent();
        }

        @Test
        @DisplayName("encodage qui rend une chaine vide : meme refus que null")
        void sell_encodeReturnsEmpty_refusesBeforeReservation() throws Exception {
            base64.when(() -> Base64ItemStack.encode(any())).thenReturn("");

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.INVALID_ITEM, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
        }

        @Test
        @DisplayName("encodage qui leve (NPE NMS) : INVALID_ITEM gere, rien en base")
        void sell_encodeThrows_isHandled() throws Exception {
            base64.when(() -> Base64ItemStack.encode(any())).thenThrow(new NullPointerException("nms"));

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.INVALID_ITEM, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
        }

        @Test
        @DisplayName("multi-stack : un seul stack non serialisable refuse tout le lot")
        void sell_multiStackWithOneUnencodable_refusesWholeLot() throws Exception {
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            ItemStack broken = placeInSlot(OTHER_SLOT, stack("BROKEN", 1));
            base64.when(() -> Base64ItemStack.encode(broken)).thenReturn(null);
            Map<Integer, ItemStack> slots = new LinkedHashMap<>();
            slots.put(SLOT, diamonds);
            slots.put(OTHER_SLOT, broken);

            SellResult result = sell(new BigDecimal("100"), slots);

            assertEquals(SellFailReason.INVALID_ITEM, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
        }

        @Test
        @DisplayName("le slot ne contient plus l'item avant la reservation : ITEMS_CHANGED, rien en base")
        void sell_slotEmptyBeforeReservation_isRefused() throws Exception {
            ItemStack diamonds = stack("DIAMOND", 5);
            when(inventory.getItem(SLOT)).thenReturn(null);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.ITEMS_CHANGED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(Message.SELL_ERROR_CHANGE), service.messages);
        }

        @Test
        @DisplayName("le slot contient moins d'items qu'annonce : ITEMS_CHANGED")
        void sell_slotAmountTooLow_isRefused() throws Exception {
            ItemStack toSell = stack("DIAMOND", 5);
            ItemStack inSlot = placeInSlot(SLOT, stack("DIAMOND", 2));
            when(inSlot.isSimilar(toSell)).thenReturn(true);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, toSell));

            assertEquals(SellFailReason.ITEMS_CHANGED, result.getFailReason());
            assertNothingStored();
        }

        @Test
        @DisplayName("le slot contient un item different : ITEMS_CHANGED")
        void sell_slotHoldsDifferentItem_isRefused() throws Exception {
            ItemStack toSell = stack("DIAMOND", 5);
            placeInSlot(SLOT, stack("DIRT", 5)); // isSimilar(toSell) n'est pas stubbe : false

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, toSell));

            assertEquals(SellFailReason.ITEMS_CHANGED, result.getFailReason());
            assertNothingStored();
        }
    }

    static Stream<Arguments> invalidPrices() {
        return Stream.of(
                Arguments.of("0", SellFailReason.PRICE_TOO_LOW, Message.PRICE_TOO_LOW),
                Arguments.of("-5", SellFailReason.PRICE_TOO_LOW, Message.PRICE_TOO_LOW),
                Arguments.of("0.5", SellFailReason.PRICE_TOO_LOW, Message.PRICE_TOO_LOW),
                Arguments.of("1000000.01", SellFailReason.PRICE_TOO_HIGH, Message.PRICE_TOO_HIGH)
        );
    }

    // ------------------------------------------------------------------------------------------
    // Reservation posee puis abandonnee AVANT le jalon : la ligne doit etre SUPPRIMEE
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("reservation abandonnee avant le jalon de vente")
    class ReservationCancelled {

        @Test
        @DisplayName("items modifies entre la reservation et le retrait : reservation supprimee avec ses contenus, items intacts")
        void sell_itemsChangedAfterReservation_dropsReservation() throws Exception {
            ItemStack diamonds = stack("DIAMOND", 5);
            // 1er verifyItemsInSlots (avant reservation) : present ; 2e (sur le thread du joueur) : parti
            when(inventory.getItem(SLOT)).thenReturn(diamonds, (ItemStack) null);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.ITEMS_CHANGED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(Message.SELL_ERROR_CHANGE), service.messages);
            assertNoListedEvent();
            verify(manager, never()).addItem(any(), any());
        }

        @Test
        @DisplayName("items modifies apres reservation avec taxe : la taxe prelevee est remboursee")
        void sell_itemsChangedAfterReservation_refundsTax() throws Exception {
            useTax(sellTax(10));
            ItemStack diamonds = stack("DIAMOND", 5);
            when(inventory.getItem(SLOT)).thenReturn(diamonds, (ItemStack) null);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.ITEMS_CHANGED, result.getFailReason());
            verify(economy).withdrawChecked(sellerId, new BigDecimal("10.00"), SELL_TAX_REASON);
            verify(economy).depositChecked(sellerId, new BigDecimal("10.00"), "Refund sell tax (items changed)");
            assertNothingStored();
        }

        @Test
        @DisplayName("joueur deconnecte avant le retrait : PLAYER_DISCONNECTED, reservation supprimee, aucun message")
        void sell_playerOfflineBeforeRemoval_dropsReservation() throws Exception {
            online.set(false);

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.PLAYER_DISCONNECTED, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertTrue(service.messages.isEmpty(), "aucun message a un joueur hors ligne");
            assertNoListedEvent();
        }

        @Test
        @DisplayName("echec de la reservation en base : DATABASE_ERROR, taxe remboursee, items intacts")
        void sell_reservationFails_refundsTaxAndKeepsItems() throws Exception {
            useTax(sellTax(10));
            ZStorageManager spied = spy(storageManager);
            doReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")))
                    .when(spied).reserveAuctionItem(any(), any(), anyLong(), any(), any(), any());
            when(plugin.getStorageManager()).thenReturn(spied);

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            verify(economy).depositChecked(sellerId, new BigDecimal("10.00"), "Refund sell tax (reservation failed)");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Jalon de vente (pending_publish 1 -> 2) perdu
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("jalon de vente compromis")
    class CommitMilestone {

        @Test
        @DisplayName("compare-and-set perdu (la ligne n'est plus une reservation) : lot rendu en main propre, vente annulee")
        void sell_commitCasLost_handsItemsBack() throws Exception {
            ItemStack diamonds = stack("DIAMOND", 5);
            // 2e lecture du slot = juste avant le retrait : un autre acteur a deja fait evoluer la ligne
            when(inventory.getItem(SLOT)).thenReturn(diamonds).thenAnswer(invocation -> {
                execute("UPDATE " + Tables.ITEMS + " SET pending_publish = 0 WHERE storage_type = ? AND pending_publish = ?", "DELETED", 1);
                return diamonds;
            });

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            // le lot a ete retire PUIS rendu, sur le meme thread
            InOrder inOrder = inOrder(inventory);
            inOrder.verify(inventory).setItem(SLOT, null);
            inOrder.verify(inventory).addItem(diamonds);
            // la ligne ne porte plus le jalon 1 : cancelReservation la laisse telle quelle, elle reste invisible
            assertEquals(0, count(Tables.ITEMS, "storage_type = ?", "LISTED"));
            assertEquals(1, count(Tables.ITEMS, "storage_type = ? AND pending_publish = ?", "DELETED", 0));
            assertNoListedEvent();
            verify(manager, never()).addItem(any(), any());
        }

        @Test
        @DisplayName("compare-and-set perdu avec taxe : la taxe est remboursee")
        void sell_commitCasLost_refundsTax() throws Exception {
            useTax(sellTax(10));
            ItemStack diamonds = stack("DIAMOND", 5);
            when(inventory.getItem(SLOT)).thenReturn(diamonds).thenAnswer(invocation -> {
                execute("DELETE FROM " + Tables.ITEMS + " WHERE storage_type = ? AND pending_publish = ?", "DELETED", 1);
                return diamonds;
            });

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            verify(inventory).addItem(diamonds);
            verify(economy).depositChecked(sellerId, new BigDecimal("10.00"), "Refund sell tax (sale milestone refused)");
            assertEquals(0, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("base muette au moment du jalon : le lot N'EST PAS rendu et la reservation reste intacte (pending 1)")
        void sell_commitUnknown_keepsReservationAndDoesNotHandBack() throws Exception {
            ItemStack diamonds = stack("DIAMOND", 5);
            // la table disparait juste avant le jalon : l'UPDATE leve, le verdict est UNKNOWN
            // Le slot est relu une 3e fois par removeItemsFromSlots : le renommage ne doit avoir lieu qu'une fois.
            AtomicBoolean renamed = new AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicReference<Throwable> hookError = new java.util.concurrent.atomic.AtomicReference<>();
            when(inventory.getItem(SLOT)).thenReturn(diamonds).thenAnswer(invocation -> {
                try {
                    if (renamed.compareAndSet(false, true)) {
                        execute("ALTER TABLE " + Tables.ITEMS + " RENAME TO " + Tables.ITEMS + "_hidden");
                    }
                } catch (Throwable throwable) {
                    hookError.set(throwable);
                    throw throwable;
                }
                return diamonds;
            });

            SellResult result;
            try {
                CompletableFuture<SellResult> future = service.sellAuctionItems(player, new BigDecimal("100"), expiredAt, Map.of(SLOT, diamonds), economy);
                assertNull(hookError.get(), "le renommage de la table a echoue : " + hookError.get());
                assertTrue(future.isDone(), "toute la chaine de vente doit etre synchrone dans ce montage");
                result = future.join();
            } finally {
                execute("ALTER TABLE " + Tables.ITEMS + "_hidden RENAME TO " + Tables.ITEMS);
            }

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            verify(inventory).setItem(SLOT, null);
            verify(inventory, never()).addItem(any(ItemStack.class));
            // la ligne est exactement dans l'etat laisse par la reservation : le demarrage tranchera
            int id = singleItemId();
            assertEquals("DELETED", storageTypeOf(id));
            assertEquals(1, pendingOf(id));
            assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", id));
            assertNoListedEvent();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Publication perdue APRES le jalon : le lot doit exister a UN exemplaire
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("publication perdue apres le jalon de vente")
    class PublishLost {

        @ParameterizedTest(name = "publication : {0}")
        @MethodSource("fr.maxlego08.zauctionhouse.services.SellServiceTest#publishFailures")
        @DisplayName("vendeur en ligne : jalon recule, lot rendu en main propre, reservation supprimee")
        void sell_publishLost_playerOnline_handsItemsBack(String label, CompletableFuture<Integer> publishResult) throws Exception {
            useTax(sellTax(10));
            failPublishWith(publishResult);
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            InOrder inOrder = inOrder(inventory);
            inOrder.verify(inventory).setItem(SLOT, null);
            inOrder.verify(inventory).addItem(diamonds);
            // deleteReservation exige pending 1 : la suppression prouve que le jalon a ete recule AVANT
            assertNothingStored();
            verify(economy).depositChecked(sellerId, new BigDecimal("10.00"), "Refund sell tax (sale failed)");
            assertNoListedEvent();
            verify(manager, never()).addItem(any(), any());
        }

        @Test
        @DisplayName("vendeur deconnecte apres le jalon : la reservation devient RECLAMABLE (EXPIRED, sans expiration), lot non rendu, taxe non remboursee")
        void sell_publishLost_playerOffline_makesReservationClaimable() throws Exception {
            useTax(sellTax(10));
            failPublishWith(CompletableFuture.completedFuture(0));
            // saveData est appele juste apres le jalon et avant la publication : le joueur part a cet instant
            when(configuration.isSaveProfileOnSell()).thenReturn(true);
            doAnswer(invocation -> {
                online.set(false);
                return null;
            }).when(player).saveData();
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            int id = singleItemId();
            assertEquals("EXPIRED", storageTypeOf(id));
            assertEquals(0, pendingOf(id));
            ItemDTO dto = items().select(id).orElseThrow();
            assertEquals(StorageType.EXPIRED, dto.storage_type());
            assertEquals(0L, dto.expired_at().getTime(), "0 = reclamable sans limite de temps");
            assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", id), "les contenus sont conserves");
            verify(inventory).setItem(SLOT, null);
            verify(inventory, never()).addItem(any(ItemStack.class));
            verify(economy, never()).depositChecked(any(), any(), anyString());
            assertNoListedEvent();
        }

        @Test
        @DisplayName("tache de region jamais executee (entite retiree) : la reservation devient RECLAMABLE")
        void sell_publishLost_entityRetired_makesReservationClaimable() throws Exception {
            failPublishWith(CompletableFuture.completedFuture(0));
            when(configuration.isSaveProfileOnSell()).thenReturn(true);
            doAnswer(invocation -> {
                entityTasksRun.set(false);
                return null;
            }).when(player).saveData();
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            int id = singleItemId();
            assertEquals("EXPIRED", storageTypeOf(id));
            assertEquals(0, pendingOf(id));
            assertEquals(0L, items().select(id).orElseThrow().expired_at().getTime());
            verify(inventory, never()).addItem(any(ItemStack.class));
        }

        @Test
        @DisplayName("publication ET recul du jalon perdus (ligne deja modifiee ailleurs) : rien n'est rendu, ligne laissee telle quelle")
        void sell_publishLost_uncommitLost_leavesRowAlone() throws Exception {
            when(configuration.isSaveProfileOnSell()).thenReturn(true);
            // entre le jalon et la publication, un autre acteur retire le jalon : plus rien ne matche
            doAnswer(invocation -> {
                execute("UPDATE " + Tables.ITEMS + " SET pending_publish = 0 WHERE pending_publish = ?", 2);
                return null;
            }).when(player).saveData();
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.DATABASE_ERROR, result.getFailReason());
            int id = singleItemId();
            assertEquals("DELETED", storageTypeOf(id));
            assertEquals(0, pendingOf(id));
            assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", id));
            verify(inventory).setItem(SLOT, null);
            verify(inventory, never()).addItem(any(ItemStack.class));
            assertNoListedEvent();
        }

        @Test
        @DisplayName("publication perdue : saveData du vendeur est force apres la restitution quand la configuration le demande")
        void sell_publishLost_savesProfileAfterHandBack() throws Exception {
            failPublishWith(CompletableFuture.completedFuture(0));
            when(configuration.isSaveProfileOnSell()).thenReturn(true);
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            // une fois apres le jalon, une fois apres la restitution
            verify(player, times(2)).saveData();
            assertNothingStored();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Taxe de vente
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("taxe de vente")
    class SellTax {

        @Test
        @DisplayName("taxe de 10 % : 10.00 est preleve avant la reservation, journalise et porte par l'evenement Post")
        void sell_withTax_withdrawsAndReports() throws Exception {
            useTax(sellTax(10));
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertTrue(result.isSuccess(), result.getMessage());
            verify(economy).withdrawChecked(sellerId, new BigDecimal("10.00"), SELL_TAX_REASON);
            verify(economy, never()).depositChecked(any(), any(), anyString());

            int id = singleItemId();
            assertEquals("LISTED", storageTypeOf(id));
            assertEquals(0, new BigDecimal("100").compareTo(priceOf(id)), "le prix affiche reste le prix demande");
            assertEquals("added_auction_item_to_listed;sell_tax=10.00",
                    queryString("SELECT additional_data FROM " + Tables.LOGS + " WHERE item_id = ?", id));

            AuctionItemListedEvent post = assertInstanceOf(AuctionItemListedEvent.class, firedEvents().get(1));
            assertNotNull(post.getSellTax());
            assertEquals(new BigDecimal("10.00"), post.getSellTax().taxAmount());
            assertEquals(TaxType.SELL, post.getSellTax().appliedType());

            assertEquals(List.of(Message.TAX_SELL_APPLIED, Message.ITEM_SOLD), service.messages);
        }

        @Test
        @DisplayName("fonds insuffisants pour la taxe : INSUFFICIENT_FUNDS_FOR_TAX, rien en base, items intacts")
        void sell_insufficientFundsForTax_isRefused() throws Exception {
            useTax(sellTax(10));
            when(economy.withdrawChecked(any(), any(), anyString())).thenReturn(false);

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.INSUFFICIENT_FUNDS_FOR_TAX, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            assertEquals(List.of(Message.TAX_INSUFFICIENT_FUNDS), service.messages);
            assertNoListedEvent();
        }

        @Test
        @DisplayName("joueur exempte : aucun retrait, message TAX_EXEMPT, Post sans TaxResult")
        void sell_taxBypassed_doesNotWithdraw() throws Exception {
            useTax(new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, "zah.tax.bypass", List.of(), false, List.of()));
            when(player.hasPermission("zah.tax.bypass")).thenReturn(true);

            SellResult result = sellOne(new BigDecimal("100"));

            assertTrue(result.isSuccess(), result.getMessage());
            verify(economy, never()).withdrawChecked(any(), any(), anyString());
            assertEquals(List.of(Message.TAX_EXEMPT, Message.ITEM_SOLD), service.messages);
            AuctionItemListedEvent post = assertInstanceOf(AuctionItemListedEvent.class, firedEvents().get(1));
            assertNull(post.getSellTax());
            assertEquals("added_auction_item_to_listed",
                    queryString("SELECT additional_data FROM " + Tables.LOGS + " WHERE item_id = ?", singleItemId()));
        }

        @Test
        @DisplayName("taxe de type PURCHASE : rien n'est preleve a la mise en vente")
        void sell_purchaseOnlyTax_doesNotWithdraw() throws Exception {
            useTax(new ZTaxConfiguration(true, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 10, null, List.of(), false, List.of()));

            SellResult result = sellOne(new BigDecimal("100"));

            assertTrue(result.isSuccess(), result.getMessage());
            verify(economy, never()).withdrawChecked(any(), any(), anyString());
            assertEquals(List.of(Message.ITEM_SOLD), service.messages);
        }

        @Test
        @DisplayName("taxe BOTH : prelevee a la mise en vente")
        void sell_bothTax_withdraws() throws Exception {
            useTax(new ZTaxConfiguration(true, TaxType.BOTH, TaxAmountType.FIXED, 7, null, List.of(), false, List.of()));

            SellResult result = sellOne(new BigDecimal("100"));

            assertTrue(result.isSuccess(), result.getMessage());
            verify(economy).withdrawChecked(sellerId, new BigDecimal("7.00"), SELL_TAX_REASON);
        }

        @Test
        @DisplayName("multi-stack : la taxe la plus elevee parmi les regles par item est appliquee")
        void sell_multiStack_appliesHighestItemTax() throws Exception {
            Rule goldRule = mock(Rule.class);
            ItemStack diamonds = placeInSlot(SLOT, stack("DIAMOND", 5));
            ItemStack gold = placeInSlot(OTHER_SLOT, stack("GOLD_INGOT", 2));
            Material goldMaterial = gold.getType();
            when(goldRule.matches(any())).thenAnswer(invocation -> ((ItemRuleContext) invocation.getArgument(0)).getMaterial() == goldMaterial);
            ZItemTaxRule itemRule = new ZItemTaxRule("gold", 10, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, goldRule);
            useTax(new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(itemRule)));
            Map<Integer, ItemStack> slots = new LinkedHashMap<>();
            slots.put(SLOT, diamonds);
            slots.put(OTHER_SLOT, gold);

            SellResult result = sell(new BigDecimal("100"), slots);

            assertTrue(result.isSuccess(), result.getMessage());
            verify(economy).withdrawChecked(sellerId, new BigDecimal("20.00"), SELL_TAX_REASON);
            verify(economy, times(1)).withdrawChecked(any(), any(), anyString());
        }

        @Test
        @DisplayName("calcul de taxe qui leve : TAX_ERROR, rien en base, items intacts")
        void sell_taxCalculationThrows_isTaxError() throws Exception {
            useTax(sellTax(10));
            // doThrow : when(...) invoquerait le stub precedent (delegation a la configuration) avec des null
            doThrow(new RuntimeException("tax provider down")).when(economy).calculateSellTax(any(), any(), any());

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.TAX_ERROR, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
            verify(economy, never()).withdrawChecked(any(), any(), anyString());
        }

        @Test
        @DisplayName("retrait de taxe qui leve : TAX_ERROR gere")
        void sell_taxWithdrawThrows_isTaxError() throws Exception {
            useTax(sellTax(10));
            when(economy.withdrawChecked(any(), any(), anyString())).thenThrow(new IllegalStateException("vault down"));

            SellResult result = sellOne(new BigDecimal("100"));

            assertEquals(SellFailReason.TAX_ERROR, result.getFailReason());
            assertNothingStored();
            assertInventoryUntouched();
        }

        @Test
        @DisplayName("remboursement qui echoue : la restitution des items n'est pas interrompue")
        void sell_refundFails_stillHandsItemsBack() throws Exception {
            useTax(sellTax(10));
            when(economy.depositChecked(any(), any(), anyString())).thenThrow(new IllegalStateException("vault down"));
            ItemStack diamonds = stack("DIAMOND", 5);
            when(inventory.getItem(SLOT)).thenReturn(diamonds, (ItemStack) null);

            SellResult result = sell(new BigDecimal("100"), Map.of(SLOT, diamonds));

            assertEquals(SellFailReason.ITEMS_CHANGED, result.getFailReason());
            assertNothingStored();
            verify(economy).depositChecked(sellerId, new BigDecimal("10.00"), "Refund sell tax (items changed)");
        }
    }

    static Stream<Arguments> publishFailures() {
        return Stream.of(
                Arguments.of("0 ligne (compare-and-set perdu)", CompletableFuture.completedFuture(0)),
                Arguments.of("future en erreur (base injoignable)", CompletableFuture.<Integer>failedFuture(new IllegalStateException("db down")))
        );
    }

    // ------------------------------------------------------------------------------------------
    // openSellCommandInventory
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("openSellCommandInventory")
    class OpenSellInventory {

        private PlayerCache cache;
        private InventoriesLoader inventoriesLoader;

        @BeforeEach
        void setUpInventory() {
            this.cache = mock(PlayerCache.class);
            this.inventoriesLoader = mock(InventoriesLoader.class);
            when(manager.getCache(player)).thenReturn(this.cache);
            when(plugin.getInventoriesLoader()).thenReturn(this.inventoriesLoader);
        }

        @Test
        @DisplayName("prepare le cache du joueur (prix, economie, expiration, quantite) et ouvre l'inventaire de vente")
        void open_fillsCacheAndOpensInventory() {
            when(configuration.getSellExpiration()).thenReturn(new ExpirationConfiguration(3600L, false, Collections.emptyMap()));
            long before = System.currentTimeMillis();

            service.openSellCommandInventory(player, new BigDecimal("42"), economy, 3);

            verify(cache).set(PlayerCacheKey.SELL_PRICE, new BigDecimal("42"));
            verify(cache).set(PlayerCacheKey.SELL_ECONOMY, economy);
            verify(cache).set(PlayerCacheKey.SELL_AMOUNT, 3);
            ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
            verify(cache).set(eq(PlayerCacheKey.SELL_EXPIRED_AT), captor.capture());
            long expiredAtValue = captor.getValue();
            assertTrue(expiredAtValue >= before + 3_600_000L && expiredAtValue <= System.currentTimeMillis() + 3_600_000L,
                    "expiration = maintenant + 3600 s, obtenu " + expiredAtValue);
            verify(cache).remove(PlayerCacheKey.SELL_ITEMS);
            verify(inventoriesLoader).openInventory(player, Inventories.SELL_INVENTORY);
        }

        @Test
        @DisplayName("expiration 0 => jamais d'expiration ; quantite negative => 0 (stack entier)")
        void open_zeroExpirationAndNegativeAmount() {
            when(configuration.getSellExpiration()).thenReturn(new ExpirationConfiguration(0L, false, Collections.emptyMap()));

            service.openSellCommandInventory(player, BigDecimal.TEN, economy, -3);

            verify(cache).set(PlayerCacheKey.SELL_EXPIRED_AT, 0L);
            verify(cache).set(PlayerCacheKey.SELL_AMOUNT, 0);
            verify(inventoriesLoader).openInventory(player, Inventories.SELL_INVENTORY);
        }

        @Test
        @DisplayName("l'expiration par permission la plus haute est retenue")
        void open_usesPermissionExpiration() {
            when(configuration.getSellExpiration()).thenReturn(new ExpirationConfiguration(100L, true, Map.of("zah.vip", 500L, "zah.mvp", 300L)));
            when(player.hasPermission("zah.vip")).thenReturn(true);
            long before = System.currentTimeMillis();

            service.openSellCommandInventory(player, BigDecimal.TEN, economy, 0);

            ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
            verify(cache).set(eq(PlayerCacheKey.SELL_EXPIRED_AT), captor.capture());
            assertTrue(captor.getValue() >= before + 500_000L, "500 s attendues, obtenu " + captor.getValue());
        }
    }
}
