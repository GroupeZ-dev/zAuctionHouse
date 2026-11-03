package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.menu.api.utils.version.MinecraftVersion;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.AndRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.CustomModelDataRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.ItemModelRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.LoreRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.MaterialContainsRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.MaterialPrefixRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.MaterialRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.MaterialSuffixRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.NameRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.TagRuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.AndRule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialRule;
import fr.maxlego08.zauctionhouse.rule.rules.TagRule;
import fr.maxlego08.zauctionhouse.testutil.BukkitMocks;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZRuleLoaderRegistryTest {

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;
    private ZRuleLoaderRegistry registry;

    @BeforeAll
    static void initTags() {
        BukkitMocks.ensureTagsInitialized();
        BukkitMocks.registerCustomTag("registry_gems", Material.DIAMOND);
    }

    @BeforeEach
    void setUp() {
        plugin = MockPlugins.plugin(tempDir);
        registry = new ZRuleLoaderRegistry(plugin);
    }

    /**
     * registerDefaultLoaders lit la version du serveur via zMenu (Bukkit.getBukkitVersion) :
     * il doit tourner sous Bukkit mocke.
     */
    private void registerDefaults() {
        try (MockedStatic<Bukkit> ignored = BukkitMocks.mockBukkit()) {
            registry.registerDefaultLoaders();
        }
    }

    private static boolean itemModelExpected() {
        try (MockedStatic<Bukkit> ignored = BukkitMocks.mockBukkit()) {
            return MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.parse("1.21.4"));
        }
    }

    /**
     * Loader de test minimal : type et alias configurables, rend toujours la meme regle.
     */
    private static RuleLoader loader(String type, Rule result, String... aliases) {
        return new RuleLoader() {
            @Override
            public String getType() {
                return type;
            }

            @Override
            public List<String> getAliases() {
                return Arrays.asList(aliases);
            }

            @Override
            public Rule load(Map<?, ?> configuration) {
                return result;
            }
        };
    }

    @Nested
    @DisplayName("registerDefaultLoaders")
    class DefaultLoaders {

        @Test
        @DisplayName("enregistre chaque type attendu avec le bon loader")
        void registersEveryExpectedType() {
            registerDefaults();

            assertInstanceOf(MaterialRuleLoader.class, registry.getLoader("material").orElseThrow());
            assertInstanceOf(MaterialSuffixRuleLoader.class, registry.getLoader("material-suffix").orElseThrow());
            assertInstanceOf(MaterialPrefixRuleLoader.class, registry.getLoader("material-prefix").orElseThrow());
            assertInstanceOf(MaterialContainsRuleLoader.class, registry.getLoader("material-contains").orElseThrow());
            assertInstanceOf(TagRuleLoader.class, registry.getLoader("material-tag").orElseThrow());
            assertInstanceOf(TagRuleLoader.class, registry.getLoader("tag").orElseThrow(), "alias 'tag'");
            assertInstanceOf(NameRuleLoader.class, registry.getLoader("name").orElseThrow());
            assertInstanceOf(LoreRuleLoader.class, registry.getLoader("lore").orElseThrow());
            assertInstanceOf(CustomModelDataRuleLoader.class, registry.getLoader("custom-model-data").orElseThrow());
            assertInstanceOf(AndRuleLoader.class, registry.getLoader("and").orElseThrow());
        }

        @Test
        @DisplayName("'tag' et 'material-tag' pointent sur la MEME instance")
        void tagAliasSharesInstance() {
            registerDefaults();

            assertSame(registry.getLoader("material-tag").orElseThrow(), registry.getLoader("tag").orElseThrow());
        }

        @Test
        @DisplayName("item-model n'est enregistre que si la version serveur >= 1.21.4")
        void itemModel_dependsOnServerVersion() {
            registerDefaults();

            Optional<RuleLoader> itemModel = registry.getLoader("item-model");
            assertEquals(itemModelExpected(), itemModel.isPresent());
            itemModel.ifPresent(loader -> assertInstanceOf(ItemModelRuleLoader.class, loader));
        }

        @Test
        @DisplayName("getLoaders rend les loaders UNIQUES (l'alias ne compte pas deux fois)")
        void getLoaders_returnsUniqueInstances() {
            registerDefaults();

            int expected = 9 + (itemModelExpected() ? 1 : 0);
            assertEquals(expected, registry.getLoaders().size());
        }

        @Test
        @DisplayName("aucun loader de hook (itemsadder, oraxen...) n'est enregistre par defaut")
        void hookLoaders_notRegisteredByDefault() {
            registerDefaults();

            for (String hook : List.of("itemsadder", "oraxen", "nexo", "mmoitems", "executableitems", "slimefun", "headdatabase", "nova", "denizen", "craftengine", "executableblocks")) {
                assertTrue(registry.getLoader(hook).isEmpty(), hook);
            }
        }
    }

    @Nested
    @DisplayName("register / unregister / getLoader")
    class Registration {

        @Test
        @DisplayName("registre neuf : vide")
        void freshRegistry_isEmpty() {
            assertTrue(registry.getLoaders().isEmpty());
            assertTrue(registry.getLoader("material").isEmpty());
        }

        @Test
        @DisplayName("register : type et alias sont normalises en minuscules, la recherche aussi")
        void register_caseInsensitive() {
            RuleLoader custom = loader("My-Type", null, "ALIAS-One", "alias-two");
            registry.register(custom);

            assertSame(custom, registry.getLoader("my-type").orElseThrow());
            assertSame(custom, registry.getLoader("MY-TYPE").orElseThrow());
            assertSame(custom, registry.getLoader("My-Type").orElseThrow());
            assertSame(custom, registry.getLoader("alias-one").orElseThrow());
            assertSame(custom, registry.getLoader("ALIAS-TWO").orElseThrow());
            assertEquals(1, registry.getLoaders().size());
        }

        @Test
        @DisplayName("register : un second loader du meme type remplace le premier")
        void register_sameType_replaces() {
            RuleLoader first = loader("dup", null);
            RuleLoader second = loader("DUP", null);
            registry.register(first);
            registry.register(second);

            assertSame(second, registry.getLoader("dup").orElseThrow());
            assertEquals(1, registry.getLoaders().size());
        }

        @Test
        @DisplayName("unregister : retire le type ET ses alias, insensible a la casse")
        void unregister_removesTypeAndAliases() {
            registry.register(loader("my-type", null, "alias-one"));

            registry.unregister("MY-TYPE");

            assertTrue(registry.getLoader("my-type").isEmpty());
            assertTrue(registry.getLoader("alias-one").isEmpty());
            assertTrue(registry.getLoaders().isEmpty());
        }

        @Test
        @DisplayName("unregister : type inconnu => aucune exception, registre inchange")
        void unregister_unknownType_isNoOp() {
            registry.register(loader("keep", null));

            assertDoesNotThrow(() -> registry.unregister("unknown"));
            assertEquals(1, registry.getLoaders().size());
        }

        @Test
        @DisplayName("unregister : ne touche pas les autres loaders")
        void unregister_leavesOthersIntact() {
            RuleLoader a = loader("a", null);
            RuleLoader b = loader("b", null);
            registry.register(a);
            registry.register(b);

            registry.unregister("a");

            assertTrue(registry.getLoader("a").isEmpty());
            assertSame(b, registry.getLoader("b").orElseThrow());
        }
    }

    @Nested
    @DisplayName("loadRule / loadRules / loadRulesFromList")
    class Loading {

        @BeforeEach
        void registerDefaultLoaders() {
            registerDefaults();
        }

        @Test
        @DisplayName("loadRule : type connu => regle du bon type et fonctionnelle")
        void loadRule_knownType() {
            Rule rule = registry.loadRule(Map.of("type", "material", "materials", List.of("DIAMOND")));

            assertInstanceOf(MaterialRule.class, rule);
            assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        }

        @Test
        @DisplayName("loadRule : la casse du type est ignoree")
        void loadRule_typeCaseInsensitive() {
            assertInstanceOf(MaterialRule.class, registry.loadRule(Map.of("type", "MATERIAL", "materials", List.of("DIAMOND"))));
            assertInstanceOf(MaterialRule.class, registry.loadRule(Map.of("type", "Material", "materials", List.of("DIAMOND"))));
        }

        @Test
        @DisplayName("loadRule : via l'alias 'tag'")
        void loadRule_viaAlias() {
            Rule rule = registry.loadRule(Map.of("type", "tag", "tags", List.of("registry_gems")));

            assertInstanceOf(TagRule.class, rule);
            assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        }

        @Test
        @DisplayName("loadRule : type inconnu => null")
        void loadRule_unknownType_returnsNull() {
            assertNull(registry.loadRule(Map.of("type", "does-not-exist", "materials", List.of("DIAMOND"))));
        }

        @Test
        @DisplayName("loadRule : sans cle 'type' => null, sans consulter les loaders")
        void loadRule_missingType_returnsNull() {
            assertNull(registry.loadRule(Map.of("materials", List.of("DIAMOND"))));
            assertNull(registry.loadRule(Map.of()));
        }

        @Test
        @DisplayName("loadRule : type connu mais config inexploitable => null (delegue au loader)")
        void loadRule_knownTypeBadConfig_returnsNull() {
            assertNull(registry.loadRule(Map.of("type", "material")));
            assertNull(registry.loadRule(Map.of("type", "material", "materials", List.of("NOPE"))));
        }

        @Test
        @DisplayName("loadRule : 'and' imbrique resolu recursivement via le registre")
        void loadRule_nestedAnd() {
            Map<String, Object> config = Map.of("type", "and", "rules", List.of(
                    Map.of("type", "material", "materials", List.of("PAPER")),
                    Map.of("type", "name", "mode", "CONTAINS", "values", List.of("Voucher"))
            ));

            Rule rule = registry.loadRule(config);

            assertInstanceOf(AndRule.class, rule);
            assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Voucher"))));
            assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Key"))));
        }

        @Test
        @DisplayName("loadRules : liste a un element si la regle charge, vide sinon")
        void loadRules_singletonOrEmpty() {
            List<Rule> loaded = registry.loadRules(Map.of("type", "material", "materials", List.of("DIAMOND")));
            List<Rule> empty = registry.loadRules(Map.of("type", "nope"));

            assertEquals(1, loaded.size());
            assertInstanceOf(MaterialRule.class, loaded.getFirst());
            assertTrue(empty.isEmpty());
        }

        @Test
        @DisplayName("loadRulesFromList : conserve les regles valides, saute types inconnus et regles invalides")
        void loadRulesFromList_filtersInvalidAndUnknown() {
            List<Map<?, ?>> configs = List.of(
                    Map.of("type", "material", "materials", List.of("DIAMOND")),
                    Map.of("type", "unknown-type"),
                    // Plage inversee => ModelDataRangeRule.isValid() == false => ecartee avec un warning
                    Map.of("type", "custom-model-data", "ranges", List.of(Map.of("min", 10, "max", 1))),
                    Map.of("type", "material-suffix", "suffixes", List.of("_SWORD"))
            );

            List<Rule> rules = registry.loadRulesFromList(configs);

            assertEquals(2, rules.size());
            assertInstanceOf(MaterialRule.class, rules.get(0));
            assertTrue(rules.get(1).matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
        }

        @Test
        @DisplayName("loadRulesFromList : liste vide => liste vide")
        void loadRulesFromList_empty() {
            assertTrue(registry.loadRulesFromList(List.of()).isEmpty());
        }

        @Test
        @DisplayName("loadRulesFromList : l'ordre des configurations est conserve")
        void loadRulesFromList_preservesOrder() {
            List<Map<?, ?>> configs = List.of(
                    Map.of("type", "material-suffix", "suffixes", List.of("_SWORD")),
                    Map.of("type", "material", "materials", List.of("DIAMOND"))
            );

            List<Rule> rules = registry.loadRulesFromList(configs);

            assertEquals(2, rules.size());
            assertTrue(rules.get(0).matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
            assertInstanceOf(MaterialRule.class, rules.get(1));
        }
    }
}
