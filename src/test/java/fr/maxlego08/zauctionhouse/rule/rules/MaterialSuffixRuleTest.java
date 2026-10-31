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

class MaterialSuffixRuleTest {

    @ParameterizedTest(name = "{0} se termine par _SWORD")
    @EnumSource(value = Material.class, names = {"WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD", "GOLDEN_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD"})
    @DisplayName("matches : toutes les epees via le suffixe _SWORD")
    void matches_allSwords(Material material) {
        MaterialSuffixRule rule = new MaterialSuffixRule(List.of("_SWORD"));

        assertTrue(rule.matches(RuleTestItems.context(material)));
    }

    @Test
    @DisplayName("matches : faux quand le nom ne se termine pas par le suffixe")
    void matches_noSuffix_returnsFalse() {
        MaterialSuffixRule rule = new MaterialSuffixRule(List.of("_SWORD"));

        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        // "_AXE" n'est pas "_SWORD" ; et "SWORD" en prefixe n'existe pas
        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND_AXE)));
    }

    @Test
    @DisplayName("matches : le suffixe est normalise en majuscules (insensible a la casse de la config)")
    void matches_suffixCaseInsensitive() {
        MaterialSuffixRule rule = new MaterialSuffixRule(List.of("_sword"));

        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
    }

    @Test
    @DisplayName("matches : plusieurs suffixes = OU logique")
    void matches_multipleSuffixes_anyMatches() {
        MaterialSuffixRule rule = new MaterialSuffixRule(List.of("_HELMET", "_BOOTS"));

        assertTrue(rule.matches(RuleTestItems.context(Material.IRON_HELMET)));
        assertTrue(rule.matches(RuleTestItems.context(Material.LEATHER_BOOTS)));
        assertFalse(rule.matches(RuleTestItems.context(Material.IRON_CHESTPLATE)));
    }

    @Test
    @DisplayName("matches : un suffixe sans underscore matche aussi (PICKAXE vs _PICKAXE)")
    void matches_suffixWithoutUnderscore() {
        MaterialSuffixRule rule = new MaterialSuffixRule(List.of("AXE"));

        // DIAMOND_PICKAXE et DIAMOND_AXE se terminent tous deux par AXE
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND_PICKAXE)));
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND_AXE)));
    }

    @Test
    @DisplayName("matches : liste vide ne matche jamais")
    void matches_emptyList_returnsFalse() {
        assertFalse(new MaterialSuffixRule(List.of()).matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
    }

    @Test
    @DisplayName("isValid : vrai si au moins un suffixe")
    void isValid_dependsOnEmptiness() {
        assertTrue(new MaterialSuffixRule(List.of("_SWORD")).isValid());
        assertFalse(new MaterialSuffixRule(List.of()).isValid());
    }
}
