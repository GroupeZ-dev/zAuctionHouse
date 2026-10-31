package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialRuleTest {

    @Test
    @DisplayName("matches : vrai quand le materiau est dans l'ensemble")
    void matches_materialInSet_returnsTrue() {
        MaterialRule rule = new MaterialRule(Set.of(Material.DIAMOND, Material.EMERALD));

        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertTrue(rule.matches(RuleTestItems.context(Material.EMERALD)));
    }

    @Test
    @DisplayName("matches : faux quand le materiau est absent de l'ensemble")
    void matches_materialNotInSet_returnsFalse() {
        MaterialRule rule = new MaterialRule(Set.of(Material.DIAMOND));

        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND_BLOCK)));
        assertFalse(rule.matches(RuleTestItems.context(Material.STONE)));
    }

    @Test
    @DisplayName("matches : un ensemble vide ne matche jamais")
    void matches_emptySet_returnsFalse() {
        MaterialRule rule = new MaterialRule(EnumSet.noneOf(Material.class));

        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND)));
    }

    @Test
    @DisplayName("matches : ignore la meta, seul le type compte")
    void matches_ignoresMeta() {
        MaterialRule rule = new MaterialRule(Set.of(Material.DIAMOND));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.DIAMOND, "Fancy"))));
    }

    @Test
    @DisplayName("isValid : vrai si au moins un materiau, faux sinon")
    void isValid_dependsOnEmptiness() {
        assertTrue(new MaterialRule(Set.of(Material.DIAMOND)).isValid());
        assertFalse(new MaterialRule(Set.of()).isValid());
    }
}
