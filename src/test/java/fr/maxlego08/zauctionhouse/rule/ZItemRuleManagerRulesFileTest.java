package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.Rules;
import fr.maxlego08.zauctionhouse.testutil.BukkitMocks;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Complement de {@link ZItemRuleManagerTest} (qui mocke le registre) : ici le VRAI
 * {@link ZRuleLoaderRegistry} avec ses loaders par defaut charge le VRAI {@code rules.yml} de
 * production, et la table de verite de {@code isAllowed} est couverte exhaustivement.
 */
class ZItemRuleManagerRulesFileTest {

    private static final Rule ALWAYS_TRUE = context -> true;
    private static final Rule ALWAYS_FALSE = context -> false;

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;
    private ZRuleLoaderRegistry registry;
    private ZItemRuleManager manager;

    @BeforeEach
    void setUp() {
        plugin = MockPlugins.plugin(tempDir);
        registry = new ZRuleLoaderRegistry(plugin);
        manager = new ZItemRuleManager(plugin, registry);
    }

    private static Rules rules(boolean enabled, boolean matching) {
        return new Rules(enabled, List.of(matching ? ALWAYS_TRUE : ALWAYS_FALSE));
    }

    /**
     * loadRules declenche RuleLoadEvent.callEvent() (Bukkit.getPluginManager) et le registre
     * par defaut lit la version serveur : tout tourne sous Bukkit mocke.
     */
    private MockedStatic<Bukkit> loadRulesWithDefaults() {
        MockedStatic<Bukkit> bukkit = BukkitMocks.mockBukkit();
        registry.registerDefaultLoaders();
        manager.loadRules();
        return bukkit;
    }

