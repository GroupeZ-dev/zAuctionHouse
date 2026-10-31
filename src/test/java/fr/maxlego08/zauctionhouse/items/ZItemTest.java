package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemLoreConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.TimeConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemPlaceholder;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code ZItem} est abstrait : il est exerce a travers {@link ZAuctionItem}, son unique
 * implementation concrete, construite par {@link TestItems}.
 */
class ZItemTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private Configuration configuration;
    private AuctionEconomy economy;
    private final UUID seller = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(dataFolder);
        this.configuration = plugin.getConfiguration();
        this.economy = MockPlugins.economy("vault");
    }

    private ZAuctionItem item(int id, long expiredAt) {
        return TestItems.auctionItem(plugin, id, seller, "Seller", BigDecimal.TEN, economy, expiredAt, List.of(TestItems.diamond(1)));
    }

    private ZAuctionItem item(long expiredAt) {
        return item(1, expiredAt);
    }

    private static ItemLoreConfiguration itemLore() {
        return new ItemLoreConfiguration(false,
                List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(),
                "seller-status", "buyer-status", "right-seller-status", "right-buyer-status");
    }

    private static TimeConfiguration timeConfiguration() {
        return new TimeConfiguration("second", "seconds", "minute", "minutes", "hour", "hours", "day", "days",
                "%02dd %02dh %02dm", "%02dh %02dm %02ds", "%02dm %02ds", "%02ds");
    }

    @Test
    @DisplayName("les accesseurs refletent les valeurs du constructeur")
    void constructor_getters() {
        Date expiredAt = new Date(System.currentTimeMillis() + 60_000L);
        ZAuctionItem item = TestItems.auctionItem(plugin, 7, seller, "Seller", new BigDecimal("12.5"), economy, expiredAt.getTime(), List.of(TestItems.diamond(2)));

        assertEquals(7, item.getId());
        assertEquals(MockPlugins.SERVER_NAME, item.getServerName());
        assertEquals(seller, item.getSellerUniqueId());
        assertEquals("Seller", item.getSellerName());
        assertEquals(new BigDecimal("12.5"), item.getPrice());
        assertSame(economy, item.getAuctionEconomy());
        assertEquals("vault", item.getEconomyName());
        assertNotNull(item.getCreatedAt());
        assertEquals(expiredAt, item.getExpiredAt());
        assertEquals(ItemStatus.AVAILABLE, item.getStatus());
        assertNull(item.getBuyerUniqueId());
        assertNull(item.getBuyerName());
        assertTrue(item.getCategories().isEmpty());
    }

    @Test
    @DisplayName("setAuctionEconomy remplace l'economie mais conserve le nom d'origine (resolution apres reload)")
    void setAuctionEconomy_keepsEconomyName() {
        ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);
        AuctionEconomy points = MockPlugins.economy("points");

        item.setAuctionEconomy(points);

        assertSame(points, item.getAuctionEconomy());
        assertEquals("vault", item.getEconomyName());
    }

    @Test
    @DisplayName("setExpiredAt et setStatus mettent a jour l'etat")
    void setExpiredAt_setStatus() {
        ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);
        Date later = new Date(System.currentTimeMillis() + 120_000L);
        item.setExpiredAt(later);
        item.setStatus(ItemStatus.IS_BEING_PURCHASED);
        assertEquals(later, item.getExpiredAt());
        assertEquals(ItemStatus.IS_BEING_PURCHASED, item.getStatus());
    }

    @Nested
    @DisplayName("Expiration")
    class Expiration {

        @Test
        @DisplayName("une date passee est expiree, une date future ne l'est pas")
        void isExpired_pastAndFuture() {
            assertTrue(item(System.currentTimeMillis() - 1_000L).isExpired());
            assertFalse(item(System.currentTimeMillis() + 60_000L).isExpired());
        }

        @Test
        @DisplayName("expiredAt a 0 signifie « n'expire jamais »")
        void isExpired_zero_neverExpires() {
            assertFalse(item(0L).isExpired());
            assertTrue(item(0L).isActivelyListed());
        }

        @Test
        @DisplayName("isActivelyListed exige AVAILABLE et non expire")
        void isActivelyListed() {
            ZAuctionItem live = item(System.currentTimeMillis() + 60_000L);
            assertTrue(live.isActivelyListed());

            live.setStatus(ItemStatus.PURCHASED);
            assertFalse(live.isActivelyListed());

            ZAuctionItem expired = item(System.currentTimeMillis() - 1_000L);
            assertFalse(expired.isActivelyListed());
        }

        @Test
        @DisplayName("getRemainingTime formate le temps restant via la configuration de temps")
        void getRemainingTime_usesTimeConfiguration() {
            when(configuration.getTime()).thenReturn(timeConfiguration());
            // 90.5 s : la demi-seconde de marge absorbe le temps d'execution du test.
            ZAuctionItem item = item(System.currentTimeMillis() + 90_500L);

            assertEquals("01m 30s", item.getRemainingTime());
        }

        @Test
        @DisplayName("getFormattedExpireDate applique le format de date configure")
        void getFormattedExpireDate_usesDateFormat() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            when(configuration.getDateFormat()).thenReturn(format);
            long expiredAt = System.currentTimeMillis() + 60_000L;

            assertEquals(format.format(new Date(expiredAt)), item(expiredAt).getFormattedExpireDate());
        }
    }

    @Nested
    @DisplayName("Prix")
    class Price {

        @Test
        @DisplayName("getFormattedPrice delegue au gestionnaire d'economies avec l'economie de l'item")
        void getFormattedPrice_delegates() {
            EconomyManager economyManager = plugin.getEconomyManager();
            when(economyManager.format(economy, BigDecimal.TEN)).thenReturn("10$");
            when(economyManager.format(PriceFormat.PRICE_RAW, BigDecimal.TEN)).thenReturn("10");
            when(economyManager.format(PriceFormat.PRICE_WITH_REDUCTION, BigDecimal.TEN)).thenReturn("10");

            ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);

            assertEquals("10$", item.getFormattedPrice());
            assertEquals("10", item.getFormattedPrice(PriceFormat.PRICE_RAW));
            assertEquals("10", item.getFormattedPrice(PriceFormat.PRICE_WITH_REDUCTION));
        }
    }

    @Nested
    @DisplayName("Acheteur et categories")
    class BuyerAndCategories {

        @Test
        @DisplayName("setBuyer(Player) lit l'UUID et le nom du joueur")
        void setBuyer_fromPlayer() {
            ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);
            Player buyer = MockPlugins.player("Buyer");

            item.setBuyer(buyer);

            assertEquals(buyer.getUniqueId(), item.getBuyerUniqueId());
            assertEquals("Buyer", item.getBuyerName());
        }

        @Test
        @DisplayName("setBuyer(uuid, name) enregistre les identifiants fournis")
        void setBuyer_fromIdentifiers() {
            ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);
            UUID uuid = UUID.randomUUID();

            item.setBuyer(uuid, "Offline");

            assertEquals(uuid, item.getBuyerUniqueId());
            assertEquals("Offline", item.getBuyerName());
        }

        @Test
        @DisplayName("setCategories(null) retombe sur un ensemble vide, sinon conserve l'ensemble fourni")
        void setCategories_nullBecomesEmpty() {
            ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);
            Category weapons = mock(Category.class);
            lenient().when(weapons.getId()).thenReturn("weapons");
            Set<Category> categories = new HashSet<>(Set.of(weapons));

            item.setCategories(categories);
            assertSame(categories, item.getCategories());
            assertTrue(item.hasCategory(weapons));
            assertTrue(item.hasCategory("weapons"));
            assertFalse(item.hasCategory("blocks"));
            assertFalse(item.hasCategory((String) null));

            item.setCategories(null);
            assertNotNull(item.getCategories());
            assertTrue(item.getCategories().isEmpty());
            assertFalse(item.hasCategory(weapons));
        }
    }

    @Nested
    @DisplayName("Identite")
    class Identity {

        @Test
        @DisplayName("equals et hashCode reposent uniquement sur l'identifiant")
        void equals_hashCode_byId() {
            ZAuctionItem a = item(1, System.currentTimeMillis() + 60_000L);
            ZAuctionItem sameId = TestItems.auctionItem(plugin, 1, UUID.randomUUID(), "Other", BigDecimal.ONE, economy, 0L, List.of(TestItems.diamond(5)));
            ZAuctionItem otherId = item(2, System.currentTimeMillis() + 60_000L);

            assertEquals(a, a);
            assertEquals(a, sameId);
            assertEquals(a.hashCode(), sameId.hashCode());
            assertNotEquals(a, otherId);
            assertNotEquals(a, null);
            assertNotEquals(a, "not an item");
        }

        @Test
        @DisplayName("equals accepte toute implementation de Item portant le meme identifiant")
        void equals_anyItemImplementation() {
            ZAuctionItem a = item(42, 0L);
            Item other = mock(Item.class);
            when(other.getId()).thenReturn(42);
            assertEquals(a, other);
        }
    }

    @Nested
    @DisplayName("Interactions Bukkit")
    class BukkitInteractions {

        @Test
        @DisplayName("getSeller passe par Bukkit.getOfflinePlayer avec l'UUID du vendeur")
        void getSeller_usesBukkit() {
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer(seller)).thenReturn(offlinePlayer);
                assertSame(offlinePlayer, item(0L).getSeller());
            }
        }

        @Test
        @DisplayName("canReceiveItem depend d'un slot libre dans l'inventaire")
        void canReceiveItem_checksFirstEmptySlot() {
            Player player = MockPlugins.player("Receiver");
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            ZAuctionItem item = item(0L);

            when(inventory.firstEmpty()).thenReturn(-1);
            assertFalse(item.canReceiveItem(player));

            when(inventory.firstEmpty()).thenReturn(3);
            assertTrue(item.canReceiveItem(player));
        }
    }

    @Nested
    @DisplayName("Placeholders")
    class PlaceholdersTests {

        @Test
        @DisplayName("createPlaceholders(player, needed) ne resout que les placeholders demandes")
        void createPlaceholders_subset() {
            when(configuration.getItemLore()).thenReturn(itemLore());
            EconomyManager economyManager = plugin.getEconomyManager();
            when(economyManager.format(economy, BigDecimal.TEN)).thenReturn("10$");
            Player viewer = MockPlugins.player("Viewer");
            ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);

            Placeholders placeholders = item.createPlaceholders(viewer, Set.of(ItemPlaceholder.SELLER, ItemPlaceholder.PRICE, ItemPlaceholder.STATUS, ItemPlaceholder.ECONOMY_NAME));

            Map<String, String> map = placeholders.placeholders();
            assertEquals(4, map.size(), map.toString());
            assertEquals("Seller", map.get("seller"));
            assertEquals("10$", map.get("price"));
            assertEquals("buyer-status", map.get("status"), "le spectateur n'est pas le vendeur");
            assertEquals("vault", map.get("economy-name"));
            assertEquals("Seller sells for 10$", placeholders.parse("%seller% sells for %price%"));
        }

        @Test
        @DisplayName("createPlaceholders(player) resout les onze placeholders d'item")
        void createPlaceholders_all() {
            when(configuration.getItemLore()).thenReturn(itemLore());
            when(configuration.getTime()).thenReturn(timeConfiguration());
            when(configuration.getDateFormat()).thenReturn(new SimpleDateFormat("yyyy"));
            EconomyManager economyManager = plugin.getEconomyManager();
            when(economyManager.format(economy, BigDecimal.TEN)).thenReturn("10$");
            for (PriceFormat priceFormat : PriceFormat.values()) {
                when(economyManager.format(priceFormat, BigDecimal.TEN)).thenReturn(priceFormat.name());
            }
            Player viewer = MockPlugins.player(seller, "Seller");
            ZAuctionItem item = item(System.currentTimeMillis() + 60_000L);

            Map<String, String> map = item.createPlaceholders(viewer).placeholders();

            assertEquals(Set.of("economy-name", "economy-display-name", "seller", "status", "price", "price-price-raw",
                    "price-price-with-decimal-format", "price-price-with-reduction", "price-price-without-decimal",
                    "time-remaining", "formatted-expire-date"), map.keySet());
            assertEquals("seller-status", map.get("status"), "le spectateur est le vendeur");
            assertEquals("PRICE_RAW", map.get("price-price-raw"));
            assertEquals("PRICE_WITH_REDUCTION", map.get("price-price-with-reduction"));
            assertEquals("vault", map.get("economy-display-name"));
            assertFalse(map.get("time-remaining").isBlank());
            assertEquals(4, map.get("formatted-expire-date").length());
        }
    }
}
