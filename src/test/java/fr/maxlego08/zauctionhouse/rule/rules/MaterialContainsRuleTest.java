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

class MaterialContainsRuleTest {

    @ParameterizedTest(name = "{0} contient WOOL")
    @EnumSource(value = Material.class, names = {"WHITE_WOOL", "RED_WOOL", "BLACK_WOOL"})
    @DisplayName("matches : tous les blocs de laine via le motif WOOL")
    void matches_allWool(Material material) {
        MaterialContainsRule rule = new MaterialContainsRule(List.of("WOOL"));

        assertTrue(rule.matches(RuleTestItems.context(material)));
    }

    @Test
    @DisplayName("matches : le motif peut etre au milieu du nom (ni prefixe ni suffixe)")
    void matches_patternInMiddle() {
        MaterialContainsRule rule = new MaterialContainsRule(List.of("DIAMOND"));

        assertTrue(rule.matches(RuleTestItems.context(Material.DEEPSLATE_DIAMOND_ORE)));
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
    }

    @Test
    @DisplayName("matches : faux quand le motif est absent")
    void matches_patternAbsent_returnsFalse() {
        MaterialContainsRule rule = new MaterialContainsRule(List.of("WOOL"));

        assertFalse(rule.matches(RuleTestItems.context(Material.STONE)));
        assertFalse(rule.matches(RuleTestItems.context(Material.WHITE_CARPET)));
    }

    @Test
    @DisplayName("matches : motif normalise en majuscules")
    void matches_patternCaseInsensitive() {
        MaterialContainsRule rule = new MaterialContainsRule(List.of("banner"));

        assertTrue(rule.matches(RuleTestItems.context(Material.RED_BANNER)));
    }

    @Test
    @DisplayName("matches : plusieurs motifs = OU logique")
    void matches_multiplePatterns_anyMatches() {
        MaterialContainsRule rule = new MaterialContainsRule(List.of("CANDLE", "CARPET"));

        assertTrue(rule.matches(RuleTestItems.context(Material.WHITE_CANDLE)));
        assertTrue(rule.matches(RuleTestItems.context(Material.WHITE_CARPET)));
        assertFalse(rule.matches(RuleTestItems.context(Material.WHITE_WOOL)));
    }

    @Test
    @DisplayName("matches : liste vide ne matche jamais")
    void matches_emptyList_returnsFalse() {
        assertFalse(new MaterialContainsRule(List.of()).matches(RuleTestItems.context(Material.WHITE_WOOL)));
    }

    @Test
    @DisplayName("isValid : vrai si au moins un motif")
    void isValid_dependsOnEmptiness() {
        assertTrue(new MaterialContainsRule(List.of("WOOL")).isValid());
        assertFalse(new MaterialContainsRule(List.of()).isValid());
    }
}