    private void copyResource(String resource, File target) throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(resource), "ressource introuvable : " + resource)) {
            Files.copy(in, target.toPath());
        }
    }

    private void writeRules(String yaml) throws IOException {
        Files.writeString(new File(tempDir, "rules.yml").toPath(), yaml, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("isAllowed : table de verite complete")
    class IsAllowedTruthTable {

        /**
         * Semantique de l'API ({@code ItemRuleManager#isAllowed}) : whitelist activee ET matchee
         * => autorise ; sinon autorise ssi blacklist desactivee OU non matchee.
         */
        @ParameterizedTest(name = "BL(enabled={0}, matches={1}) WL(enabled={2}, matches={3}) => allowed={4}")
        @CsvSource({
                // BLe,   BLm,   WLe,   WLm,   expected
                "false, false, false, false, true",
                "false, true,  false, false, true",
                "false, true,  false, true,  true",
                "true,  false, false, false, true",
                "true,  false, false, true,  true",
                "true,  true,  false, false, false",
                "true,  true,  false, true,  false",
                "false, false, true,  false, true",
                "false, false, true,  true,  true",
                "false, true,  true,  false, true",
                "false, true,  true,  true,  true",
                "true,  false, true,  false, true",
                "true,  false, true,  true,  true",
                "true,  true,  true,  false, false",
                "true,  true,  true,  true,  true",
        })
        void isAllowed_truthTable(boolean blEnabled, boolean blMatches, boolean wlEnabled, boolean wlMatches, boolean expected) {
            manager.setBlacklistRules(rules(blEnabled, blMatches));
            manager.setWhitelistRules(rules(wlEnabled, wlMatches));

            assertEquals(expected, manager.isAllowed(RuleTestItems.plain(Material.PAPER)));
        }

        @Test
        @DisplayName("isBlacklisted / isWhitelisted suivent le drapeau enabled, independamment de isAllowed")
        void flagsGateMatching() {
            manager.setBlacklistRules(rules(false, true));
            manager.setWhitelistRules(rules(false, true));
            ItemStack item = RuleTestItems.plain(Material.PAPER);

            assertFalse(manager.isBlacklisted(item));
            assertFalse(manager.isWhitelisted(item));

            manager.setBlacklistEnabled(true);
            manager.setWhitelistEnabled(true);

            assertTrue(manager.isBlacklisted(item));
            assertTrue(manager.isWhitelisted(item));
        }
    }

    @Nested
    @DisplayName("loadRules avec le vrai registre")
    class LoadRules {

        @Test
        @DisplayName("rules.yml de production : blacklist active (2 regles), whitelist inactive (1 regle), evenement emis")
        void productionFile_structure() throws IOException {
            copyResource("/rules.yml", new File(tempDir, "rules.yml"));

            try (MockedStatic<Bukkit> bukkit = loadRulesWithDefaults()) {
                bukkit.verify(Bukkit::getPluginManager);
            }

            assertTrue(manager.isBlacklistEnabled());
            assertEquals(2, manager.blacklistRules().rules().size());
            assertFalse(manager.isWhitelistEnabled());
            assertEquals(1, manager.whitelistRules().rules().size());
            verify(plugin, never()).saveFile(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("rules.yml de production : BEDROCK bloque, DIAMOND autorise")
        void productionFile_bedrockBlocked() throws IOException {
            copyResource("/rules.yml", new File(tempDir, "rules.yml"));
            try (MockedStatic<Bukkit> ignored = loadRulesWithDefaults()) {
            }

            assertTrue(manager.isBlacklisted(RuleTestItems.plain(Material.BEDROCK)));
            assertFalse(manager.isAllowed(RuleTestItems.plain(Material.BEDROCK)));
            assertFalse(manager.isBlacklisted(RuleTestItems.plain(Material.DIAMOND)));
            assertTrue(manager.isAllowed(RuleTestItems.plain(Material.DIAMOND)));
        }

        @Test
        @DisplayName("rules.yml de production : la regle 'and' exige lore Untradeable ET model data 1001")
        void productionFile_andRule() throws IOException {
            copyResource("/rules.yml", new File(tempDir, "rules.yml"));
            try (MockedStatic<Bukkit> ignored = loadRulesWithDefaults()) {
            }

            assertTrue(manager.isBlacklisted(RuleTestItems.item(Material.PAPER, null, List.of("This item is Untradeable"), 1001, null)));
            assertFalse(manager.isBlacklisted(RuleTestItems.item(Material.PAPER, null, List.of("Untradeable"), 1002, null)), "model data different");
            assertFalse(manager.isBlacklisted(RuleTestItems.withModelData(Material.PAPER, 1001)), "pas de lore");
            assertFalse(manager.isBlacklisted(RuleTestItems.withLore(Material.PAPER, "Untradeable")), "pas de model data");
        }

        @Test
        @DisplayName("rules.yml de production : whitelist NETHER_STAR 'Voucher' inactive par defaut, active sur demande")
        void productionFile_whitelist() throws IOException {
            copyResource("/rules.yml", new File(tempDir, "rules.yml"));
            try (MockedStatic<Bukkit> ignored = loadRulesWithDefaults()) {
            }
            ItemStack voucher = RuleTestItems.named(Material.NETHER_STAR, "Rank Voucher");
            ItemStack plainStar = RuleTestItems.plain(Material.NETHER_STAR);
            ItemStack paperVoucher = RuleTestItems.named(Material.PAPER, "Voucher");

            assertFalse(manager.isWhitelisted(voucher), "whitelist desactivee dans le fichier");

            manager.setWhitelistEnabled(true);

            assertTrue(manager.isWhitelisted(voucher));
            assertFalse(manager.isWhitelisted(plainStar), "nom requis");
            assertFalse(manager.isWhitelisted(paperVoucher), "materiau requis");
        }

        @Test
        @DisplayName("fichier custom : drapeaux lus, types inconnus ignores, regles valides conservees, whitelist prime")
        void customFile() throws IOException {
            writeRules("""
                    blacklist:
                      enabled: false
                      rules:
                        - type: material
                          materials:
                            - BEDROCK
                        - type: no-such-type
                          foo: bar
                        - type: material-suffix
                          suffixes:
                            - "_SWORD"
                    whitelist:
                      enabled: true
                      rules:
                        - type: name
                          mode: EQUALS
                          values:
                            - "Voucher"
                    """);
            try (MockedStatic<Bukkit> ignored = loadRulesWithDefaults()) {
            }

            assertFalse(manager.isBlacklistEnabled());
            assertEquals(2, manager.blacklistRules().rules().size(), "le type inconnu est ignore");
            assertTrue(manager.isWhitelistEnabled());
            assertEquals(1, manager.whitelistRules().rules().size());

            assertTrue(manager.isAllowed(RuleTestItems.plain(Material.BEDROCK)), "blacklist desactivee");
            manager.setBlacklistEnabled(true);
            assertFalse(manager.isAllowed(RuleTestItems.plain(Material.BEDROCK)));
            assertFalse(manager.isAllowed(RuleTestItems.plain(Material.DIAMOND_SWORD)));
            assertTrue(manager.isAllowed(RuleTestItems.named(Material.DIAMOND_SWORD, "Voucher")), "whitelistee malgre la blacklist");
        }

        @Test
        @DisplayName("fichier absent : saveFile demande ; blacklist off et whitelist on par defaut, tout passe")
        void missingFile_defaults() {
            try (MockedStatic<Bukkit> ignored = loadRulesWithDefaults()) {
            }

            verify(plugin).saveFile("rules.yml", false);
            assertFalse(manager.isBlacklistEnabled());
            assertTrue(manager.isWhitelistEnabled());
            assertTrue(manager.blacklistRules().rules().isEmpty());
            assertTrue(manager.whitelistRules().rules().isEmpty());
            assertTrue(manager.isAllowed(RuleTestItems.plain(Material.BEDROCK)));
        }
    }
}
