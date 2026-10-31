package fr.maxlego08.zauctionhouse.economy;

import fr.maxlego08.menu.api.font.FontImage;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.NumberFormatReduction;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.tax.ItemTaxRule;
import fr.maxlego08.zauctionhouse.api.tax.TaxAmountType;
import fr.maxlego08.zauctionhouse.api.tax.TaxConfiguration;
import fr.maxlego08.zauctionhouse.api.tax.TaxReduction;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.traqueur.currencies.Currencies;
import fr.traqueur.currencies.CurrencyProvider;
import fr.traqueur.currencies.providers.LevelProvider;
import fr.traqueur.currencies.providers.ZMenuItemProvider;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZEconomyManagerTest {

    private static Locale defaultLocale;

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private ZEconomyManager manager;

    @BeforeAll
    static void fixLocale() {
        // DecimalFormat et String.format dependent de la locale par defaut : on la fige.
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.configPlugin(this.dataFolder, MockPlugins.sqliteConfig());
        this.manager = new ZEconomyManager(this.plugin);
    }

    // ---------------------------------------------------------------- helpers

    private File economiesFile() {
        return new File(this.dataFolder, "economies.yml");
    }

    private void writeEconomies(String yaml) throws IOException {
        Files.writeString(economiesFile().toPath(), yaml, StandardCharsets.UTF_8);
    }

    /**
     * {@code loadEconomies} declenche {@code AuctionLoadEconomyEvent.callEvent()}, qui passe par
     * {@code Bukkit.getPluginManager()} : le statique est mocke le temps du chargement.
     */
    private void load() {
        PluginManager pluginManager = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            this.manager.loadEconomies();
        }
        verify(pluginManager).callEvent(any());
    }

    private ZAuctionEconomy economy(String name, PriceFormat priceFormat, String format) {
        CurrencyProvider provider = mock(CurrencyProvider.class);
        return new ZAuctionEconomy(this.plugin, provider, Currencies.VAULT, name, name, format, "$", null, "d", "w",
                priceFormat, new EnumMap<>(ItemType.class), new EnumMap<>(ItemType.class), true, false, null);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "attendu " + expected + " mais obtenu " + actual);
    }

    private static final String LEVELS_ECONOMY = """
            economies:
              - type: LEVEL
                is-enable: true
                name: levels
                display-name: "Levels"
                format: "%price% lvl%s%"
                symbol: "L"
                auto-claim: false
                must-be-online: false
                permission: "zauctionhouse.economy.levels"
                withdraw-reason: "W"
                deposit-reason: "D"
                price-format: price_without_decimal
                min-prices:
                  auction: 1
                  bid: 2.5
                max-prices: 100
                tax:
                  enabled: true
                  type: purchase
                  amount-type: fixed
                  amount: 12.5
                  bypass-permission: "zauctionhouse.tax.bypass"
                  reductions:
                    - permission: "zauctionhouse.tax.vip"
                      percentage: 50
                    - permission: ""
                      percentage: 20
                    - permission: "zauctionhouse.tax.zero"
                      percentage: 0
                    - permission: "zauctionhouse.tax.premium"
                      percentage: 25
                  item-rules:
                    enabled: true
                    rules:
                      - name: "diamond_tax"
                        priority: 100
                        type: CAPITALISM
                        amount-type: PERCENTAGE
                        amount: 10
                        rule:
                          type: material
                          materials: [DIAMOND]
                      - priority: 5
                        rule:
                          type: material
                      - name: "no_rule"
                        priority: 1
                      - name: "invalid_types"
                        priority: 2
                        type: WHATEVER
                        amount-type: NOPE
                        amount: 3
                        rule:
                          type: unknown
              - type: EXPERIENCE
                is-enable: false
                name: xp
              - is-enable: true
                display-name: "No name"
              - type: REDIRECT
                is-enable: true
                name: redirect
              - type: LEVEL
                is-enable: true
                name: badformat
                price-format: PRICE_WHATEVER
              - type: ITEM
                is-enable: true
                name: noitem
              - type: COINSENGINE
                is-enable: true
                name: nocurrency
            default-economy:
              auction: levels
              bid: LEVELS
            price-decimal-format: "#,##0.00"
            price-reductions:
              - format: "#.#"
                max-amount: 1000
              - format: "%.1fK"
                max-amount: 1000000
              - format: "%.1fM"
                max-amount: abc
              - format: "%.1fB"
            """;

    // ---------------------------------------------------------------- registre

    @Nested
    @DisplayName("registre d'economies")
    class Registry {

        @Test
        @DisplayName("registerEconomy ajoute une economie inconnue et la retrouve")
        void register_newEconomy_added() {
            ZAuctionEconomy vault = economy("vault", PriceFormat.PRICE_RAW, "%price%$");

            assertTrue(manager.registerEconomy(vault));
            assertEquals(1, manager.getEconomies().size());
            assertSame(vault, manager.getEconomy("vault").orElseThrow());
        }

        @Test
        @DisplayName("registerEconomy refuse un nom deja pris, sans tenir compte de la casse")
        void register_duplicateName_rejected() {
            manager.registerEconomy(economy("vault", PriceFormat.PRICE_RAW, "%price%$"));

            assertFalse(manager.registerEconomy(economy("VAULT", PriceFormat.PRICE_RAW, "%price%$")));
            assertFalse(manager.registerEconomy(economy("vault", PriceFormat.PRICE_RAW, "%price%$")));
            assertEquals(1, manager.getEconomies().size());
        }

        @Test
        @DisplayName("getEconomy est insensible a la casse et rend vide pour un inconnu")
        void getEconomy_caseInsensitive() {
            ZAuctionEconomy vault = economy("Vault", PriceFormat.PRICE_RAW, "%price%$");
            manager.registerEconomy(vault);

            assertSame(vault, manager.getEconomy("vault").orElseThrow());
            assertSame(vault, manager.getEconomy("VAULT").orElseThrow());
            assertEquals(Optional.empty(), manager.getEconomy("points"));
        }

        @Test
        @DisplayName("removeEconomy rend true puis false")
        void remove_thenAbsent() {
            ZAuctionEconomy vault = economy("vault", PriceFormat.PRICE_RAW, "%price%$");
            manager.registerEconomy(vault);

            assertTrue(manager.removeEconomy(vault));
            assertFalse(manager.removeEconomy(vault));
            assertTrue(manager.getEconomy("vault").isEmpty());
            assertTrue(manager.getEconomies().isEmpty());
        }

        @Test
        @DisplayName("getEconomies est une vue non modifiable")
        void getEconomies_unmodifiable() {
            ZAuctionEconomy vault = economy("vault", PriceFormat.PRICE_RAW, "%price%$");
            var economies = manager.getEconomies();

            assertThrows(UnsupportedOperationException.class, () -> economies.add(vault));
        }

        @Test
        @DisplayName("avant chargement : pas d'economie par defaut, pas de format decimal, pas de reductions")
        void beforeLoad_emptyState() {
            assertNull(manager.getDefaultEconomy(ItemType.AUCTION));
            assertNull(manager.getPriceDecimalFormat());
            assertTrue(manager.getPriceReductions().isEmpty());
        }
    }

    // ---------------------------------------------------------------- chargement

    @Nested
    @DisplayName("loadEconomies")
    class Loading {

        @Test
        @DisplayName("fichier absent : saveFile est demande, rien n'est charge, format decimal par defaut")
        void missingFile_savesDefaultsAndLoadsNothing() {
            load();

            verify(plugin).saveFile("economies.yml", false);
            assertTrue(manager.getEconomies().isEmpty());
            assertNull(manager.getDefaultEconomy(ItemType.AUCTION));
            assertEquals(new DecimalFormat("#,###.#").toPattern(), manager.getPriceDecimalFormat().toPattern());
            assertEquals("1,234.5", manager.format(PriceFormat.PRICE_WITH_DECIMAL_FORMAT, 1234.5));
            assertTrue(manager.getPriceReductions().isEmpty());
        }

        @Test
        @DisplayName("une reduction sans 'format' est ignoree")
        @Disabled("BUG: ZEconomyManager.loadConfiguration teste accessor.getString(\"format\") == null mais TypedMapAccessor.getString(String) rend la chaine \"null\" pour une cle absente (ZEconomyManager.java:97-104) : la reduction est enregistree avec le format litteral \"null\" et s'affiche 'null'")
        void reductionWithoutFormat_isSkipped() throws IOException {
            writeEconomies("""
                    economies: []
                    price-reductions:
                      - max-amount: 5
                      - format: "%.1fK"
                        max-amount: 1000000
                    """);

            load();

            assertEquals(1, manager.getPriceReductions().size());
            assertEquals("%.1fK", manager.getPriceReductions().get(0).format());
        }

        @Test
        @DisplayName("fichier present : saveFile n'est pas appele")
        void existingFile_noSave() throws IOException {
            writeEconomies("economies: []\n");

            load();

            verify(plugin, never()).saveFile(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("seules les economies valides et actives sont chargees")
        void onlyValidEnabledEconomiesLoaded() throws IOException {
            writeEconomies(LEVELS_ECONOMY);

            load();

            assertEquals(1, manager.getEconomies().size(), "levels seule : xp desactivee, sans nom, REDIRECT inconnu, format invalide, ITEM sans item, COINSENGINE sans currency-name");
            assertTrue(manager.getEconomy("levels").isPresent());
            for (String rejected : List.of("xp", "redirect", "badformat", "noitem", "nocurrency")) {
                assertTrue(manager.getEconomy(rejected).isEmpty(), rejected + " ne doit pas etre chargee");
            }
        }

        @Test
        @DisplayName("les attributs de l'economie sont lus et les enums acceptent la casse libre")
        void economyAttributes_loaded() throws IOException {
            writeEconomies(LEVELS_ECONOMY);

            load();

            ZAuctionEconomy levels = (ZAuctionEconomy) manager.getEconomy("levels").orElseThrow();
            assertEquals("levels", levels.getName());
            assertEquals("Levels", levels.getDisplayName());
            assertEquals("%price% lvl%s%", levels.getFormat());
            assertEquals("L", levels.getSymbol());
            assertFalse(levels.isAutoClaim());
            assertEquals("zauctionhouse.economy.levels", levels.getPermission());
            assertEquals("W", levels.getWithdrawReason());
            assertEquals("D", levels.getDepositReason());
            assertEquals(PriceFormat.PRICE_WITHOUT_DECIMAL, levels.getPriceFormat());
            assertEquals(Currencies.LEVEL, levels.getCurrencies());
            assertInstanceOf(LevelProvider.class, levels.getCurrencyProvider());
            assertSame(plugin, levels.getPlugin());
        }

        @Test
        @DisplayName("C-032 : must-be-online est force a true pour une economie LEVEL")
        void playerBackedEconomy_forcesMustBeOnline() throws IOException {
            writeEconomies(LEVELS_ECONOMY);

            load();

            AuctionEconomy levels = manager.getEconomy("levels").orElseThrow();
            assertTrue(levels.mustBeOnline(), "le fichier dit false, le code doit forcer true");
            assertFalse(levels.supportsOfflineDeposit());
        }

        @Test
        @DisplayName("min-prices en map par type, max-prices en nombre pour tous les types")
        void prices_loaded() throws IOException {
            writeEconomies(LEVELS_ECONOMY);

            load();

            AuctionEconomy levels = manager.getEconomy("levels").orElseThrow();
            assertMoney("1", levels.getMinPrice(ItemType.AUCTION));
            assertMoney("2.5", levels.getMinPrice(ItemType.BID));
            assertNull(levels.getMinPrice(ItemType.RENT));
            for (ItemType itemType : ItemType.values()) {
                assertMoney("100", levels.getMaxPrice(itemType));
            }
        }

        @Test
        @DisplayName("default-economy : resolution insensible a la casse, type non configure => null")
        void defaultEconomies_resolved() throws IOException {
            writeEconomies(LEVELS_ECONOMY);

            load();

            AuctionEconomy levels = manager.getEconomy("levels").orElseThrow();
            assertSame(levels, manager.getDefaultEconomy(ItemType.AUCTION));
            assertSame(levels, manager.getDefaultEconomy(ItemType.BID));
            assertNull(manager.getDefaultEconomy(ItemType.RENT));
        }

        @Test
        @DisplayName("default-economy pointant vers une economie inconnue => null")
        void defaultEconomy_unknownName_null() throws IOException {
            writeEconomies("""
                    economies: []
                    default-economy:
                      auction: ghost
                    """);

            load();

            assertNull(manager.getDefaultEconomy(ItemType.AUCTION));
        }

        @Test
        @DisplayName("format decimal et reductions : les entrees invalides sont ignorees")
        void priceFormatting_loaded() throws IOException {
            writeEconomies(LEVELS_ECONOMY);

            load();

            assertEquals("#,##0.00", manager.getPriceDecimalFormat().toPattern());
            List<NumberFormatReduction> reductions = manager.getPriceReductions();
            assertEquals(2, reductions.size(), "max-amount 'abc' et max-amount manquant sont ignores");
            assertEquals("#.#", reductions.get(0).format());
            assertMoney("1000", reductions.get(0).maxAmount());
            assertEquals("%amount%", reductions.get(0).display(), "display par defaut");
            assertEquals("%.1fK", reductions.get(1).format());
            assertMoney("1000000", reductions.get(1).maxAmount());
        }

        @Test
        @DisplayName("price-decimal-format vide : le format n'est pas remplace")
        void emptyDecimalFormat_skipped() throws IOException {
            writeEconomies("""
                    economies: []
                    price-decimal-format: ""
                    """);

            load();

            assertNull(manager.getPriceDecimalFormat());
            assertTrue(manager.getPriceReductions().isEmpty());
        }

        @Test
        @DisplayName("recharger remplace les economies precedentes")
        void reload_replacesPreviousEconomies() throws IOException {
            writeEconomies(LEVELS_ECONOMY);
            load();
            assertTrue(manager.getEconomy("levels").isPresent());

            writeEconomies("""
                    economies:
                      - type: EXPERIENCE
                        is-enable: true
                        name: xp
                    default-economy:
                      auction: xp
                    """);
            load();

            assertTrue(manager.getEconomy("levels").isEmpty());
            AuctionEconomy xp = manager.getEconomy("xp").orElseThrow();
            assertEquals("xp", xp.getDisplayName(), "display-name par defaut = name");
            assertEquals("$", xp.getSymbol());
            assertEquals("%price%$", xp.getFormat());
            assertEquals(PriceFormat.PRICE_RAW, xp.getPriceFormat());
            assertTrue(xp.isAutoClaim());
            assertTrue(xp.mustBeOnline(), "EXPERIENCE est force en ligne");
            assertNull(xp.getPermission());
            assertFalse(xp.getTaxConfiguration().isEnabled(), "pas de section tax => desactivee");
            assertSame(xp, manager.getDefaultEconomy(ItemType.AUCTION));
        }

        @Test
        @DisplayName("economie ITEM avec item : provider ZMenuItemProvider construit via zMenu")
        void itemEconomy_usesZMenuItemProvider() throws IOException {
            writeEconomies("""
                    economies:
                      - type: item
                        is-enable: true
                        name: gems
                        item:
                          material: DIAMOND
                    """);

            load();

            ZAuctionEconomy gems = (ZAuctionEconomy) manager.getEconomy("gems").orElseThrow();
            assertEquals(Currencies.ITEM, gems.getCurrencies());
            assertInstanceOf(ZMenuItemProvider.class, gems.getCurrencyProvider());
            assertTrue(gems.mustBeOnline());
            verify(plugin.getInventoriesLoader().getInventoryManager()).loadItemStack(any(File.class), any(String.class), anyMap());
        }

        @Test
        @DisplayName("type sans provider auto-creable (VAULT) et plugin absent : l'economie est ignoree, pas l'ensemble du chargement")
        @Disabled("BUG: Currencies.VAULT.createProvider() leve une RuntimeException quand Vault est absent et ZEconomyManager.loadEconomy ne l'attrape pas (ZEconomyManager.java:345) : toutes les economies suivantes et les reductions ne sont jamais chargees")
        void vaultEconomy_withoutVault_isSkippedNotFatal() throws IOException {
            YamlConfiguration real = ConfigTestSupport.loadResource("economies.yml");
            real.save(economiesFile());

            assertDoesNotThrow(this::loadQuietly);
            assertTrue(manager.getEconomy("vault").isEmpty());
            assertEquals(6, manager.getPriceReductions().size());
        }

        private void loadQuietly() {
            load();
        }
    }

    // ---------------------------------------------------------------- taxes

    @Nested
    @DisplayName("chargement de la configuration de taxe")
    class TaxLoading {

        @BeforeEach
        void stubRuleRegistry() throws IOException {
            Rule materialRule = mock(Rule.class);
            when(plugin.getRuleLoaderRegistry().loadRule(any())).thenAnswer(invocation -> {
                Map<?, ?> map = invocation.getArgument(0);
                return "material".equals(map.get("type")) ? materialRule : null;
            });
            writeEconomies(LEVELS_ECONOMY);
            load();
        }

        private TaxConfiguration taxConfiguration() {
            return manager.getEconomy("levels").orElseThrow().getTaxConfiguration();
        }

        @Test
        @DisplayName("type, mode et montant sont lus (casse libre)")
        void baseValues_loaded() {
            TaxConfiguration tax = taxConfiguration();

            assertTrue(tax.isEnabled());
            assertEquals(TaxType.PURCHASE, tax.getTaxType());
            assertEquals(TaxAmountType.FIXED, tax.getAmountType());
            assertEquals(12.5, tax.getAmount(), 1e-9);
            assertEquals("zauctionhouse.tax.bypass", tax.getBypassPermission());
        }

        @Test
        @DisplayName("reductions : permission vide ou pourcentage nul sont ecartes, l'ordre est conserve")
        void reductions_filtered() {
            List<TaxReduction> reductions = taxConfiguration().getReductions();

            assertEquals(List.of(new TaxReduction("zauctionhouse.tax.vip", 50), new TaxReduction("zauctionhouse.tax.premium", 25)), reductions);
        }

        @Test
        @DisplayName("regles d'items : seules les regles nommees avec une regle de correspondance valide survivent")
        void itemRules_filtered() {
            TaxConfiguration tax = taxConfiguration();

            assertTrue(tax.hasItemRules());
            assertEquals(1, tax.getItemRules().size());
            ItemTaxRule rule = tax.getItemRules().get(0);
            assertEquals("diamond_tax", rule.getName());
            assertEquals(100, rule.getPriority());
            assertEquals(TaxType.CAPITALISM, rule.getTaxType());
            assertEquals(TaxAmountType.PERCENTAGE, rule.getAmountType());
            assertEquals(10, rule.getAmount(), 1e-9);
            assertNotNull(rule.getRule());
        }

        @Test
        @DisplayName("la taxe chargee s'applique : FIXED 12.5 a l'achat")
        void loadedTax_isEffective() {
            TaxConfiguration tax = taxConfiguration();
            var player = ConfigTestSupport.playerWithPermissions("Buyer");

            var result = tax.calculatePurchaseTax(player, new BigDecimal("100"), null);

            assertMoney("12.50", result.taxAmount());
            assertMoney("87.50", result.sellerReceives());
        }
    }

    @Nested
    @DisplayName("configuration de taxe : cas degrades")
    class TaxLoadingEdgeCases {

        @Test
        @DisplayName("tax.enabled=false => configuration desactivee meme avec un montant")
        void taxDisabled_isDisabledConfiguration() throws IOException {
            writeEconomies("""
                    economies:
                      - type: LEVEL
                        is-enable: true
                        name: levels
                        tax:
                          enabled: false
                          amount: 50
                    """);

            load();

            assertFalse(manager.getEconomy("levels").orElseThrow().getTaxConfiguration().isEnabled());
        }

        @Test
        @DisplayName("types invalides => SELL et PERCENTAGE par defaut ; item-rules desactivees => aucune regle")
        void invalidTypes_fallBackToDefaults() throws IOException {
            writeEconomies("""
                    economies:
                      - type: LEVEL
                        is-enable: true
                        name: levels
                        tax:
                          enabled: true
                          type: GIFT
                          amount-type: SQUARE
                          amount: 7
                          item-rules:
                            enabled: false
                            rules:
                              - name: "ignored"
                                rule:
                                  type: material
                    """);

            load();

            TaxConfiguration tax = manager.getEconomy("levels").orElseThrow().getTaxConfiguration();
            assertTrue(tax.isEnabled());
            assertEquals(TaxType.SELL, tax.getTaxType());
            assertEquals(TaxAmountType.PERCENTAGE, tax.getAmountType());
            assertEquals(7, tax.getAmount(), 1e-9);
            assertNull(tax.getBypassPermission());
            assertTrue(tax.getReductions().isEmpty());
            assertFalse(tax.hasItemRules());
            verify(plugin.getRuleLoaderRegistry(), never()).loadRule(any());
        }

        @Test
        @DisplayName("section tax qui n'est pas une map => desactivee")
        void taxNotAMap_disabled() throws IOException {
            writeEconomies("""
                    economies:
                      - type: LEVEL
                        is-enable: true
                        name: levels
                        tax: yes
                    """);

            load();

            assertFalse(manager.getEconomy("levels").orElseThrow().getTaxConfiguration().isEnabled());
        }
    }

    // ---------------------------------------------------------------- formats

    @Nested
    @DisplayName("format(PriceFormat, Number)")
    class Formatting {

        @BeforeEach
        void loadRealPriceConfiguration() throws IOException {
            // Le vrai economies.yml, prive de ses economies (VAULT exigerait le plugin Vault) :
            // on verifie les VRAIES reductions K/M/B/T/Q et le vrai format decimal.
            YamlConfiguration real = ConfigTestSupport.loadResource("economies.yml");
            real.set("economies", new ArrayList<>());
            real.save(economiesFile());
            load();
            assertEquals(6, manager.getPriceReductions().size());
        }

        @Test
        @DisplayName("PRICE_RAW : toString du nombre")
        void raw() {
            assertEquals("1234.50", manager.format(PriceFormat.PRICE_RAW, new BigDecimal("1234.50")));
            assertEquals("5", manager.format(PriceFormat.PRICE_RAW, 5));
            assertEquals("-2.5", manager.format(PriceFormat.PRICE_RAW, -2.5));
        }

        @Test
        @DisplayName("PRICE_WITHOUT_DECIMAL : troncature vers le long")
        void withoutDecimal() {
            assertEquals("1234", manager.format(PriceFormat.PRICE_WITHOUT_DECIMAL, new BigDecimal("1234.99")));
            assertEquals("-5", manager.format(PriceFormat.PRICE_WITHOUT_DECIMAL, -5.5));
            assertEquals("0", manager.format(PriceFormat.PRICE_WITHOUT_DECIMAL, 0.9));
        }

        @Test
        @DisplayName("PRICE_WITH_DECIMAL_FORMAT : motif #,###.# du fichier")
        void withDecimalFormat() {
            assertEquals("1,234.6", manager.format(PriceFormat.PRICE_WITH_DECIMAL_FORMAT, 1234.56));
            assertEquals("1,000,000", manager.format(PriceFormat.PRICE_WITH_DECIMAL_FORMAT, 1_000_000L));
            assertEquals("0", manager.format(PriceFormat.PRICE_WITH_DECIMAL_FORMAT, 0));
            assertEquals("999", manager.format(PriceFormat.PRICE_WITH_DECIMAL_FORMAT, 999));
        }

        @ParameterizedTest(name = "PRICE_WITH_REDUCTION : {0} => {1}")
        @CsvSource({
                "0, 0",
                "-5, -5",
                "999, 999",
                "999.5, 999.5",
                "1000, 1.0K",
                "1500, 1.5K",
                "999999, 1000.0K",
                "1000000, 1.0M",
                "1500000, 1.5M",
                "1000000000, 1.0B",
                "1500000000, 1.5B",
                "2500000000000, <green>2.50T",
                "100000000000000000, 100000000000000000",
        })
        void withReduction(String input, String expected) {
            assertEquals(expected, manager.format(PriceFormat.PRICE_WITH_REDUCTION, new BigDecimal(input)));
        }

        @Test
        @DisplayName("PRICE_WITH_REDUCTION : un quadrillion s'affiche 1.00Q")
        @Disabled("BUG: economies.yml declare max-amount 1e17 pour le palier Q alors que ZEconomyManager.formatAmount derive le diviseur de max-amount/1000 (ZEconomyManager.java:231) : 1e15 s'affiche '<red>10.00Q' au lieu de '<red>1.00Q'")
        void withReduction_quadrillion() {
            assertEquals("<red>1.00Q", manager.format(PriceFormat.PRICE_WITH_REDUCTION, new BigDecimal("1000000000000000")));
        }

        @Test
        @DisplayName("PRICE_WITH_REDUCTION accepte les Long, Integer et Double")
        void withReduction_numberTypes() {
            assertEquals("1.5K", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 1500L));
            assertEquals("1.5K", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 1500));
            assertEquals("1.5K", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 1500.0d));
        }

        @Test
        @DisplayName("getDisplayBalance(null) leve IllegalArgumentException")
        void displayBalance_null_throws() {
            assertThrows(IllegalArgumentException.class, () -> manager.getDisplayBalance(null));
        }

        @Test
        @DisplayName("format(economie, nombre) applique le format de l'economie puis son gabarit")
        void formatEconomy_appliesTemplate() {
            ZAuctionEconomy coins = economy("coins", PriceFormat.PRICE_WITHOUT_DECIMAL, "%price% coin%s%");
            ZAuctionEconomy dollars = economy("dollars", PriceFormat.PRICE_WITH_REDUCTION, "$%price%");

            assertEquals("1234 coins", manager.format(coins, new BigDecimal("1234.9")));
            assertEquals("1 coin", manager.format(coins, 1));
            assertEquals("$1.5K", manager.format(dollars, 1500));
        }

        @Test
        @DisplayName("format(economie, nombre) passe par la FontImage zMenu quand le resultat contient ':'")
        void formatEconomy_withColon_usesFontImage() {
            ZAuctionEconomy coins = economy("coins", PriceFormat.PRICE_RAW, ":coin: %price%");
            FontImage fontImage = plugin.getInventoriesLoader().getInventoryManager().getFontImage();
            when(fontImage.replace(":coin: 10")).thenReturn(" 10");

            assertEquals(" 10", manager.format(coins, 10));
            verify(fontImage).replace(":coin: 10");
        }

        @Test
        @DisplayName("format(economie, nombre) sans ':' n'appelle pas la FontImage")
        void formatEconomy_withoutColon_skipsFontImage() {
            ZAuctionEconomy coins = economy("coins", PriceFormat.PRICE_RAW, "%price%$");

            assertEquals("10$", manager.format(coins, 10));
            verify(plugin.getInventoriesLoader().getInventoryManager().getFontImage(), never()).replace(any());
        }
    }

    @Nested
    @DisplayName("reductions personnalisees")
    class CustomReductions {

        @Test
        @DisplayName("display personnalise, format DecimalFormat et depassement du dernier palier")
        void customReductions() throws IOException {
            writeEconomies("""
                    economies: []
                    price-reductions:
                      - format: "#,##0"
                        max-amount: 10000
                        display: "<gold>%amount% coins"
                      - format: "%.3fK"
                        max-amount: 1000000
                    """);

            load();

            assertEquals("<gold>9,999 coins", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 9999));
            assertEquals("10.000K", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 10000));
            assertEquals("1000000", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 1_000_000), "au-dela du dernier palier : nombre brut");
        }

        @Test
        @DisplayName("display vide : le palier est saute")
        void emptyDisplay_skipsTier() throws IOException {
            writeEconomies("""
                    economies: []
                    price-reductions:
                      - format: "#"
                        max-amount: 1000
                        display: ""
                      - format: "%.1fK"
                        max-amount: 1000000
                    """);

            load();

            assertEquals("0.5K", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 500));
        }

        @Test
        @DisplayName("aucune reduction configuree : nombre brut")
        void noReductions_plainNumber() throws IOException {
            writeEconomies("economies: []\n");

            load();

            assertEquals("123456789", manager.format(PriceFormat.PRICE_WITH_REDUCTION, 123_456_789L));
        }
    }
}
