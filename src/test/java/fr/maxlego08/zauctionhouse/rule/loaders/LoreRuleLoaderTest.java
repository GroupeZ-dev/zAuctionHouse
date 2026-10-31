package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.LoreContainsRule;
import fr.maxlego08.zauctionhouse.rule.rules.LoreEqualsRule;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreRuleLoaderTest {

    private final LoreRuleLoader loader = new LoreRuleLoader();

    @Test
    @DisplayName("getType : 'lore'")
    void getType_isLore() {
        assertEquals("lore", loader.getType());
    }

    @Test
    @DisplayName("load : sans mode => LoreContainsRule")
    void load_noMode_returnsContainsRule() {
        Rule rule = loader.load(Map.of("values", List.of("Untradeable")));

        assertInstanceOf(LoreContainsRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.PAPER, "This is Untradeable"))));
    }

    @ParameterizedTest(name = "mode={0}")
    @ValueSource(strings = {"CONTAINS", "contains", "ENDS_WITH", "REGEX", "nonsense"})
    @DisplayName("load : tout mode autre que EQUALS retombe sur LoreContainsRule")
    void load_otherModes_returnContainsRule(String mode) {
        assertInstanceOf(LoreContainsRule.class, loader.load(Map.of("mode", mode, "values", List.of("x"))));
    }

    @ParameterizedTest(name = "mode={0}")
    @ValueSource(strings = {"EQUALS", "equals"})
    @DisplayName("load : mode EQUALS => LoreEqualsRule (ligne entiere)")
    void load_equalsMode_returnsEqualsRule(String mode) {
        Rule rule = loader.load(Map.of("mode", mode, "values", List.of("Untradeable")));

        assertInstanceOf(LoreEqualsRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.PAPER, "Untradeable"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.PAPER, "This is Untradeable"))));
    }

    @Test
    @DisplayName("load : null si values absent, vide ou mal type")
    void load_missingValues_returnsNull() {
        assertNull(loader.load(Map.of("mode", "EQUALS")));
        assertNull(loader.load(Map.of("values", List.of())));
        assertNull(loader.load(Map.of("values", "Untradeable")));
    }
}
