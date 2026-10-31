package fr.maxlego08.zauctionhouse.loader;

import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.exceptions.InventoryException;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.menu.api.loader.PermissibleLoader;
import fr.maxlego08.menu.api.pattern.PatternManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZInventoriesLoaderTest {

    /** Nombre de ButtonLoader enregistres par loadButtons() dans le code actuel. */
    private static final int REGISTERED_LOADERS = 51;

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private ButtonManager buttonManager;
    private InventoryManager inventoryManager;
    private PatternManager patternManager;
    private ZInventoriesLoader loader;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.buttonManager = mock(ButtonManager.class);
        this.inventoryManager = mock(InventoryManager.class);
        this.patternManager = mock(PatternManager.class);

        Server server = mock(Server.class);
        ServicesManager servicesManager = mock(ServicesManager.class);
        RegisteredServiceProvider<ButtonManager> buttonRegistration = mock(RegisteredServiceProvider.class);
        RegisteredServiceProvider<InventoryManager> inventoryRegistration = mock(RegisteredServiceProvider.class);
        RegisteredServiceProvider<PatternManager> patternRegistration = mock(RegisteredServiceProvider.class);
        when(buttonRegistration.getProvider()).thenReturn(this.buttonManager);
        when(inventoryRegistration.getProvider()).thenReturn(this.inventoryManager);
        when(patternRegistration.getProvider()).thenReturn(this.patternManager);
        when(servicesManager.getRegistration(ButtonManager.class)).thenReturn(buttonRegistration);
        when(servicesManager.getRegistration(InventoryManager.class)).thenReturn(inventoryRegistration);
        when(servicesManager.getRegistration(PatternManager.class)).thenReturn(patternRegistration);
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(this.plugin.getServer()).thenReturn(server);
        lenient().when(this.plugin.getName()).thenReturn("zAuctionHouse");
        lenient().when(this.plugin.isEnabled()).thenReturn(true);
        lenient().when(this.buttonManager.getLoaders()).thenReturn(List.of());

        this.loader = new ZInventoriesLoader(this.plugin);
    }

    private static ButtonLoader loaderOwnedBy(Plugin owner) {
        ButtonLoader buttonLoader = mock(ButtonLoader.class);
        when(buttonLoader.getPlugin()).thenReturn(owner);
        return buttonLoader;
    }

    private static Plugin otherPlugin(String name, boolean enabled) {
        Plugin other = mock(Plugin.class);
        lenient().when(other.getName()).thenReturn(name);
        lenient().when(other.isEnabled()).thenReturn(enabled);
        return other;
    }

    @Nested
    @DisplayName("Constructeur")
    class ConstructorTests {

        @Test
        @DisplayName("les trois managers zMenu sont resolus via le ServicesManager")
        void providers_resolved() {
            assertSame(buttonManager, loader.getButtonManager());
            assertSame(inventoryManager, loader.getInventoryManager());
        }

        @Test
        @DisplayName("provider absent -> null, pas d'exception")
        void missingProvider_null() {
            when(plugin.getServer().getServicesManager().getRegistration(ButtonManager.class)).thenReturn(null);

            ZInventoriesLoader degraded = new ZInventoriesLoader(plugin);

            assertTrue(degraded.getButtonManager() == null);
            assertSame(inventoryManager, degraded.getInventoryManager());
        }
    }

    @Nested
    @DisplayName("loadButtons")
    class LoadButtonsTests {

        private List<ButtonLoader> registered() {
            ArgumentCaptor<ButtonLoader> captor = ArgumentCaptor.forClass(ButtonLoader.class);
            verify(buttonManager, times(REGISTERED_LOADERS)).register(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("tous les loaders sont enregistres sous des noms uniques, apres purge des anciens")
        void registersEveryLoaderOnce() {
            loader.loadButtons();

            List<ButtonLoader> loaders = registered();
            Set<String> names = loaders.stream().map(ButtonLoader::getName).collect(Collectors.toSet());
            assertEquals(REGISTERED_LOADERS, names.size(), "duplicate loader names: " + loaders.stream().map(ButtonLoader::getName).toList());
            assertTrue(loaders.stream().allMatch(buttonLoader -> buttonLoader.getPlugin() == plugin));
            verify(buttonManager).unregisters(plugin);
        }

        @Test
        @DisplayName("les noms attendus par les inventaires livres sont presents")
        void expectedNames_present() {
            loader.loadButtons();

            Set<String> names = registered().stream().map(ButtonLoader::getName).collect(Collectors.toSet());
            assertTrue(names.containsAll(Set.of(
                    "ZAUCTIONHOUSE_LISTED_ITEMS", "ZAUCTIONHOUSE_EXPIRED_ITEMS", "ZAUCTIONHOUSE_SELLING_ITEMS", "ZAUCTIONHOUSE_PURCHASED_ITEMS",
                    "ZAUCTIONHOUSE_ITEM_CONTENT", "ZAUCTIONHOUSE_COMBINED_ITEMS", "ZAUCTIONHOUSE_REMOVE_ALL_EXPIRED",
                    "ZAUCTIONHOUSE_EXPIRED_INVENTORY", "ZAUCTIONHOUSE_SELLING_INVENTORY", "ZAUCTIONHOUSE_PURCHASED_INVENTORY", "ZAUCTIONHOUSE_HISTORY_INVENTORY",
                    "ZAUCTIONHOUSE_SHOW", "ZAUCTIONHOUSE_CONFIRM_REMOVE_LISTED", "ZAUCTIONHOUSE_CONFIRM_PURCHASE",
                    "ZAUCTIONHOUSE_SELL_CANCEL", "ZAUCTIONHOUSE_SELL_CONFIRM", "ZAUCTIONHOUSE_SELL_ECONOMY", "ZAUCTIONHOUSE_SELL_SHOW_ITEM",
                    "ZAUCTIONHOUSE_SELL_PRICE", "ZAUCTIONHOUSE_SELL_LIMIT", "ZAUCTIONHOUSE_HISTORY_ITEMS", "ZAUCTIONHOUSE_HISTORY_SORT",
                    "ZAUCTIONHOUSE_ADMIN_LOGS", "ZAUCTIONHOUSE_ADMIN_TRANSACTIONS", "ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_TYPE", "ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_DATE",
                    "ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_STATUS", "ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_DATE",
                    "ZAUCTIONHOUSE_CHANGE_SORT", "ZAUCTIONHOUSE_REFRESH", "ZAUCTIONHOUSE_CATEGORY", "ZAUCTIONHOUSE_CATEGORY_SWITCHER",
                    "ZAUCTIONHOUSE_CLAIM", "ZAUCTIONHOUSE_SEARCH", "ZAUCTIONHOUSE_CLEAR_SEARCH", "ZAUCTIONHOUSE_OPTION_TOGGLE",
                    "ZAUCTIONHOUSE_SHULKER_OPEN", "ZAUCTIONHOUSE_SHULKER_CONTENT", "ZAUCTIONHOUSE_SHULKER_INFO", "ZAUCTIONHOUSE_SHULKER_NAVIGATION")), names.toString());
        }

        @Test
        @DisplayName("le permissible zauctionhouse_category est enregistre")
        void permissible_registered() {
            loader.loadButtons();

            ArgumentCaptor<PermissibleLoader> captor = ArgumentCaptor.forClass(PermissibleLoader.class);
            verify(buttonManager).registerPermissible(captor.capture());
            assertEquals("zauctionhouse_category", captor.getValue().getKey());
        }

        @Test
        @DisplayName("rechargement a chaud : les loaders d'une ancienne instance desactivee du MEME plugin sont purges")
        void hotReload_purgesStaleInstance() {
            Plugin stale = otherPlugin("zAuctionHouse", false);
            // Les mocks sont construits AVANT le when(...) : loaderOwnedBy stubbe lui-meme un mock,
            // ce qui declencherait un UnfinishedStubbingException s'il etait appele dans thenReturn(...).
            List<ButtonLoader> staleLoaders = List.of(loaderOwnedBy(stale), loaderOwnedBy(stale));
            when(buttonManager.getLoaders()).thenReturn(staleLoaders);

            loader.loadButtons();

            verify(buttonManager, times(1)).unregisters(stale);
            verify(inventoryManager, times(1)).deleteInventories(stale);
            InOrder order = inOrder(buttonManager);
            order.verify(buttonManager).unregisters(stale);
            order.verify(buttonManager).unregisters(plugin);
        }

        @Test
        @DisplayName("les loaders d'un autre plugin, ou d'une instance encore active, ne sont pas touches")
        void hotReload_leavesOtherLoaders() {
            Plugin foreign = otherPlugin("zShop", false);
            Plugin activeTwin = otherPlugin("zAuctionHouse", true);
            List<ButtonLoader> existingLoaders = List.of(loaderOwnedBy(foreign), loaderOwnedBy(activeTwin), loaderOwnedBy(plugin));
            when(buttonManager.getLoaders()).thenReturn(existingLoaders);

            loader.loadButtons();

            verify(buttonManager, never()).unregisters(foreign);
            verify(buttonManager, never()).unregisters(activeTwin);
            verify(inventoryManager, never()).deleteInventories(any());
            verify(buttonManager).unregisters(plugin);
        }
    }

    @Nested
    @DisplayName("Fichiers : inventaires et patterns")
    class FilesTests {

        @Test
        @DisplayName("loadInventory delegue a l'InventoryManager")
        void loadInventory_delegates() throws Exception {
            File file = new File(dataFolder, "x.yml");

            loader.loadInventory(file);

            verify(inventoryManager).loadInventory(plugin, file);
        }

        @Test
        @DisplayName("InventoryException est encapsulee dans une RuntimeException")
        void loadInventory_wrapsException() throws Exception {
            File file = new File(dataFolder, "x.yml");
            InventoryException cause = new InventoryException("bad yaml");
            when(inventoryManager.loadInventory(plugin, file)).thenThrow(cause);

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> loader.loadInventory(file));

            assertSame(cause, thrown.getCause());
        }

        @Test
        @DisplayName("loadPattern delegue au PatternManager et encapsule les erreurs")
        void loadPattern_delegatesAndWraps() throws Exception {
            File ok = new File(dataFolder, "ok.yml");
            File bad = new File(dataFolder, "bad.yml");
            when(patternManager.loadPattern(bad)).thenThrow(new InventoryException("bad"));

            loader.loadPattern(ok);
            verify(patternManager).loadPattern(ok);
            assertThrows(RuntimeException.class, () -> loader.loadPattern(bad));
        }

        @Test
        @DisplayName("dossier inventories absent : il est cree et les fichiers par defaut sont extraits")
        void loadInventories_missingFolder_extractsDefaults() {
            loader.loadInventories();

            assertTrue(new File(dataFolder, "inventories").isDirectory());
            verify(plugin).saveFile("inventories/auction.yml", false);
            verify(plugin).saveFile("inventories/admin/admin-logs.yml", false);
            verify(plugin).saveFile("inventories/confirms/purchase-confirm.yml", false);
            verify(plugin).saveFile("inventories/shulker-content.yml", false);
            verify(plugin).saveFile("inventories/options.yml", false);
            verify(plugin, times(20)).saveFile(anyString(), eq(false));
        }

        @Test
        @DisplayName("dossier inventories present : chaque .yml (recursif) est charge, les autres fichiers ignores")
        void loadInventories_existingFolder_loadsEveryYaml() throws Exception {
            File folder = new File(dataFolder, "inventories");
            File admin = new File(folder, "admin");
            admin.mkdirs();
            File auction = new File(folder, "auction.yml");
            File logs = new File(admin, "admin-logs.yml");
            File readme = new File(folder, "readme.txt");
            Files.writeString(auction.toPath(), "size: 54", StandardCharsets.UTF_8);
            Files.writeString(logs.toPath(), "size: 54", StandardCharsets.UTF_8);
            Files.writeString(readme.toPath(), "nope", StandardCharsets.UTF_8);

            loader.loadInventories();

            verify(inventoryManager).loadInventory(plugin, auction);
            verify(inventoryManager).loadInventory(plugin, logs);
            verify(inventoryManager, times(2)).loadInventory(eq(plugin), any(File.class));
            verify(plugin, never()).saveFile(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("dossier patterns absent : cree et les trois patterns par defaut extraits")
        void loadPatterns_missingFolder_extractsDefaults() {
            loader.loadPatterns();

            assertTrue(new File(dataFolder, "patterns").isDirectory());
            verify(plugin).saveFile("patterns/decoration.yml", false);
            verify(plugin).saveFile("patterns/pagination.yml", false);
            verify(plugin).saveFile("patterns/back.yml", false);
            verify(plugin, times(3)).saveFile(anyString(), eq(false));
        }

        @Test
        @DisplayName("load() enchaine boutons, patterns puis inventaires")
        void load_ordersSteps() {
            loader.load();

            InOrder order = inOrder(buttonManager, plugin);
            order.verify(buttonManager, times(REGISTERED_LOADERS)).register(any());
            order.verify(plugin).saveFile("patterns/decoration.yml", false);
            order.verify(plugin).saveFile("inventories/auction.yml", false);
        }

        @Test
        @DisplayName("reload() supprime les inventaires du plugin puis recharge patterns et inventaires, sans re-enregistrer les boutons")
        void reload_deletesThenReloads() {
            loader.reload();

            InOrder order = inOrder(inventoryManager, plugin);
            order.verify(inventoryManager).deleteInventories(plugin);
            order.verify(plugin).saveFile("patterns/decoration.yml", false);
            order.verify(plugin).saveFile("inventories/auction.yml", false);
            verify(buttonManager, never()).register(any());
        }
    }

    @Nested
    @DisplayName("openInventory")
    class OpenTests {

        @Test
        @DisplayName("inventaire connu : ouvert pour le joueur a la page demandee, sur son thread")
        void known_opensAtPage() {
            Player player = MockPlugins.player("Alice");
            Inventory inventory = mock(Inventory.class);
            when(inventoryManager.getInventory(plugin, "auction")).thenReturn(Optional.of(inventory));

            loader.openInventory(player, Inventories.AUCTION, 3);

            verify(inventoryManager).openInventoryWithOldInventories(player, inventory, 3);
        }

        @Test
        @DisplayName("sans page : page 1")
        void known_defaultPage() {
            Player player = MockPlugins.player("Alice");
            Inventory inventory = mock(Inventory.class);
            when(inventoryManager.getInventory(plugin, "history")).thenReturn(Optional.of(inventory));

            loader.openInventory(player, Inventories.HISTORY);

            verify(inventoryManager).openInventoryWithOldInventories(player, inventory, 1);
        }
    }
}
