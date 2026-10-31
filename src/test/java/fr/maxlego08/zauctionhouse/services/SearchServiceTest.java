package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;
import fr.maxlego08.zauctionhouse.api.filter.SearchFilterType;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.category.ZCategory;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import fr.maxlego08.zauctionhouse.utils.cache.SortedItemsCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link SearchService} sur un {@link SortedItemsCache} REEL alimente par des annonces
 * mockees dont les ItemStack portent un ItemMeta mocke (nom, lore).
 * <p>
 * Catalogue de test (prix / expiration croissants par id inverse pour distinguer les tris) :
 * <pre>
 *  id 1  DIAMOND_SWORD  "Epic Sword"  lore [Sharpness V, Unbreakable]  vendeur Notch      prix 150  expire +1000
 *  id 2  STONE          (sans meta)                                     vendeur Alice      prix  10  expire +2000
 *  id 3  DIAMOND        "Shiny Gem"   lore [A gem]                      vendeur Bob        prix  50  expire +3000
 *  id 4  IRON_SWORD     (sans nom)    lore [Rusty blade]                vendeur notch_fan  prix  20  expire +4000
 * </pre>
 * Les materiaux sont de VRAIS constantes {@link Material} : seul {@code name()} est lu, jamais une
 * methode qui passe par le registre Paper.
 */
class SearchServiceTest {

    private static final long BASE_EXPIRATION = System.currentTimeMillis() + 86_400_000L;

    @TempDir
    File dataFolder;

    private final Map<Integer, Item> store = new LinkedHashMap<>();
    private final Category weapons = new ZCategory("weapons", "Weapons", List.of(), List.of(), false);
    private final Category blocks = new ZCategory("blocks", "Blocks", List.of(), List.of(), false);
    private final Category misc = ZCategory.miscellaneous("misc", "Misc");

    private AuctionPlugin plugin;
    private SortedItemsCache cache;
    private SearchService service;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        Configuration configuration = this.plugin.getConfiguration();
        AuctionManager manager = mock(AuctionManager.class);
        SearchFilterConfiguration filters = defaultFilters();
        PerformanceConfiguration performance = new PerformanceConfiguration(10_000, 5_000, 2, 5_000L, 5_000L, 3_000L, 3_000L, 5_000L);

        when(configuration.getSearchFilter()).thenReturn(filters);
        when(configuration.getPerformance()).thenReturn(performance);
        when(this.plugin.getAuctionManager()).thenReturn(manager);
        when(manager.getItem(eq(StorageType.LISTED), anyInt())).thenAnswer(invocation -> this.store.get((Integer) invocation.getArgument(1)));

        add(item(1, Material.DIAMOND_SWORD, "Epic Sword", List.of("Sharpness V", "Unbreakable"), "Notch", "150", 1_000L, weapons));
        add(item(2, Material.STONE, null, null, "Alice", "10", 2_000L, blocks));
        add(item(3, Material.DIAMOND, "Shiny Gem", List.of("A gem"), "Bob", "50", 3_000L, misc));
        add(item(4, Material.IRON_SWORD, null, List.of("Rusty blade"), "notch_fan", "20", 4_000L, weapons));

