package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.NameContainsRule;
import fr.maxlego08.zauctionhouse.rule.rules.NameEqualsRule;
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

class NameRuleLoaderTest {

    private final NameRuleLoader loader = new NameRuleLoader();

    @Test
    @DisplayName("getType : 'name'")
    void getType_isName() {
        assertEquals("name", loader.getType());
    }

    @Test
    @DisplayName("load : sans mode => NameContainsRule")
    void load_noMode_returnsContainsRule() {
        Rule rule = loader.load(Map.of("values", List.of("Voucher")));

        assertInstanceOf(NameContainsRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Rank Voucher"))));
    }

    @ParameterizedTest(name = "mode={0}")
    @ValueSource(strings = {"CONTAINS", "contains", "STARTS_WITH", "REGEX", "anything-else"})
    @DisplayName("load : tout mode autre que EQUALS retombe sur NameContainsRule")
    void load_otherModes_returnContainsRule(String mode) {
        Rule rule = loader.load(Map.of("mode", mode, "values", List.of("Voucher")));

        assertInstanceOf(NameContainsRule.class, rule);
    }

    @ParameterizedTest(name = "mode={0}")
    @ValueSource(strings = {"EQUALS", "equals", "Equals"})
    @DisplayName("load : mode EQUALS (insensible a la casse) => NameEqualsRule")
    void load_equalsMode_returnsEqualsRule(String mode) {
        Rule rule = loader.load(Map.of("mode", mode, "values", List.of("Voucher")));

        assertInstanceOf(NameEqualsRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Voucher"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Rank Voucher"))));
    }

    @Test
    @DisplayName("load : EQUALS ignore la casse par defaut (ignore-case absent => true)")
    void load_equalsMode_defaultIgnoreCase() {
        Rule rule = loader.load(Map.of("mode", "EQUALS", "values", List.of("Voucher")));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "VOUCHER"))));
    }

    @Test
    @DisplayName("load : EQUALS avec ignore-case: false => sensible a la casse")
    void load_equalsMode_ignoreCaseFalse() {
        Rule rule = loader.load(Map.of("mode", "EQUALS", "ignore-case", false, "values", List.of("Voucher")));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Voucher"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "VOUCHER"))));
    }

    @Test
    @DisplayName("load : ignore-case accepte aussi une chaine 'false'")
    void load_equalsMode_ignoreCaseAsString() {
        Rule rule = loader.load(Map.of("mode", "EQUALS", "ignore-case", "false", "values", List.of("Voucher")));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "voucher"))));
    }

    @Test
    @DisplayName("load : null si values absent, vide ou mal type, quel que soit le mode")
    void load_missingValues_returnsNull() {
        assertNull(loader.load(Map.of("mode", "EQUALS")));
        assertNull(loader.load(Map.of("mode", "CONTAINS", "values", List.of())));
        assertNull(loader.load(Map.of("values", "Voucher")));
    }
}
