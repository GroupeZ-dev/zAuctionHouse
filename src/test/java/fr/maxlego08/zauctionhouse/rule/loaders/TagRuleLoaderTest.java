package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.TagRule;
import fr.maxlego08.zauctionhouse.testutil.BukkitMocks;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagRuleLoaderTest {

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;
    private TagRuleLoader loader;

    @BeforeAll
    static void initTags() {
        BukkitMocks.ensureTagsInitialized();
        BukkitMocks.registerCustomTag("loader_gems", Material.DIAMOND, Material.EMERALD);
        BukkitMocks.registerCustomTag("loader_ingots", Material.IRON_INGOT, Material.GOLD_INGOT);
    }

    @BeforeEach
    void setUp() {
        plugin = MockPlugins.plugin(tempDir);
        loader = new TagRuleLoader(plugin);
    }

    @Test
    @DisplayName("getType : 'material-tag' avec l'alias 'tag'")
    void getType_andAliases() {
        assertEquals("material-tag", loader.getType());
        assertEquals(List.of("tag"), loader.getAliases());
    }

    @Test
    @DisplayName("load : liste 'tags' => TagRule resolue")
    void load_tagsList_returnsRule() {
        Rule rule = loader.load(Map.of("tags", List.of("loader_gems", "loader_ingots")));

        assertInstanceOf(TagRule.class, rule);
        assertTrue(rule.isValid());
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertTrue(rule.matches(RuleTestItems.context(Material.IRON_INGOT)));
        assertFalse(rule.matches(RuleTestItems.context(Material.STONE)));
    }

    @Test
    @DisplayName("load : cle singuliere 'tag' acceptee en secours")
    void load_singleTagKey_returnsRule() {
        Rule rule = loader.load(Map.of("tag", "LOADER_GEMS"));

        assertInstanceOf(TagRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(Material.EMERALD)));
        assertFalse(rule.matches(RuleTestItems.context(Material.IRON_INGOT)));
    }

    @Test
    @DisplayName("load : 'tags' non vide a priorite sur 'tag'")
    void load_tagsTakesPrecedence() {
        Rule rule = loader.load(Map.of("tags", List.of("loader_gems"), "tag", "loader_ingots"));

        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertFalse(rule.matches(RuleTestItems.context(Material.IRON_INGOT)));
    }

    @Test
    @DisplayName("load : un tag inconnu produit une TagRule non nulle mais invalide")
    void load_unknownTag_returnsInvalidRule() {
        Rule rule = loader.load(Map.of("tags", List.of("no_such_tag")));

        assertInstanceOf(TagRule.class, rule);
        assertFalse(rule.isValid());
    }

    @Test
    @DisplayName("load : null si ni 'tags' ni 'tag'")
    void load_noTag_returnsNull() {
        assertNull(loader.load(Map.of()));
        assertNull(loader.load(Map.of("tags", List.of())));
        assertNull(loader.load(Map.of("tags", "loader_gems")), "'tags' doit etre une liste ; 'tag' absent");
    }
}
