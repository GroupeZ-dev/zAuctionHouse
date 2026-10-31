package fr.maxlego08.zauctionhouse.loader.permissibles;

import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.requirement.Action;
import fr.maxlego08.menu.api.requirement.Permissible;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.permissible.CategoryPermissible;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryPermissibleLoaderTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private ButtonManager buttonManager;
    private AuctionManager auctionManager;
    private PlayerCache cache;
    private Player player;
    private File file;
    private CategoryPermissibleLoader loader;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.buttonManager = mock(ButtonManager.class);
        InventoriesLoader inventoriesLoader = mock(InventoriesLoader.class);
        when(inventoriesLoader.getButtonManager()).thenReturn(this.buttonManager);
        when(this.plugin.getInventoriesLoader()).thenReturn(inventoriesLoader);
        lenient().when(this.buttonManager.loadActions(any(List.class), any(String.class), any(File.class))).thenReturn(List.of());

        this.auctionManager = mock(AuctionManager.class);
        this.cache = mock(PlayerCache.class);
        this.player = MockPlugins.player("Alice");
        lenient().when(this.plugin.getAuctionManager()).thenReturn(this.auctionManager);
        lenient().when(this.auctionManager.getCache(this.player)).thenReturn(this.cache);

        this.file = new File(this.dataFolder, "auction.yml");
        this.loader = new CategoryPermissibleLoader(this.plugin);
    }

    private CategoryPermissible load(Map<String, Object> values) {
        Permissible permissible = this.loader.load("items.test.requirements", new TypedMapAccessor(values), this.file);
        return assertInstanceOf(CategoryPermissible.class, permissible);
    }

    private void currentCategory(String id) {
        Category category = mock(Category.class);
        lenient().when(category.getId()).thenReturn(id);
        when(this.cache.get(PlayerCacheKey.CURRENT_CATEGORY)).thenReturn(category);
    }

    @Test
    @DisplayName("la cle du permissible est zauctionhouse_category")
    void key() {
        assertEquals("zauctionhouse_category", loader.getKey());
    }

    @Nested
    @DisplayName("load")
    class LoadTests {

        @Test
        @DisplayName("la categorie est lue depuis 'category'")
        void category_read() {
            CategoryPermissible permissible = load(Map.of("category", "weapons"));

            assertEquals("weapons", permissible.getCategoryId());
            assertTrue(permissible.isValid());
        }

        @Test
        @DisplayName("sans 'category' -> main")
        void missingCategory_defaultsToMain() {
            assertEquals("main", load(Map.of()).getCategoryId());
        }

        @Test
        @DisplayName("les actions deny et success sont chargees via le ButtonManager avec le chemin et le fichier")
        void actions_loadedThroughButtonManager() {
            List<Map<String, Object>> denyMaps = List.of(Map.of("type", "message", "messages", List.of("non")));
            List<Map<String, Object>> successMaps = List.of(Map.of("type", "sound", "sound", "CLICK"));
            Action deny = mock(Action.class);
            Action success = mock(Action.class);
            when(buttonManager.loadActions(eq(denyMaps), eq("items.test.requirements"), eq(file))).thenReturn(List.of(deny));
            when(buttonManager.loadActions(eq(successMaps), eq("items.test.requirements"), eq(file))).thenReturn(List.of(success));
            Map<String, Object> values = new HashMap<>();
            values.put("category", "tools");
            values.put("deny", denyMaps);
            values.put("success", successMaps);

            CategoryPermissible permissible = load(values);

            assertEquals(List.of(deny), permissible.getDenyActions());
            assertEquals(List.of(success), permissible.getSuccessActions());
        }

        @Test
        @DisplayName("sans deny/success : listes vides demandees au ButtonManager")
        void noActions_emptyLists() {
            CategoryPermissible permissible = load(Map.of("category", "x"));

            assertTrue(permissible.getDenyActions().isEmpty());
            assertTrue(permissible.getSuccessActions().isEmpty());
        }

        @Test
        @DisplayName("categorie vide -> permissible invalide")
        void emptyCategory_invalid() {
            assertFalse(load(Map.of("category", "")).isValid());
        }
    }

    @Nested
    @DisplayName("hasPermission du permissible produit")
    class PermissionTests {

        private final InventoryEngine engine = mock(InventoryEngine.class);
        private final Placeholders placeholders = new Placeholders();

        @Test
        @DisplayName("vrai quand la categorie courante du joueur correspond")
        void matchingCategory_true() {
            currentCategory("weapons");

            assertTrue(load(Map.of("category", "weapons")).hasPermission(player, null, engine, placeholders));
        }

        @Test
        @DisplayName("comparaison insensible a la casse")
        void caseInsensitive() {
            currentCategory("WEAPONS");

            assertTrue(load(Map.of("category", "weapons")).hasPermission(player, null, engine, placeholders));
        }

        @Test
        @DisplayName("faux quand la categorie differe")
        void differentCategory_false() {
            currentCategory("tools");

            assertFalse(load(Map.of("category", "weapons")).hasPermission(player, null, engine, placeholders));
        }

        @Test
        @DisplayName("aucune categorie selectionnee -> consideree comme 'main'")
        void noCategory_isMain() {
            when(cache.get(PlayerCacheKey.CURRENT_CATEGORY)).thenReturn(null);

            assertTrue(load(Map.of("category", "main")).hasPermission(player, null, engine, placeholders));
            assertTrue(load(Map.of()).hasPermission(player, null, engine, placeholders));
            assertFalse(load(Map.of("category", "weapons")).hasPermission(player, null, engine, placeholders));
        }

        @Test
        @DisplayName("le cache interroge est celui du joueur passe en parametre")
        void usesPlayerCache() {
            Player other = MockPlugins.player("Bob");
            PlayerCache otherCache = mock(PlayerCache.class);
            Category tools = mock(Category.class);
            when(tools.getId()).thenReturn("tools");
            when(auctionManager.getCache(other)).thenReturn(otherCache);
            when(otherCache.get(PlayerCacheKey.CURRENT_CATEGORY)).thenReturn(tools);
            currentCategory("weapons");

            CategoryPermissible permissible = load(Map.of("category", "tools"));

            assertTrue(permissible.hasPermission(other, null, engine, placeholders));
            assertFalse(permissible.hasPermission(player, null, engine, placeholders));
            assertSame(otherCache, auctionManager.getCache(other));
        }
    }
}
