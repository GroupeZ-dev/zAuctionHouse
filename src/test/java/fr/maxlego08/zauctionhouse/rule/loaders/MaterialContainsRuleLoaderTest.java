package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialContainsRule;
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

class MaterialContainsRuleLoaderTest {

    private final MaterialContainsRuleLoader loader = new MaterialContainsRuleLoader();

    @Test
    @DisplayName("getType : 'material-contains'")
    void getType_isMaterialContains() {
        assertEquals("material-contains", loader.getType());
    }

    @Test
    @DisplayName("load : liste valide => MaterialContainsRule operationnelle")
    void load_validPatterns_returnsRule() {
        Rule rule = loader.load(Map.of("type", "material-contains", "patterns", List.of("WOOL", "banner")));

        assertInstanceOf(MaterialContainsRule.class, rule);
        assertTrue(rule.isValid());
        assertTrue(rule.matches(RuleTestItems.context(Material.WHITE_WOOL)));
        assertTrue(rule.matches(RuleTestItems.context(Material.RED_BANNER)));
        assertFalse(rule.matches(RuleTestItems.context(Material.STONE)));
    }

    @Test
    @DisplayName("load : null si cle absente, vide, ou mal typee")
    void load_missingOrInvalidKey_returnsNull() {
        assertNull(loader.load(Map.of()));
        assertNull(loader.load(Map.of("patterns", List.of())));
        assertNull(loader.load(Map.of("patterns", "WOOL")));
        assertNull(loader.load(Map.of("pattern", List.of("WOOL"))), "la cle est 'patterns', pas 'pattern'");
    }
}
