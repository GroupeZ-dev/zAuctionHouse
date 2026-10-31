package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.event.events.RuleLoadEvent;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.Rules;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.rule.rules.AndRule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialRule;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ZItemRuleManager} : precedence blacklist / whitelist, activation, mutation des
 * jeux de regles et chargement depuis {@code rules.yml}.
 */
class ZItemRuleManagerTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private RuleLoaderRegistry registry;
    private ZItemRuleManager manager;
    private ItemStack diamond;
    private ItemStack stone;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.registry = mock(RuleLoaderRegistry.class);
        this.manager = new ZItemRuleManager(this.plugin, this.registry);
        this.diamond = TestItems.diamond(1);
        this.stone = TestItems.itemStack(Material.STONE, 1);
    }

    private static Rule materials(Material... materials) {
        return new MaterialRule(Set.of(materials));
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("etat initial")
    class InitialState {

        @Test
        @DisplayName("sans chargement, les deux listes sont vides et desactivees : tout est autorise")
        void defaults_allowEverything() {
            assertFalse(manager.isBlacklistEnabled());
            assertFalse(manager.isWhitelistEnabled());
            assertTrue(manager.blacklistRules().rules().isEmpty());
            assertTrue(manager.whitelistRules().rules().isEmpty());
            assertTrue(manager.isAllowed(diamond));
            assertFalse(manager.isBlacklisted(diamond));
            assertFalse(manager.isWhitelisted(diamond));
        }
    }

    @Nested
    @DisplayName("blacklist")
    class Blacklist {

        @Test
        @DisplayName("blacklist active : l'item qui matche est refuse, les autres passent")
        void enabledBlacklist_refusesMatchingItems() {
            manager.addBlacklistRule(materials(Material.DIAMOND));
            manager.setBlacklistEnabled(true);

            assertTrue(manager.isBlacklistEnabled());
            assertTrue(manager.isBlacklisted(diamond));
            assertFalse(manager.isAllowed(diamond));
            assertFalse(manager.isBlacklisted(stone));
            assertTrue(manager.isAllowed(stone));
        }

        @Test
        @DisplayName("blacklist desactivee : une regle qui matche ne refuse rien")
        void disabledBlacklist_isIgnored() {
            manager.addBlacklistRule(materials(Material.DIAMOND));

            assertFalse(manager.isBlacklistEnabled());
            assertFalse(manager.isBlacklisted(diamond), "Rules.matches rend false quand le jeu est desactive");
            assertTrue(manager.isAllowed(diamond));
        }

        @Test
        @DisplayName("addBlacklistRule conserve les regles precedentes (OU logique)")
        void addBlacklistRule_accumulates() {
            manager.addBlacklistRule(materials(Material.DIAMOND));
            manager.addBlacklistRule(materials(Material.STONE));
            manager.setBlacklistEnabled(true);

            assertEquals(2, manager.blacklistRules().rules().size());
            assertTrue(manager.isBlacklisted(diamond));
            assertTrue(manager.isBlacklisted(stone));
        }

        @Test
        @DisplayName("setBlacklistRules remplace integralement le jeu de regles")
        void setBlacklistRules_replaces() {
            manager.addBlacklistRule(materials(Material.DIAMOND));
            Rules replacement = new Rules(true, List.of(materials(Material.STONE)));

            manager.setBlacklistRules(replacement);

            assertSame(replacement, manager.blacklistRules());
            assertFalse(manager.isBlacklisted(diamond));
            assertTrue(manager.isBlacklisted(stone));
        }

        @Test
        @DisplayName("setBlacklistEnabled bascule sans perdre les regles")
        void setBlacklistEnabled_togglesKeepingRules() {
            manager.addBlacklistRule(materials(Material.DIAMOND));
            manager.setBlacklistEnabled(true);
            assertTrue(manager.isBlacklisted(diamond));

            manager.setBlacklistEnabled(false);
            assertFalse(manager.isBlacklisted(diamond));
            assertEquals(1, manager.blacklistRules().rules().size());

            manager.setBlacklistEnabled(true);
            assertTrue(manager.isBlacklisted(diamond));
        }
    }

    @Nested
    @DisplayName("whitelist")
    class Whitelist {

        @Test
        @DisplayName("whitelist active : l'item qui matche est whiteliste, celui qui ne matche pas ne l'est pas")
        void enabledWhitelist_matches() {
            manager.addWhitelistRule(materials(Material.DIAMOND));
            manager.setWhitelistEnabled(true);

            assertTrue(manager.isWhitelistEnabled());
            assertTrue(manager.isWhitelisted(diamond));
            assertFalse(manager.isWhitelisted(stone));
        }

        @Test
        @DisplayName("isAllowed ne refuse jamais un item seulement parce qu'il est absent de la whitelist")
        void isAllowed_doesNotEnforceWhitelistAlone() {
            manager.addWhitelistRule(materials(Material.DIAMOND));
            manager.setWhitelistEnabled(true);

            // Le refus NOT_WHITELISTED est porte par les services (isWhitelistEnabled && !isWhitelisted),
            // isAllowed ne connait que la blacklist.
            assertTrue(manager.isAllowed(stone));
        }

        @Test
        @DisplayName("un item whiteliste est autorise meme s'il est blackliste (la whitelist prime)")
        void whitelist_beatsBlacklist() {
            manager.addWhitelistRule(materials(Material.DIAMOND));
            manager.setWhitelistEnabled(true);
            manager.addBlacklistRule(materials(Material.DIAMOND));
            manager.setBlacklistEnabled(true);

            assertTrue(manager.isBlacklisted(diamond));
            assertTrue(manager.isWhitelisted(diamond));
            assertTrue(manager.isAllowed(diamond));
        }

        @Test
        @DisplayName("whitelist desactivee : elle ne protege plus de la blacklist")
        void disabledWhitelist_doesNotProtect() {
            manager.addWhitelistRule(materials(Material.DIAMOND));
            manager.addBlacklistRule(materials(Material.DIAMOND));
            manager.setBlacklistEnabled(true);

            assertFalse(manager.isWhitelisted(diamond));
            assertFalse(manager.isAllowed(diamond));
        }

        @Test
        @DisplayName("setWhitelistRules remplace le jeu de regles")
        void setWhitelistRules_replaces() {
            Rules replacement = new Rules(true, List.of(materials(Material.STONE)));

            manager.setWhitelistRules(replacement);

            assertSame(replacement, manager.whitelistRules());
            assertTrue(manager.isWhitelistEnabled());
            assertTrue(manager.isWhitelisted(stone));
            assertFalse(manager.isWhitelisted(diamond));
        }
    }

    @Nested
    @DisplayName("loadRules")
    class LoadRules {

        private PluginManager pluginManager;
        private MockedStatic<Bukkit> bukkit;

        @BeforeEach
        void setUpBukkit() {
            this.pluginManager = mock(PluginManager.class);
            this.bukkit = mockStatic(Bukkit.class);
            this.bukkit.when(Bukkit::getPluginManager).thenReturn(this.pluginManager);
        }

        @AfterEach
        void closeBukkit() {
            this.bukkit.close();
        }

        private File writeRules(boolean blacklistEnabled, List<Map<String, Object>> blacklistRules,
                                boolean whitelistEnabled, List<Map<String, Object>> whitelistRules) throws Exception {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("blacklist.enabled", blacklistEnabled);
            configuration.set("blacklist.rules", blacklistRules);
            configuration.set("whitelist.enabled", whitelistEnabled);
            configuration.set("whitelist.rules", whitelistRules);
            File file = new File(dataFolder, "rules.yml");
            configuration.save(file);
            return file;
        }

        @Test
        @DisplayName("charge blacklist et whitelist depuis rules.yml via le registre de loaders et tire RuleLoadEvent")
        void loadRules_readsFileThroughRegistry() throws Exception {
            Map<String, Object> diamondMap = Map.of("type", "material", "materials", List.of("DIAMOND"));
            Map<String, Object> stoneMap = Map.of("type", "material", "materials", List.of("STONE"));
            writeRules(true, List.of(diamondMap), false, List.of(stoneMap));
            when(registry.loadRules(any())).thenAnswer(invocation -> {
                Map<?, ?> map = invocation.getArgument(0);
                return "DIAMOND".equals(((List<?>) map.get("materials")).getFirst()) ? List.of(materials(Material.DIAMOND)) : List.of(materials(Material.STONE));
            });

            manager.loadRules();

            assertTrue(manager.isBlacklistEnabled());
            assertFalse(manager.isWhitelistEnabled());
            assertEquals(1, manager.blacklistRules().rules().size());
            assertEquals(1, manager.whitelistRules().rules().size());
            assertTrue(manager.isBlacklisted(diamond));
            assertFalse(manager.isAllowed(diamond));
            assertTrue(manager.isAllowed(stone));

            ArgumentCaptor<RuleLoadEvent> captor = ArgumentCaptor.forClass(RuleLoadEvent.class);
            verify(pluginManager).callEvent(captor.capture());
            assertSame(manager, captor.getValue().getItemRuleManager());
            verify(plugin, never()).saveFile(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("plusieurs sous-regles pour une meme entree sont combinees en AndRule")
        void loadRules_multipleSubRules_becomeAndRule() throws Exception {
            writeRules(true, List.of(Map.of("type", "and")), false, List.of());
            Rule first = mock(Rule.class);
            Rule second = mock(Rule.class);
            when(first.matches(any())).thenReturn(true);
            when(second.matches(any())).thenReturn(false);
            when(registry.loadRules(any())).thenReturn(List.of(first, second));

            manager.loadRules();

            assertEquals(1, manager.blacklistRules().rules().size());
            assertInstanceOf(AndRule.class, manager.blacklistRules().rules().getFirst());
            assertFalse(manager.isBlacklisted(diamond), "ET logique : une sous-regle qui refuse suffit");

            when(second.matches(any())).thenReturn(true);
            assertTrue(manager.isBlacklisted(diamond));
        }

        @Test
        @DisplayName("une entree sans regle chargeable est ignoree")
        void loadRules_emptySubRules_areSkipped() throws Exception {
            writeRules(true, List.of(Map.of("type", "unknown")), false, List.of());
            when(registry.loadRules(any())).thenReturn(List.of());

            manager.loadRules();

            assertTrue(manager.isBlacklistEnabled());
            assertTrue(manager.blacklistRules().rules().isEmpty());
            assertFalse(manager.isBlacklisted(diamond));
        }

        @Test
        @DisplayName("une liste de regles absente donne un jeu vide avec le drapeau configure")
        void loadRules_noRules_keepsEnabledFlag() throws Exception {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("blacklist.enabled", true);
            configuration.set("whitelist.enabled", false);
            configuration.save(new File(dataFolder, "rules.yml"));

            manager.loadRules();

            assertTrue(manager.isBlacklistEnabled());
            assertFalse(manager.isWhitelistEnabled());
            assertTrue(manager.blacklistRules().rules().isEmpty());
            verify(registry, never()).loadRules(any());
        }

        @Test
        @DisplayName("rules.yml absent : le plugin est prie de l'ecrire, puis les valeurs par defaut s'appliquent (blacklist off, whitelist on)")
        void loadRules_missingFile_asksPluginToSaveIt() {
            manager.loadRules();

            verify(plugin).saveFile("rules.yml", false);
            assertFalse(manager.isBlacklistEnabled());
            assertTrue(manager.isWhitelistEnabled(), "defaut historique de whitelist.enabled quand la cle manque");
            assertTrue(manager.whitelistRules().rules().isEmpty());
            verify(pluginManager).callEvent(isA(RuleLoadEvent.class));
        }

        @Test
        @DisplayName("un second chargement remplace les regles ajoutees a chaud")
        void loadRules_replacesRuntimeRules() throws Exception {
            manager.addBlacklistRule(materials(Material.STONE));
            manager.setBlacklistEnabled(true);
            writeRules(false, List.of(), false, List.of());

            manager.loadRules();

            assertFalse(manager.isBlacklistEnabled());
            assertTrue(manager.blacklistRules().rules().isEmpty());
            assertTrue(manager.isAllowed(stone));
        }
    }
}
