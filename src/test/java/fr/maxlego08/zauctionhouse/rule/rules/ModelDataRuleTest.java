package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDataRuleTest {

    @Test
    @DisplayName("matches : vrai pour une valeur exacte de l'ensemble")
    void matches_exactValue_returnsTrue() {
        ModelDataRule rule = new ModelDataRule(Set.of(1001, 1002));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 1001))));
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 1002))));
    }

    @Test
    @DisplayName("matches : faux pour une valeur hors ensemble (voisines incluses)")
    void matches_otherValue_returnsFalse() {
        ModelDataRule rule = new ModelDataRule(Set.of(1001));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 1000))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 1002))));
    }

    @Test
    @DisplayName("matches : un item SANS custom model data ne matche pas, meme si l'ensemble contient 0")
    void matches_noCustomModelData_returnsFalseEvenForZero() {
        ModelDataRule rule = new ModelDataRule(Set.of(0));

        // Le contexte rend 0 par defaut mais hasCustomModelData() est faux : la regle doit refuser
        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "named"))));
    }

    @Test
    @DisplayName("matches : un item avec custom model data 0 explicite matche la valeur 0")
    void matches_explicitZero_returnsTrue() {
        ModelDataRule rule = new ModelDataRule(Set.of(0));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 0))));
    }

    @Test
    @DisplayName("isValid : vrai si au moins une valeur")
    void isValid_dependsOnEmptiness() {
        assertTrue(new ModelDataRule(Set.of(1)).isValid());
        assertFalse(new ModelDataRule(Set.of()).isValid());
    }
}
