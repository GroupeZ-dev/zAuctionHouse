package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDataRangeRuleTest {

    @ParameterizedTest(name = "[100;200] contient {0} ? {1}")
    @CsvSource({
            "99, false",
            "100, true",
            "150, true",
            "200, true",
            "201, false",
    })
    @DisplayName("matches : bornes min et max INCLUSES")
    void matches_boundsInclusive(int value, boolean expected) {
        ModelDataRangeRule rule = new ModelDataRangeRule(100, 200);

        assertEquals(expected, rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, value))));
    }

    @Test
    @DisplayName("matches : plage degeneree min == max ne matche que cette valeur")
    void matches_singlePointRange() {
        ModelDataRangeRule rule = new ModelDataRangeRule(42, 42);

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 42))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 41))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 43))));
    }

    @Test
    @DisplayName("matches : un item sans custom model data ne matche pas, meme si 0 est dans la plage")
    void matches_noCustomModelData_returnsFalse() {
        ModelDataRangeRule rule = new ModelDataRangeRule(0, 10);

        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
    }

    @Test
    @DisplayName("matches : plage inversee (min > max) ne matche jamais")
    void matches_invertedRange_neverMatches() {
        ModelDataRangeRule rule = new ModelDataRangeRule(200, 100);

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 150))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 100))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 200))));
    }

    @Test
    @DisplayName("matches : plage ouverte [0; MAX_VALUE] accepte toute valeur positive")
    void matches_openRange_acceptsAnyValue() {
        ModelDataRangeRule rule = new ModelDataRangeRule(0, Integer.MAX_VALUE);

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, Integer.MAX_VALUE))));
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 0))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, -1))));
    }

    @Test
    @DisplayName("isValid : vrai si min <= max, faux si inversee")
    void isValid_requiresOrderedBounds() {
        assertTrue(new ModelDataRangeRule(1, 10).isValid());
        assertTrue(new ModelDataRangeRule(5, 5).isValid());
        assertFalse(new ModelDataRangeRule(10, 1).isValid());
    }
}
