package fr.maxlego08.zauctionhouse.configuration;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandArgumentConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.InventoryCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.SimpleCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.arguments.CommandSellArguments;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.MaintenanceConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceDebugConfiguration;
import fr.maxlego08.zauctionhouse.api.filter.SearchFilterType;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.ItemPlaceholder;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MainConfigurationTest {

    @TempDir
    File dataFolder;

    private MainConfiguration loadFrom(YamlConfiguration config) {
        AuctionPlugin plugin = ConfigTestSupport.configPlugin(this.dataFolder, config);
        MainConfiguration configuration = new MainConfiguration(plugin);
        configuration.load();
        return configuration;
    }

    private MainConfiguration loadResource(String path) {
        return loadFrom(ConfigTestSupport.loadResource(path));
    }

    @Nested
    @DisplayName("config.yml de production (anglais)")
    class EnglishConfig {

        private MainConfiguration configuration;

        @BeforeEach
        void load() {
            this.configuration = loadResource("config.yml");
        }

        @Test
        @DisplayName("valeurs racines : serveur, debug, dates, decimales")
        void rootValues() {
            assertEquals("skyblock", configuration.getServerName());
            assertFalse(configuration.isEnableDebug());
            assertFalse(configuration.isEnablePerformanceDebug());
            assertEquals("dd/MM/yyyy HH:mm:ss", configuration.getDateFormat().toPattern());
            assertEquals(TimeZone.getDefault().getID(), configuration.getDateFormat().getTimeZone().getID(), "timezone auto = fuseau de la JVM");
            assertTrue(configuration.isAllowDecimalPrices());
            assertTrue(configuration.isSaveProfileOnSell());
            assertFalse(configuration.isSellInventoryEnabled());
        }

        @Test
        @DisplayName("message-colors : les quatre couleurs sont chargees par reflexion")
        void messageColors_loaded() {
            var colors = configuration.getMessageColors();

            assertEquals(4, colors.size());
            assertTrue(colors.stream().anyMatch(color -> "<primary>".equals(color.key()) && "#24d65d".equals(color.color())),
                    "la couleur <primary> doit etre lue avec ses deux composants : " + colors);
        }

        @Test
        @DisplayName("expirations : valeurs par defaut et paliers de permission")
        void expirations() {
            ExpirationConfiguration sell = configuration.getSellExpiration();
            assertEquals(172_800L, sell.defaultExpiration());
            assertFalse(sell.enablePermission());
            assertEquals(Map.of(
                    "zauctionhouse.expiration.vip", 3600L,
                    "zauctionhouse.expiration.elite", 7200L,
                    "zauctionhouse.expiration.legend", 259_200L), sell.expirations());

            assertEquals(172_800L, configuration.getBidExpiration().defaultExpiration());
            assertEquals(172_800L, configuration.getRentExpiration().defaultExpiration());
            assertEquals(604_800L, configuration.getExpireExpiration().defaultExpiration());
            assertEquals(604_800L, configuration.getPurchaseExpiration().defaultExpiration());
            assertTrue(configuration.getExpireExpiration().expirations().isEmpty());
        }

        @Test
        @DisplayName("tri : DECREASING_DATE par defaut et quatre traductions")
        void sort() {
            assertEquals(SortItem.DECREASING_DATE, configuration.getSort().defaultSort());
            assertEquals(SortItem.values().length, configuration.getSort().sortItems().size());
            for (SortItem sortItem : SortItem.values()) {
                String translation = configuration.getSort().sortItems().get(sortItem);
                assertNotNull(translation);
                assertFalse(translation.equals(sortItem.name()), "la traduction ne doit pas etre le nom brut de l'enum");
            }
        }

        @Test
        @DisplayName("permissions : limites 5/10/15 pour chaque type")
        void permissions() {
            for (ItemType itemType : ItemType.values()) {
                Map<String, Integer> limits = configuration.getPermission().permissions().get(itemType);
                assertEquals(Map.of("zauctionhouse.max.5", 5, "zauctionhouse.max.10", 10, "zauctionhouse.max.15", 15), limits);
            }
            var player = ConfigTestSupport.playerWithPermissions("Vip", "zauctionhouse.max.5", "zauctionhouse.max.15");
            assertEquals(15, configuration.getPermission().getLimit(ItemType.AUCTION, player));
            assertEquals(0, configuration.getPermission().getLimit(ItemType.AUCTION, ConfigTestSupport.playerWithPermissions("Nobody")));
        }

        @Test
        @DisplayName("mondes bannis : world_the_end pour chaque type")
        void worlds() {
            for (ItemType itemType : ItemType.values()) {
                assertEquals(List.of("world_the_end"), configuration.getWorld().worlds().get(itemType));
                assertTrue(configuration.getWorld().isWorldBanned(itemType, "world_the_end"));
                assertFalse(configuration.getWorld().isWorldBanned(itemType, "world"));
            }
        }

        @Test
        @DisplayName("raccourcis de prix : K, M, B, T, Q sont actifs")
        void priceShortcuts() {
            var multiplication = configuration.getNumberMultiplicationConfiguration();

            assertTrue(multiplication.enable());
            assertEquals(14, multiplication.multiplications().size());
            assertEquals(0, new BigDecimal("1000").compareTo(multiplication.parseNumber("1k")));
            assertEquals(0, new BigDecimal("2500000").compareTo(multiplication.parseNumber("2.5M")));
            assertEquals(0, new BigDecimal("1000000000").compareTo(multiplication.parseNumber("1b")));
            assertEquals(0, new BigDecimal("1000000000000").compareTo(multiplication.parseNumber("1T")));
            assertEquals(0, new BigDecimal("1000000000000000").compareTo(multiplication.parseNumber("1q")));
        }

        @Test
        @DisplayName("temps : libelles et gabarits")
        void time() {
            var time = configuration.getTime();

            assertEquals("second", time.second());
            assertEquals("days", time.days());
            assertEquals("%02dᴅ %02dʜ %02dᴍ", time.timeDay());
            assertEquals("%02ds", time.timeSecond());
            assertEquals("01ᴍ 30s", time.getStringTime(90_000L));
        }

        @Test
        @DisplayName("actions : apercu de conteneur, retrait, achat sans argent")
        void actions() {
            var actions = configuration.getActions();

            assertTrue(actions.updateInventoryOnAction());
            assertTrue(actions.resetCategoryOnOpen());
            assertTrue(actions.resetSearchOnOpen());
            assertTrue(actions.containerPreview().enable());
            assertEquals(ClickType.RIGHT, actions.containerPreview().click());
            assertFalse(actions.listed().giveItem());
            assertTrue(actions.listed().openInventory());
            assertTrue(actions.listed().openConfirmInventory());
            assertTrue(actions.expired().openInventory());
            assertTrue(actions.expired().freeSpace());
            assertTrue(actions.selling().openInventory());
            assertTrue(actions.selling().freeSpace());
            assertFalse(actions.purchased().giveItem());
            assertTrue(actions.purchased().openInventory());
            assertTrue(actions.purchased().freeSpace());
            assertTrue(actions.purchased().sendNoMoneyMessage());
            assertTrue(actions.purchased().noMoney().enable());
            assertEquals(60, actions.purchased().noMoney().duration());
            assertNotNull(actions.purchased().noMoney().menuItemStack(), "l'item 'pas assez d'argent' est charge via zMenu");
            assertTrue(actions.purchased().noMoneySound().enable());
            assertEquals("minecraft:entity.villager.no", actions.purchased().noMoneySound().sound());
            assertEquals(1f, actions.purchased().noMoneySound().volume(), 1e-6);
            assertEquals(1f, actions.purchased().noMoneySound().pitch(), 1e-6);
        }

        @Test
        @DisplayName("lore des items : listes et placeholders detectes")
        void itemLore() {
            var lore = configuration.getItemLore();

            assertFalse(lore.forceAmountOne());
            assertEquals(6, lore.listedAuctionLore().size());
            assertTrue(lore.listedAuctionPlaceholders().containsAll(List.of(ItemPlaceholder.SELLER, ItemPlaceholder.PRICE, ItemPlaceholder.TIME_REMAINING, ItemPlaceholder.STATUS)));
            assertFalse(lore.listedAuctionPlaceholders().contains(ItemPlaceholder.ITEM_COUNT));
            assertEquals(4, lore.expiredLore().size());
            assertEquals(List.of(ItemPlaceholder.TIME_REMAINING), List.copyOf(lore.expiredPlaceholders()));
            assertEquals("Item Listed", lore.getLogTypeName(LogType.SALE));
            assertEquals("Retrieved Purchase", lore.getLogTypeName(LogType.REMOVE_PURCHASED));
            assertTrue(lore.sellerStatus().contains("ᴄʟɪᴄᴋ"));
        }

        @Test
        @DisplayName("affichage des items, items speciaux")
        void itemDisplayAndSpecialItems() {
            assertEquals("#8ee6e3x%amount% &7<lang:%item-translation-key%>", configuration.getItemDisplay().langDisplay());
            assertEquals("#8ee6e3x%amount% &7%item-name%", configuration.getItemDisplay().itemNameDisplay());
            assertEquals(" &fand ", configuration.getItemDisplay().and());
            assertEquals("&f, ", configuration.getItemDisplay().between());
            assertTrue(configuration.getItemDisplay().mergeSimilar());
            assertNotNull(configuration.getSpecialItems().auctionItem());
        }

        @Test
        @DisplayName("auto-claim, historique, notifications, broadcast")
        void miscSections() {
            assertTrue(configuration.getAutoClaimConfiguration().enabled());
            assertEquals(20L, configuration.getAutoClaimConfiguration().delayTicks());
            assertTrue(configuration.getAutoClaimConfiguration().notifyPlayer());
            assertTrue(configuration.getAutoClaimConfiguration().notifyPending());
            assertEquals(40L, configuration.getAutoClaimConfiguration().notifyDelayTicks());
            assertEquals("Claimed pending auction money", configuration.getAutoClaimConfiguration().depositReason());

            assertEquals(500, configuration.getHistory().maxEntries());
            assertEquals(0, configuration.getHistory().expireAfterDays());

            assertTrue(configuration.getSalesNotificationConfiguration().enabled());
            assertEquals(60L, configuration.getSalesNotificationConfiguration().delayTicks());

            assertFalse(configuration.getBroadcast().sellEnabled());
            assertFalse(configuration.getBroadcast().purchaseEnabled());
            assertTrue(configuration.getBroadcast().excludeSeller());
            assertTrue(configuration.getBroadcast().excludeBuyer());
            assertTrue(configuration.getBroadcast().categoryMessagesSell().isEmpty());
            assertTrue(configuration.getBroadcast().categoryMessagesPurchase().isEmpty());
        }

        @Test
        @DisplayName("performance, maintenance, debug de performance")
        void performanceSections() {
            var performance = configuration.getPerformance();
            assertEquals(10_000, performance.parallelSortThreshold());
            assertEquals(5_000, performance.parallelCategoryThreshold());
            assertEquals(-1, performance.parallelism());
            assertTrue(performance.getEffectiveParallelism() >= 2);
            assertEquals(5_000L, performance.checkAvailabilityTimeoutMs());
            assertEquals(5_000L, performance.lockItemTimeoutMs());
            assertEquals(3_000L, performance.unlockItemTimeoutMs());
            assertEquals(3_000L, performance.notifyStatusChangeTimeoutMs());
            assertEquals(5_000L, performance.notifyItemActionTimeoutMs());

            MaintenanceConfiguration maintenance = configuration.getMaintenance();
            assertEquals(60L, maintenance.expirationSweepIntervalSeconds());
            assertEquals(50, maintenance.expirationSweepBatchSize());
            assertEquals(60L, maintenance.confirmationTimeoutSeconds());
            assertEquals(10L, maintenance.confirmationSweepIntervalSeconds());
            assertTrue(maintenance.isExpirationSweepEnabled());
            assertTrue(maintenance.isConfirmationSweepEnabled());

            assertEquals(PerformanceDebugConfiguration.FilterMode.DISABLED, configuration.getPerformanceDebug().mode());
            assertEquals(List.of("loadItems.*", "SortedItemsCache.*"), configuration.getPerformanceDebug().operations());
        }

        @Test
        @DisplayName("filtres de recherche et cooldown")
        void searchAndCooldown() {
            assertEquals("~", configuration.getSearchFilter().getOperator(SearchFilterType.CONTAINS));
            assertEquals("==", configuration.getSearchFilter().getOperator(SearchFilterType.EQUALS_IGNORE_CASE));

            assertEquals(100L, configuration.getCooldown().defaultCooldown());
            assertEquals("zauctionhouse.cooldown.command.bypass", configuration.getCooldown().bypassPermission());
            assertTrue(configuration.getCooldown().perCommandCooldowns().isEmpty());
            assertEquals(100L, configuration.getCooldown().getCooldownForCommand("sell"));
        }

        @Test
        @DisplayName("commandes d'inventaire : selling, expired, purchased, history")
        void inventoryCommands() {
            List<InventoryCommandConfiguration> commands = configuration.getInventoryCommands();

            assertEquals(4, commands.size());
            assertEquals(List.of(Inventories.SELLING_ITEMS, Inventories.EXPIRED_ITEMS, Inventories.PURCHASED_ITEMS, Inventories.HISTORY),
                    commands.stream().map(InventoryCommandConfiguration::inventories).toList());
            InventoryCommandConfiguration selling = commands.get(0);
            assertEquals("zauctionhouse.selling", selling.permission());
            assertEquals("Open selling items", selling.description());
            assertEquals(List.of("selling"), selling.aliases());
            assertTrue(selling.enablePage());
            assertEquals("page", selling.pageName());
        }

        @Test
        @DisplayName("loadCommandConfiguration : la commande sell et ses trois arguments types")
        void loadCommandConfiguration_sell() {
            CommandConfiguration<CommandSellArguments> sell = configuration.loadCommandConfiguration("commands.sell.", CommandSellArguments.class);

            assertEquals(List.of("sell", "s", "vendre"), sell.aliases());
            assertEquals(3, sell.arguments().size());

            CommandArgumentConfiguration<CommandSellArguments> price = sell.arguments().get(0);
            assertEquals(CommandSellArguments.PRICE, price.name());
            assertEquals("price", price.displayName());
            assertTrue(price.required());
            assertEquals(List.of("1000", "10000", "25000"), price.autoCompletion(), "les completions numeriques sont converties en chaines");
            assertNull(price.defaultValue());

            CommandArgumentConfiguration<CommandSellArguments> amount = sell.arguments().get(1);
            assertEquals(CommandSellArguments.AMOUNT, amount.name());
            assertFalse(amount.required());
            assertEquals(List.of("1", "%max-stack-size%"), amount.autoCompletion());

            assertEquals(CommandSellArguments.ECONOMY, sell.arguments().get(2).name());
            assertEquals(List.of("vault"), sell.arguments().get(2).autoCompletion());
        }

        @Test
        @DisplayName("loadSimpleCommandConfiguration et loadCommandAliases")
        void simpleCommandAndAliases() {
            SimpleCommandConfiguration page = configuration.loadSimpleCommandConfiguration("commands.page.");
            assertEquals(List.of("page", "p"), page.aliases());
            assertEquals(1, page.arguments().size());
            assertEquals("page", page.arguments().get(0).name());
            assertEquals("page", page.arguments().get(0).displayName());
            assertTrue(page.arguments().get(0).required());
            assertEquals(List.of("1", "2", "3", "5", "10"), page.arguments().get(0).autoCompletion());

            assertEquals(List.of("ah", "hdv", "auction", "zauction"), configuration.loadCommandAliases("commands.main-command."));
            assertEquals(List.of("claim", "c", "recuperer"), configuration.loadCommandAliases("commands.claim."));
            assertTrue(configuration.loadCommandAliases("commands.unknown.").isEmpty());
        }
    }

    @Nested
    @DisplayName("traductions")
    class Translations {

        @ParameterizedTest(name = "{0}/config.yml se charge sans erreur et garde les valeurs structurantes")
        @ValueSource(strings = {"fr", "es", "it", "id", "th"})
        void translatedConfig_loads(String language) {
            MainConfiguration configuration = assertDoesNotThrow(() -> loadResource(language + "/config.yml"));

            assertEquals("skyblock", configuration.getServerName());
            assertEquals(SortItem.DECREASING_DATE, configuration.getSort().defaultSort());
            assertTrue(configuration.isAllowDecimalPrices());
            assertEquals(172_800L, configuration.getSellExpiration().defaultExpiration());
            assertEquals(604_800L, configuration.getExpireExpiration().defaultExpiration());
            assertEquals(100L, configuration.getCooldown().defaultCooldown());
            assertEquals(4, configuration.getInventoryCommands().size());
            assertTrue(configuration.getNumberMultiplicationConfiguration().enable());
            assertEquals(0, new BigDecimal("1000").compareTo(configuration.getNumberMultiplicationConfiguration().parseNumber("1k")));
            assertEquals(SortItem.values().length, configuration.getSort().sortItems().size());
            assertNotNull(configuration.getSpecialItems().auctionItem());
            assertNotNull(configuration.getItemLore().listedAuctionLore());
            assertFalse(configuration.getItemLore().listedAuctionLore().isEmpty());
            assertEquals(3, configuration.loadCommandConfiguration("commands.sell.", CommandSellArguments.class).arguments().size());
        }
    }

    @Nested
    @DisplayName("configuration minimale / degradee")
    class MinimalConfig {

        @Test
        @DisplayName("configuration vide : chargement sans exception et valeurs par defaut")
        void emptyConfig_loadsWithDefaults() {
            MainConfiguration configuration = assertDoesNotThrow(() -> loadFrom(new YamlConfiguration()));

            assertNull(configuration.getServerName());
            assertFalse(configuration.isEnableDebug());
            assertEquals("dd/MM/yyyy HH:mm:ss", configuration.getDateFormat().toPattern());
            assertEquals(0L, configuration.getSellExpiration().defaultExpiration());
            assertEquals(SortItem.DECREASING_DATE, configuration.getSort().defaultSort(), "tri par defaut de secours");
            assertEquals("DECREASING_PRICE", configuration.getSort().sortItems().get(SortItem.DECREASING_PRICE), "traduction de secours = nom de l'enum");
            assertTrue(configuration.getInventoryCommands().isEmpty());
            assertTrue(configuration.getMessageColors().isEmpty());
            assertFalse(configuration.getNumberMultiplicationConfiguration().enable());
            assertEquals(MaintenanceConfiguration.defaults(), configuration.getMaintenance());
            assertEquals(ClickType.RIGHT, configuration.getActions().containerPreview().click());
            assertFalse(configuration.getActions().purchased().noMoney().enable());
            assertNull(configuration.getActions().purchased().noMoney().menuItemStack());
            assertTrue(configuration.isSaveProfileOnSell(), "save-profile-on-sell vaut true par defaut");
            assertTrue(configuration.isAllowDecimalPrices(), "allow-decimal-prices vaut true par defaut");
            assertFalse(configuration.isSellInventoryEnabled());
            assertTrue(configuration.getPermission().permissions().get(ItemType.AUCTION).isEmpty());
            assertTrue(configuration.getWorld().worlds().get(ItemType.BID).isEmpty());
        }

        @Test
        @DisplayName("getMaintenance avant load() rend les valeurs par defaut")
        void maintenance_beforeLoad_defaults() {
            MainConfiguration configuration = new MainConfiguration(ConfigTestSupport.configPlugin(dataFolder, new YamlConfiguration()));

            assertEquals(MaintenanceConfiguration.defaults(), configuration.getMaintenance());
        }

        @Test
        @DisplayName("timezone explicite appliquee au format de date")
        void explicitTimezone_applied() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("timezone", "Europe/Paris");
            config.set("date-format", "yyyy-MM-dd");

            MainConfiguration configuration = loadFrom(config);

            assertEquals("yyyy-MM-dd", configuration.getDateFormat().toPattern());
            assertEquals("Europe/Paris", configuration.getDateFormat().getTimeZone().getID());
        }

        @Test
        @DisplayName("champs racines lus par reflexion : enable-debug, server-name")
        void reflectiveRootFields() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("enable-debug", true);
            config.set("enable-performance-debug", true);
            config.set("server-name", "lobby-1");

            MainConfiguration configuration = loadFrom(config);

            assertTrue(configuration.isEnableDebug());
            assertTrue(configuration.isEnablePerformanceDebug());
            assertEquals("lobby-1", configuration.getServerName());
        }

        @Test
        @DisplayName("loadCommandConfiguration ignore les arguments sans nom ou hors enum")
        void loadCommandConfiguration_skipsInvalidArguments() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("commands.sell.aliases", List.of("sell"));
            config.set("commands.sell.arguments", List.of(
                    Map.of("name", "price", "required", true, "auto-completion", List.of(10, 20), "default-value", "100"),
                    Map.of("display-name", "no-name", "auto-completion", List.of()),
                    Map.of("name", "color", "display-name", "pas-dans-l-enum", "auto-completion", List.of()),
                    Map.of("name", "economy", "auto-completion", List.of())));

            CommandConfiguration<CommandSellArguments> sell = loadFrom(config).loadCommandConfiguration("commands.sell.", CommandSellArguments.class);

            assertEquals(2, sell.arguments().size());
            assertEquals(CommandSellArguments.PRICE, sell.arguments().get(0).name());
            assertEquals("price", sell.arguments().get(0).displayName(), "display-name par defaut = name");
            assertEquals("100", sell.arguments().get(0).defaultValue());
            assertEquals(List.of("10", "20"), sell.arguments().get(0).autoCompletion());
            assertEquals(CommandSellArguments.ECONOMY, sell.arguments().get(1).name());
            assertFalse(sell.arguments().get(1).required());
            assertTrue(sell.arguments().get(1).autoCompletion().isEmpty());
        }

        @Test
        @DisplayName("loadSimpleCommandConfiguration lit nom, display-name, required et completion")
        void loadSimpleCommandConfiguration_readsArguments() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("commands.page.arguments", List.of(
                    Map.of("name", "page", "display-name", "numero", "required", true, "auto-completion", List.of(1, 2)),
                    Map.of("name", "filter", "auto-completion", List.of())));

            SimpleCommandConfiguration page = loadFrom(config).loadSimpleCommandConfiguration("commands.page.");

            assertTrue(page.aliases().isEmpty());
            assertEquals(2, page.arguments().size());
            assertEquals("page", page.arguments().get(0).name());
            assertEquals("numero", page.arguments().get(0).displayName());
            assertTrue(page.arguments().get(0).required());
            assertEquals(List.of("1", "2"), page.arguments().get(0).autoCompletion());
            assertEquals("filter", page.arguments().get(1).displayName(), "display-name par defaut = name");
            assertFalse(page.arguments().get(1).required());
        }

        @Test
        @DisplayName("loadSimpleCommandConfiguration ignore les arguments sans nom")
        @Disabled("BUG: MainConfiguration.loadSimpleCommandConfiguration (MainConfiguration.java:359-362) teste accessor.getString(\"name\") == null mais TypedMapAccessor.getString(String) rend la chaine \"null\" pour une cle absente : l'argument est accepte avec le nom litteral \"null\"")
        void loadSimpleCommandConfiguration_skipsNameless() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("commands.page.arguments", List.of(
                    Map.of("display-name", "x", "auto-completion", List.of()),
                    Map.of("name", "page", "auto-completion", List.of())));

            SimpleCommandConfiguration page = loadFrom(config).loadSimpleCommandConfiguration("commands.page.");

            assertEquals(1, page.arguments().size());
            assertEquals("page", page.arguments().get(0).name());
        }

        @Test
        @DisplayName("un argument sans cle auto-completion est accepte avec une completion vide")
        @Disabled("BUG: MainConfiguration.loadCommandConfiguration (MainConfiguration.java:335) et loadSimpleCommandConfiguration (:367) appellent accessor.getList(\"auto-completion\").stream() alors que TypedMapAccessor.getList rend null quand la cle est absente : NullPointerException au chargement")
        void missingAutoCompletion_isTreatedAsEmpty() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("commands.sell.arguments", List.of(Map.of("name", "price")));
            config.set("commands.page.arguments", List.of(Map.of("name", "page")));
            MainConfiguration configuration = loadFrom(config);

            CommandConfiguration<CommandSellArguments> sell = assertDoesNotThrow(() -> configuration.loadCommandConfiguration("commands.sell.", CommandSellArguments.class));
            SimpleCommandConfiguration page = assertDoesNotThrow(() -> configuration.loadSimpleCommandConfiguration("commands.page."));

            assertTrue(sell.arguments().get(0).autoCompletion().isEmpty());
            assertTrue(page.arguments().get(0).autoCompletion().isEmpty());
        }

        @Test
        @DisplayName("les getters rendent toujours la meme instance de record apres load()")
        void getters_stableInstances() {
            MainConfiguration configuration = loadFrom(new YamlConfiguration());

            assertSame(configuration.getSort(), configuration.getSort());
            assertSame(configuration.getPermission(), configuration.getPermission());
            assertSame(configuration.getActions(), configuration.getActions());
        }
    }
}
