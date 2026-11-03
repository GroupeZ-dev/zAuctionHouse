package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialPrefixRuleTest {

    @ParameterizedTest(name = "{0} commence par DIAMOND_")
    @EnumSource(value = Material.class, names = {"DIAMOND_SWORD", "DIAMOND_PICKAXE", "DIAMOND_HELMET", "DIAMOND_BLOCK", "DIAMOND_ORE"})
    @DisplayName("matches : tout ce qui commence par DIAMOND_")
    void matches_diamondPrefix(Material material) {
        MaterialPrefixRule rule = new MaterialPrefixRule(List.of("DIAMOND_"));

        assertTrue(rule.matches(RuleTestItems.context(material)));
    }

    @Test
    @DisplayName("matches : DIAMOND seul ne commence pas par DIAMOND_ (underscore significatif)")
    void matches_prefixWithUnderscore_excludesBareMaterial() {
        MaterialPrefixRule rule = new MaterialPrefixRule(List.of("DIAMOND_"));

        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertFalse(rule.matches(RuleTestItems.context(Material.DEEPSLATE_DIAMOND_ORE)));
    }

    @Test
    @DisplayName("matches : prefixe normalise en majuscules")
    void matches_prefixCaseInsensitive() {
        MaterialPrefixRule rule = new MaterialPrefixRule(List.of("iron_"));

        assertTrue(rule.matches(RuleTestItems.context(Material.IRON_INGOT)));
    }

    @Test
    @DisplayName("matches : plusieurs prefixes = OU logique")
    void matches_multiplePrefixes_anyMatches() {
        MaterialPrefixRule rule = new MaterialPrefixRule(List.of("GOLDEN_", "NETHERITE_"));

        assertTrue(rule.matches(RuleTestItems.context(Material.GOLDEN_APPLE)));
        assertTrue(rule.matches(RuleTestItems.context(Material.NETHERITE_INGOT)));
        assertFalse(rule.matches(RuleTestItems.context(Material.GOLD_INGOT)));
    }

    @Test
    @DisplayName("matches : liste vide ne matche jamais")
    void matches_emptyList_returnsFalse() {
        assertFalse(new MaterialPrefixRule(List.of()).matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
    }

    @Test
    @DisplayName("isValid : vrai si au moins un prefixe")
    void isValid_dependsOnEmptiness() {
        assertTrue(new MaterialPrefixRule(List.of("DIAMOND_")).isValid());
        assertFalse(new MaterialPrefixRule(List.of()).isValid());
    }
}
