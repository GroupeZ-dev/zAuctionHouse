package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialPrefixRule;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialPrefixRuleLoaderTest {

    private final MaterialPrefixRuleLoader loader = new MaterialPrefixRuleLoader();

    @Test
    @DisplayName("getType : 'material-prefix'")
    void getType_isMaterialPrefix() {
        assertEquals("material-prefix", loader.getType());
    }

    @Test
    @DisplayName("load : liste valide => MaterialPrefixRule operationnelle")
    void load_validPrefixes_returnsRule() {
        Rule rule = loader.load(Map.of("type", "material-prefix", "prefixes", List.of("DIAMOND_", "iron_")));

        assertInstanceOf(MaterialPrefixRule.class, rule);
        assertTrue(rule.isValid());
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
        assertTrue(rule.matches(RuleTestItems.context(Material.IRON_INGOT)));
        assertFalse(rule.matches(RuleTestItems.context(Material.GOLD_INGOT)));
    }

    @Test
    @DisplayName("load : null si cle absente, vide, ou mal typee")
    void load_missingOrInvalidKey_returnsNull() {
        assertNull(loader.load(Map.of()));
        assertNull(loader.load(Map.of("prefixes", List.of())));
        assertNull(loader.load(Map.of("prefixes", "DIAMOND_")));
        assertNull(loader.load(Map.of("prefix", List.of("DIAMOND_"))), "la cle est 'prefixes', pas 'prefix'");
    }
}
