package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PermissionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SortConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.placeholder.AutoPlaceholder;
import fr.maxlego08.zauctionhouse.placeholder.LocalPlaceholder;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Placeholders par joueur, resolus a travers un vrai {@link LocalPlaceholder} avec un
 * {@link AuctionManager}, une {@link Configuration} et un {@link EconomyManager} mockes et un cache
 * joueur en memoire ({@link FakePlayerCache}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerPlaceholdersTest {

    AuctionPlugin plugin;
    AuctionManager manager;
    Configuration configuration;
    CategoryManager categoryManager;
    EconomyManager economyManager;
    AuctionEconomy vault;
    AuctionEconomy points;
    FakePlayerCache cache;
    Player player;
    LocalPlaceholder placeholder;

    @BeforeEach
    void setUp() {
        manager = mock(AuctionManager.class);
        configuration = mock(Configuration.class);
        categoryManager = mock(CategoryManager.class);
        vault = MockPlugins.economy("vault");
        points = MockPlugins.economy("points");
        when(points.getDisplayName()).thenReturn("Player Points");
        economyManager = MockPlugins.economyManager(vault, points);
        // format(economie, montant) -> "<montant> <nom>" : assez pour prouver que le formatage passe par l'economie.
        when(economyManager.format(any(AuctionEconomy.class), any(Number.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, Number.class).toString() + " " + invocation.getArgument(0, AuctionEconomy.class).getName());

        Map<SortItem, String> translations = new HashMap<>();
        translations.put(SortItem.DECREASING_DATE, "Newest first");
        translations.put(SortItem.DECREASING_PRICE, "Most expensive");
        translations.put(SortItem.ASCENDING_DATE, "Oldest first");
        translations.put(SortItem.ASCENDING_PRICE, "Cheapest");
        when(configuration.getSort()).thenReturn(new SortConfiguration(SortItem.DECREASING_DATE, translations));

        EnumMap<ItemType, Map<String, Integer>> limits = new EnumMap<>(ItemType.class);
        limits.put(ItemType.AUCTION, Map.of("zauctionhouse.limit.default", 3, "zauctionhouse.limit.vip", 10));
        limits.put(ItemType.BID, Map.of());
        limits.put(ItemType.RENT, Map.of("zauctionhouse.limit.default", 1));
        when(configuration.getPermission()).thenReturn(new PermissionConfiguration(limits));

        player = MockPlugins.player("Alice");
        cache = new FakePlayerCache();
        when(manager.getCache(player)).thenReturn(cache);

        plugin = mock(AuctionPlugin.class);
        when(plugin.getAuctionManager()).thenReturn(manager);
        when(plugin.getConfiguration()).thenReturn(configuration);
        when(plugin.getCategoryManager()).thenReturn(categoryManager);
        when(plugin.getEconomyManager()).thenReturn(economyManager);

        register();
    }

    /**
     * (Re)construit le LocalPlaceholder : PlayerPlaceholders capture l'EconomyManager au moment de
     * l'enregistrement, il faut re-enregistrer apres l'avoir change.
     */
    void register() {
        placeholder = new LocalPlaceholder(plugin);
        new PlayerPlaceholders().register(placeholder, plugin);
    }

    String resolve(String name) {
        return placeholder.setPlaceholders(player, "%zauctionhouse_" + name + "%");
    }

    static List<Item> items(int count) {
        Item[] items = new Item[count];
        for (int index = 0; index < count; index++) items[index] = mock(Item.class);
        return List.of(items);
    }

    @Test
    @DisplayName("tous les placeholders attendus sont enregistres avec une description")
    void register_declaresExpectedPlaceholders() {
        List<String> names = placeholder.getAutoPlaceholders().stream().map(AutoPlaceholder::getStartWith).toList();
        assertTrue(names.containsAll(List.of(
                "expired_items", "selling_items", "purchased_items", "sorting_value", "sorting_name",
                "category_name", "category_id", "sell_price", "sell_price_formatted", "sell_economy",
                "sell_economy_name", "sell_amount", "pending_money", "pending_money_raw", "pending_money_",
                "has_pending_money", "max_items_")), names.toString());
        assertEquals(names.size(), names.stream().distinct().count(), "doublon : " + names);
        assertTrue(placeholder.getAutoPlaceholders().stream().allMatch(auto -> auto.getDescription() != null && !auto.getDescription().isBlank()));
    }

    @Nested
    @DisplayName("Compteurs d'items")
    class Counters {

        @Test
        @DisplayName("expired_items / selling_items / purchased_items rendent la taille des listes du manager")
        void counters_reflectManagerLists() {
            when(manager.getExpiredItems(player)).thenReturn(items(2));
            when(manager.getPlayerSellingItems(player)).thenReturn(items(5));
            when(manager.getPurchasedItems(player)).thenReturn(items(0));

            assertEquals("2", resolve("expired_items"));
            assertEquals("5", resolve("selling_items"));
            assertEquals("0", resolve("purchased_items"));
        }
    }

    @Nested
    @DisplayName("Tri")
    class Sorting {

        @Test
        @DisplayName("sorting_value / sorting_name : tri par defaut de la configuration quand le cache est vide")
        void sorting_default() {
            assertEquals("DECREASING_DATE", resolve("sorting_value"));
            assertEquals("Newest first", resolve("sorting_name"));
        }

        @Test
        @DisplayName("sorting_value / sorting_name : tri choisi par le joueur")
        void sorting_fromCache() {
            cache.set(PlayerCacheKey.ITEM_SORT, SortItem.ASCENDING_PRICE);
            assertEquals("ASCENDING_PRICE", resolve("sorting_value"));
            assertEquals("Cheapest", resolve("sorting_name"));
        }
    }

    @Nested
    @DisplayName("Categorie courante")
    class CurrentCategory {

        @Test
        @DisplayName("sans categorie : nom 'toutes les categories' et id 'main'")
        void noCategory_mainAndAllName() {
            when(categoryManager.getAllCategoryName()).thenReturn("All items");
            assertEquals("All items", resolve("category_name"));
            assertEquals("main", resolve("category_id"));
        }

        @Test
        @DisplayName("avec categorie : nom affiche et identifiant de la categorie")
        void withCategory() {
            Category category = mock(Category.class);
            when(category.getDisplayName()).thenReturn("Blocks");
            when(category.getId()).thenReturn("blocks");
            cache.set(PlayerCacheKey.CURRENT_CATEGORY, category);

            assertEquals("Blocks", resolve("category_name"));
            assertEquals("blocks", resolve("category_id"));
        }
    }

    @Nested
    @DisplayName("Vente en cours (inventaire de vente)")
    class SellInProgress {

        @Test
        @DisplayName("valeurs par defaut : prix 0, economie par defaut, quantite 0")
        void defaults() {
            assertEquals("0", resolve("sell_price"));
            assertEquals("0 vault", resolve("sell_price_formatted"));
            assertEquals("vault", resolve("sell_economy"));
            assertEquals("vault", resolve("sell_economy_name"));
            assertEquals("0", resolve("sell_amount"));
        }

        @Test
        @DisplayName("sell_price rend le prix brut sans notation scientifique")
        void sellPrice_plainString() {
            cache.set(PlayerCacheKey.SELL_PRICE, new BigDecimal("1500.50"));
            assertEquals("1500.50", resolve("sell_price"));
            cache.set(PlayerCacheKey.SELL_PRICE, new BigDecimal("1E+7"));
            assertEquals("10000000", resolve("sell_price"), "toPlainString, pas toString");
        }

        @Test
        @DisplayName("sell_price_formatted formate avec l'economie choisie")
        void sellPriceFormatted_usesChosenEconomy() {
            cache.set(PlayerCacheKey.SELL_PRICE, new BigDecimal("250"));
            cache.set(PlayerCacheKey.SELL_ECONOMY, points);
            assertEquals("250 points", resolve("sell_price_formatted"));
            assertEquals("Player Points", resolve("sell_economy"));
            assertEquals("points", resolve("sell_economy_name"));
        }

        @Test
        @DisplayName("sell_amount rend la quantite demandee")
        void sellAmount() {
            cache.set(PlayerCacheKey.SELL_AMOUNT, 16);
            assertEquals("16", resolve("sell_amount"));
        }

        @Test
        @DisplayName("sans economie par defaut : prix brut, economie vide")
        void noDefaultEconomy_fallbacks() {
            // Le mock est construit AVANT le when(...) : le creer dans thenReturn(...) declenche UnfinishedStubbingException.
            EconomyManager emptyEconomyManager = MockPlugins.economyManager();
            when(plugin.getEconomyManager()).thenReturn(emptyEconomyManager);
            register();
            cache.set(PlayerCacheKey.SELL_PRICE, new BigDecimal("12.5"));
            cache.set(PlayerCacheKey.SELL_ECONOMY, points);

            assertEquals("12.5", resolve("sell_price_formatted"));
            assertEquals("", resolve("sell_economy"), "sans economie par defaut, l'economie choisie n'est meme pas lue");
            assertEquals("", resolve("sell_economy_name"));
        }
    }

    @Nested
    @DisplayName("Argent en attente")
    class PendingMoney {

        Map<String, BigDecimal> pending() {
            Map<String, BigDecimal> pending = new LinkedHashMap<>();
            pending.put("vault", new BigDecimal("10"));
            pending.put("points", new BigDecimal("5.5"));
            return pending;
        }

        @Test
        @DisplayName("sans donnees en cache : 0 / 0 / false")
        void noData() {
            assertEquals("0", resolve("pending_money"));
            assertEquals("0", resolve("pending_money_raw"));
            assertEquals("false", resolve("has_pending_money"));
            assertEquals("0", resolve("pending_money_vault"));
        }

        @Test
        @DisplayName("une map vide se comporte comme l'absence de donnees")
        void emptyMap() {
            cache.set(PlayerCacheKey.PENDING_MONEY_DATA, new HashMap<String, BigDecimal>());
            assertEquals("0", resolve("pending_money"));
            assertEquals("0", resolve("pending_money_raw"));
            assertEquals("false", resolve("has_pending_money"));
        }

        @Test
        @DisplayName("pending_money : total de toutes les economies, formate avec l'economie par defaut")
        void total_formattedWithDefaultEconomy() {
            cache.set(PlayerCacheKey.PENDING_MONEY_DATA, pending());
            assertEquals("15.5 vault", resolve("pending_money"));
            assertEquals("15.5", resolve("pending_money_raw"));
            assertEquals("true", resolve("has_pending_money"));
        }

        @Test
        @DisplayName("pending_money sans economie par defaut : total brut")
        void total_withoutDefaultEconomy() {
            // Le mock est construit AVANT le when(...) : le creer dans thenReturn(...) declenche UnfinishedStubbingException.
            EconomyManager emptyEconomyManager = MockPlugins.economyManager();
            when(plugin.getEconomyManager()).thenReturn(emptyEconomyManager);
            register();
            cache.set(PlayerCacheKey.PENDING_MONEY_DATA, pending());
            assertEquals("15.5", resolve("pending_money"));
        }

        @Test
        @DisplayName("pending_money_<economie> : montant de cette economie, formate si elle est connue")
        void perEconomy() {
            Map<String, BigDecimal> pending = pending();
            pending.put("gems", new BigDecimal("7"));
            cache.set(PlayerCacheKey.PENDING_MONEY_DATA, pending);

            assertEquals("10 vault", resolve("pending_money_vault"));
            assertEquals("5.5 points", resolve("pending_money_points"));
            assertEquals("7", resolve("pending_money_gems"), "economie inconnue du manager : montant brut");
            assertEquals("0", resolve("pending_money_unknown"), "economie absente de la map");
            assertEquals("0", resolve("pending_money_"), "nom vide");
        }

        @Test
        @DisplayName("has_pending_money est faux quand le total est nul ou negatif")
        void hasPendingMoney_zeroTotal() {
            Map<String, BigDecimal> pending = new HashMap<>();
            pending.put("vault", BigDecimal.ZERO);
            cache.set(PlayerCacheKey.PENDING_MONEY_DATA, pending);
            assertEquals("false", resolve("has_pending_money"));
            assertEquals("0 vault", resolve("pending_money"));

            pending.put("vault", new BigDecimal("0.01"));
            assertEquals("true", resolve("has_pending_money"));
        }
    }

    @Nested
    @DisplayName("Limites d'items")
    class Limits {

        @Test
        @DisplayName("max_items_<type> : la plus haute limite parmi les permissions du joueur")
        void maxItems_highestPermission() {
            when(player.hasPermission("zauctionhouse.limit.default")).thenReturn(true);
            assertEquals("3", resolve("max_items_auction"));

            when(player.hasPermission("zauctionhouse.limit.vip")).thenReturn(true);
            assertEquals("10", resolve("max_items_auction"));
            assertEquals("10", resolve("max_items_AUCTION"), "insensible a la casse");
            assertEquals("1", resolve("max_items_rent"));
        }

        @Test
        @DisplayName("max_items_<type> : 0 sans permission ou sans limite configuree")
        void maxItems_zero() {
            assertEquals("0", resolve("max_items_auction"));
            when(player.hasPermission("zauctionhouse.limit.vip")).thenReturn(true);
            assertEquals("0", resolve("max_items_bid"), "aucune limite configuree pour BID");
        }

        @ParameterizedTest(name = "max_items_{0} -> 0")
        @ValueSource(strings = {"", "unknown", "auctions"})
        @DisplayName("max_items_ : type vide ou inconnu rend 0")
        void maxItems_invalidType(String type) {
            when(player.hasPermission("zauctionhouse.limit.vip")).thenReturn(true);
            assertEquals("0", resolve("max_items_" + type));
        }
    }
}