        this.cache = new SortedItemsCache(this.plugin, () -> new ArrayList<>(this.store.values()));
        this.cache.rebuild();
        this.service = new SearchService(this.plugin);
    }

    @AfterEach
    void tearDown() {
        if (this.cache != null) this.cache.shutdown();
    }

    private static SearchFilterConfiguration defaultFilters() {
        Map<SearchFilterType, String> operators = new EnumMap<>(SearchFilterType.class);
        for (SearchFilterType type : SearchFilterType.values()) operators.put(type, type.getDefaultOperator());
        return new SearchFilterConfiguration(operators);
    }

    private void add(Item item) {
        this.store.put(item.getId(), item);
    }

    private static AuctionItem item(int id, Material material, String displayName, List<String> lore, String seller,
                                    String price, long expirationOffset, Category... categories) {
        // Les collaborateurs sont crees AVANT les stubs qui les rendent.
        ItemMeta meta = mock(ItemMeta.class);
        ItemStack itemStack = TestItems.itemStack(material, 1);
        AuctionItem item = mock(AuctionItem.class);

        List<Component> loreComponents = null;
        if (lore != null) {
            loreComponents = new ArrayList<>();
            for (String line : lore) loreComponents.add(Component.text(line));
        }
        when(meta.displayName()).thenReturn(displayName == null ? null : Component.text(displayName));
        when(meta.lore()).thenReturn(loreComponents);
        when(itemStack.hasItemMeta()).thenReturn(displayName != null || lore != null);
        when(itemStack.getItemMeta()).thenReturn(meta);

        when(item.getId()).thenReturn(id);
        when(item.getStatus()).thenReturn(ItemStatus.AVAILABLE);
        when(item.isExpired()).thenReturn(false);
        when(item.getExpiredAt()).thenReturn(new Date(BASE_EXPIRATION + expirationOffset));
        when(item.getPrice()).thenReturn(new BigDecimal(price));
        when(item.getCategories()).thenReturn(new HashSet<>(Arrays.asList(categories)));
        when(item.getSellerName()).thenReturn(seller);
        when(item.getTranslationKey()).thenReturn("item.minecraft." + material.name().toLowerCase());
        when(item.getItemStacks()).thenReturn(List.of(itemStack));
        when(item.getItemStack()).thenReturn(itemStack);
        return item;
    }

    private List<Integer> search(String query, SortItem sort, Category category) {
        IntList ids = this.service.search(this.cache, query, sort, category);
        List<Integer> result = new ArrayList<>();
        for (int id : ids) result.add(id);
        return result;
    }

    private List<Integer> search(String query) {
        return search(query, SortItem.ASCENDING_PRICE, null);
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("requete vide")
    class EmptyQuery {

        @Test
        @DisplayName("une requete vide rend tous les identifiants dans l'ordre de tri")
        void search_emptyQuery_returnsAllSorted() {
            assertEquals(List.of(2, 4, 3, 1), search(""));
            assertEquals(List.of(1, 3, 4, 2), search("", SortItem.DECREASING_PRICE, null));
            assertEquals(List.of(1, 2, 3, 4), search("", SortItem.ASCENDING_DATE, null));
            assertEquals(List.of(4, 3, 2, 1), search("", SortItem.DECREASING_DATE, null));
        }

        @Test
        @DisplayName("une requete nulle est traitee comme vide")
        void search_nullQuery_returnsAll() {
            assertEquals(List.of(2, 4, 3, 1), search(null));
        }

        @Test
        @DisplayName("un filtre sans valeur (\"name ~\") rend tous les identifiants")
        void search_filterWithoutValue_returnsAll() {
            assertEquals(List.of(2, 4, 3, 1), search("name ~"));
        }

        @Test
        @DisplayName("requete vide avec categorie : tous les items de la categorie, tries")
        void search_emptyQueryWithCategory_returnsCategoryItems() {
            assertEquals(List.of(4, 1), search("", SortItem.ASCENDING_PRICE, weapons));
            assertEquals(List.of(2), search("", SortItem.ASCENDING_PRICE, blocks));
        }
    }

    @Nested
    @DisplayName("recherche par defaut (tous les champs)")
    class DefaultSearch {

        @ParameterizedTest(name = "\"{0}\" matche DIAMOND et DIAMOND_SWORD")
        @ValueSource(strings = {"diamond", "DIAMOND", "DiAmOnD"})
        @DisplayName("le nom du materiau est compare sans tenir compte de la casse")
        void search_byMaterialName_isCaseInsensitive(String query) {
            assertEquals(List.of(3, 1), search(query));
        }

        @Test
        @DisplayName("une sous-chaine du materiau suffit (\"sword\" -> les deux epees)")
        void search_byMaterialSubstring() {
            assertEquals(List.of(4, 1), search("sword"));
            assertEquals(List.of(2), search("stone"));
        }

        @Test
        @DisplayName("le nom d'affichage est recherche en sous-chaine insensible a la casse")
        void search_byDisplayName() {
            assertEquals(List.of(1), search("epic"));
            assertEquals(List.of(1), search("EPIC SW"));
            assertEquals(List.of(3), search("shiny"));
        }

        @Test
        @DisplayName("chaque ligne de lore est recherchee")
        void search_byLore() {
            assertEquals(List.of(1), search("unbreak"));
            assertEquals(List.of(1), search("sharpness v"));
            assertEquals(List.of(4), search("rusty"));
            assertEquals(List.of(3), search("a gem"));
        }

        @Test
        @DisplayName("le nom du vendeur ne matche qu'en egalite stricte (insensible a la casse)")
        void search_bySellerName_exactOnly() {
            assertEquals(List.of(1), search("notch"));
            assertEquals(List.of(1), search("NOTCH"));
            assertEquals(List.of(4), search("notch_fan"));
            assertTrue(search("notch_f").isEmpty(), "un prefixe du nom du vendeur ne matche pas");
        }

        @Test
        @DisplayName("aucun resultat rend une liste vide")
        void search_noMatch_returnsEmpty() {
            assertTrue(search("netherite").isEmpty());
        }

        @Test
        @DisplayName("les resultats respectent l'ordre de tri demande")
        void search_respectsSortOrder() {
            assertEquals(List.of(4, 1), search("sword", SortItem.ASCENDING_PRICE, null));
            assertEquals(List.of(1, 4), search("sword", SortItem.DECREASING_PRICE, null));
            assertEquals(List.of(1, 4), search("sword", SortItem.ASCENDING_DATE, null));
            assertEquals(List.of(4, 1), search("sword", SortItem.DECREASING_DATE, null));
        }

        @Test
        @DisplayName("la categorie restreint la recherche")
        void search_withCategory_restrictsResults() {
            assertEquals(List.of(3, 1), search("diamond", SortItem.ASCENDING_PRICE, null));
            assertEquals(List.of(1), search("diamond", SortItem.ASCENDING_PRICE, weapons));
            assertEquals(List.of(3), search("diamond", SortItem.ASCENDING_PRICE, misc));
            assertTrue(search("diamond", SortItem.ASCENDING_PRICE, blocks).isEmpty());
        }

        @Test
        @DisplayName("un item sans meta n'est pas trouve par son nom ni son lore")
        void search_itemWithoutMeta_notMatchedByNameOrLore() {
            assertEquals(List.of(2), search("stone"));
            assertEquals(List.of(2), search("alice"), "seul le nom exact du vendeur peut encore matcher");
            assertTrue(search("name ~= stone").isEmpty());
            assertTrue(search("lore ~= stone").isEmpty());
        }

        @Test
        @DisplayName("un identifiant absent du manager est ignore sans faire echouer la recherche")
        void search_itemMissingFromManager_isSkipped() {
            store.remove(1); // le cache trie garde l'id 1 (instantane ancien), le manager ne le connait plus

            assertEquals(List.of(4), search("sword"));
        }

        @Test
        @DisplayName("un item expire n'est pas dans le cache trie, donc jamais dans les resultats")
        void search_expiredItem_isExcluded() {
            AuctionItem expired = item(5, Material.DIAMOND_SWORD, "Old Sword", null, "Carl", "5", 5_000L, weapons);
            when(expired.isExpired()).thenReturn(true);
            add(expired);
            cache.rebuild();

            assertEquals(List.of(4, 1), search("sword"));
        }

        @Test
        @DisplayName("un item dont le statut n'est plus AVAILABLE est exclu")
        void search_nonAvailableItem_isExcluded() {
            AuctionItem reserved = item(6, Material.DIAMOND_SWORD, "Reserved Sword", null, "Dan", "5", 6_000L, weapons);
            when(reserved.getStatus()).thenReturn(ItemStatus.IS_BEING_PURCHASED);
            add(reserved);
            cache.rebuild();

            assertEquals(List.of(4, 1), search("sword"));
        }
    }

    @Nested
    @DisplayName("recherche ciblee (champ + operateur)")
    class FilteredSearch {

        @Test
        @DisplayName("name ~ est sensible a la casse, name ~= ne l'est pas")
        void search_nameContains() {
            assertTrue(search("name ~ epic").isEmpty());
            assertEquals(List.of(1), search("name ~ Epic"));
            assertEquals(List.of(1), search("name ~= EPIC"));
            assertEquals(List.of(3, 1), search("name ~= s"), "\"Shiny Gem\" et \"Epic Sword\" contiennent un s");
        }

        @Test
        @DisplayName("name = exige l'egalite stricte, name == l'egalite insensible a la casse")
        void search_nameEquals() {
            assertEquals(List.of(1), search("name = Epic Sword"));
            assertTrue(search("name = Epic").isEmpty());
            assertTrue(search("name = epic sword").isEmpty());
            assertEquals(List.of(1), search("name == epic sword"));
        }

        @Test
        @DisplayName("material compare le nom exact du materiau")
        void search_material() {
            assertEquals(List.of(3), search("material = DIAMOND"));
            assertTrue(search("material = diamond").isEmpty());
            assertEquals(List.of(3), search("material == diamond"));
            assertEquals(List.of(3, 1), search("material ~ DIAMOND"));
            assertTrue(search("material ~ diamond").isEmpty());
            assertEquals(List.of(3, 1), search("material ~= diamond"));
            assertEquals(List.of(4, 1), search("material ~= sword"));
        }

        @Test
        @DisplayName("seller compare le nom du vendeur selon l'operateur")
        void search_seller() {
            assertEquals(List.of(1), search("seller = Notch"));
            assertTrue(search("seller = notch").isEmpty());
            assertEquals(List.of(1), search("seller == notch"));
            assertEquals(List.of(4), search("seller ~ notch"), "seul notch_fan contient 'notch' en minuscules");
            assertEquals(List.of(4, 1), search("seller ~= notch"));
        }

        @Test
        @DisplayName("lore teste chaque ligne du lore")
        void search_lore() {
            assertEquals(List.of(1), search("lore ~ Unbreakable"));
            assertEquals(List.of(1), search("lore = Sharpness V"));
            assertTrue(search("lore = Sharpness").isEmpty());
            assertEquals(List.of(4), search("lore ~= RUSTY"));
            assertEquals(List.of(4), search("lore == rusty blade"));
            assertTrue(search("lore ~ x").isEmpty());
        }

        @Test
        @DisplayName("les espaces autour de l'operateur et de la valeur sont ignores")
        void search_spacesAreTrimmed() {
            assertEquals(List.of(2), search("material   =   STONE   "));
            assertEquals(List.of(2), search("material=STONE"));
        }

        @Test
        @DisplayName("un champ inconnu retombe sur la recherche par defaut avec la requete brute")
        void search_unknownField_fallsBackToDefault() {
            assertTrue(search("foo ~ sword").isEmpty());
        }

        @Test
        @DisplayName("un operateur en tete de requete n'est pas un filtre")
        void search_operatorAtStart_isDefaultSearch() {
            assertTrue(search("= Notch").isEmpty());
        }

        @Test
        @DisplayName("un filtre respecte aussi la categorie et le tri")
        void search_filterWithCategoryAndSort() {
            assertEquals(List.of(1, 4), search("material ~= sword", SortItem.DECREASING_PRICE, weapons));
            assertTrue(search("material ~= sword", SortItem.DECREASING_PRICE, blocks).isEmpty());
        }
    }
}
