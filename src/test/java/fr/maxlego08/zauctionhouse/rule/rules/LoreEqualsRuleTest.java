package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreEqualsRuleTest {

    @Test
    @DisplayName("matches : vrai quand UNE ligne est exactement egale a une valeur")
    void matches_oneLineEquals_returnsTrue() {
        LoreEqualsRule rule = new LoreEqualsRule(List.of("Soulbound"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "A fine blade", "Soulbound", "Legendary"))));
    }

    @Test
    @DisplayName("matches : comparaison ligne par ligne, insensible a la casse")
    void matches_caseInsensitive() {
        LoreEqualsRule rule = new LoreEqualsRule(List.of("SOULBOUND"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "soulbound"))));
    }

    @Test
    @DisplayName("matches : une ligne qui ne fait que contenir la valeur ne matche pas")
    void matches_lineContainsOnly_returnsFalse() {
        LoreEqualsRule rule = new LoreEqualsRule(List.of("Soulbound"));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "This item is Soulbound"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Soulbound "))));
    }

    @Test
    @DisplayName("matches : la valeur n'est pas comparee au lore joint, seulement aux lignes")
    void matches_valueSpanningLines_returnsFalse() {
        LoreEqualsRule rule = new LoreEqualsRule(List.of("soul bound"));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Soul", "bound"))));
    }

    @Test
    @DisplayName("matches : faux sans lore")
    void matches_noLore_returnsFalse() {
        LoreEqualsRule rule = new LoreEqualsRule(List.of("a"));

        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD))));
    }

    @Test
    @DisplayName("matches : plusieurs valeurs = OU logique")
    void matches_multipleValues_anyMatches() {
        LoreEqualsRule rule = new LoreEqualsRule(List.of("Soulbound", "Untradeable"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Untradeable"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Tradeable"))));
    }

    @Test
    @DisplayName("isValid : vrai si au moins une valeur")
    void isValid_dependsOnEmptiness() {
        assertTrue(new LoreEqualsRule(List.of("x")).isValid());
        assertFalse(new LoreEqualsRule(List.of()).isValid());
    }
}
