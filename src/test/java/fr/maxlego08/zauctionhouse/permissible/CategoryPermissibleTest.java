package fr.maxlego08.zauctionhouse.permissible;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.requirement.Action;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.category.ZCategory;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CategoryPermissibleTest {

    private AuctionPlugin plugin;
    private PlayerCache cache;
    private Player player;
    private InventoryEngine engine;
    private Placeholders placeholders;

    @BeforeEach
    void setUp() {
        // Collaborateurs construits AVANT les stubs qui les rendent
        cache = mock(PlayerCache.class);
        AuctionManager auctionManager = mock(AuctionManager.class);
        player = MockPlugins.player("Alice");
        engine = mock(InventoryEngine.class);
        placeholders = new Placeholders();

        plugin = mock(AuctionPlugin.class);
        lenient().when(plugin.getAuctionManager()).thenReturn(auctionManager);
        lenient().when(auctionManager.getCache(player)).thenReturn(cache);
    }

    private void currentCategory(Category category) {
        doReturn(category).when(cache).get(PlayerCacheKey.CURRENT_CATEGORY);
    }

    private CategoryPermissible permissible(String categoryId) {
        return new CategoryPermissible(plugin, categoryId, List.of(), List.of());
    }

    @Test
    @DisplayName("hasPermission : vrai quand la categorie courante a le meme id")
    void hasPermission_sameCategory_returnsTrue() {
        currentCategory(ZCategory.miscellaneous("weapons", "W"));

        assertTrue(permissible("weapons").hasPermission(player, null, engine, placeholders));
    }

    @Test
    @DisplayName("hasPermission : comparaison insensible a la casse")
    void hasPermission_caseInsensitive() {
        currentCategory(new ZCategory("Weapons", "W", null, null, false));

        assertTrue(permissible("weapons").hasPermission(player, null, engine, placeholders));
        assertTrue(permissible("WEAPONS").hasPermission(player, null, engine, placeholders));
    }

    @Test
    @DisplayName("hasPermission : faux quand la categorie courante differe")
    void hasPermission_differentCategory_returnsFalse() {
        currentCategory(ZCategory.miscellaneous("armor", "A"));

        assertFalse(permissible("weapons").hasPermission(player, null, engine, placeholders));
    }

    @Test
    @DisplayName("hasPermission : sans categorie en cache, 'main' est la categorie implicite")
    void hasPermission_noCategory_defaultsToMain() {
        currentCategory(null);

        assertTrue(permissible("main").hasPermission(player, null, engine, placeholders));
        assertTrue(permissible("MAIN").hasPermission(player, null, engine, placeholders));
        assertFalse(permissible("weapons").hasPermission(player, null, engine, placeholders));
    }

    @Test
    @DisplayName("hasPermission : lit le cache DU joueur passe en parametre")
    void hasPermission_readsCacheOfGivenPlayer() {
        currentCategory(ZCategory.miscellaneous("weapons", "W"));

        permissible("weapons").hasPermission(player, null, engine, placeholders);

        verify(plugin.getAuctionManager()).getCache(player);
        verify(cache).get(PlayerCacheKey.CURRENT_CATEGORY);
    }

    @Test
    @DisplayName("isValid : faux si l'id est vide, vrai sinon")
    void isValid_requiresNonEmptyId() {
        assertTrue(permissible("weapons").isValid());
        assertFalse(permissible("").isValid());
    }

    @Test
    @DisplayName("isValid : faux si l'id est null")
    void isValid_nullId_returnsFalse() {
        // L'annotation @NotNull n'est pas verifiee a l'execution : isValid doit couvrir le cas
        assertFalse(new CategoryPermissible(plugin, null, List.of(), List.of()).isValid());
    }

    @Test
    @DisplayName("getCategoryId : rend l'id tel que fourni")
    void getCategoryId_returnsRawId() {
        assertEquals("Weapons", permissible("Weapons").getCategoryId());
    }

    @Test
    @DisplayName("les actions deny/success sont transmises au Permissible parent")
    void actions_areForwardedToParent() {
        Action deny = mock(Action.class);
        Action success = mock(Action.class);

        CategoryPermissible permissible = new CategoryPermissible(plugin, "weapons", List.of(deny), List.of(success));

        assertEquals(List.of(deny), permissible.getDenyActions());
        assertEquals(List.of(success), permissible.getSuccessActions());
    }
}
