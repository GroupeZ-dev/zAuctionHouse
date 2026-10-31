package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameContainsRuleTest {

    @Test
    @DisplayName("matches : vrai quand le nom contient l'aiguille")
    void matches_nameContainsNeedle_returnsTrue() {
        NameContainsRule rule = new NameContainsRule(List.of("Voucher"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.NETHER_STAR, "Rank Voucher (VIP)"))));
    }

    @Test
    @DisplayName("matches : insensible a la casse des deux cotes")
    void matches_caseInsensitive() {
        NameContainsRule rule = new NameContainsRule(List.of("VOUCHER"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "my voucher"))));
        assertTrue(new NameContainsRule(List.of("voucher")).matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "MY VOUCHER"))));
    }

    @Test
    @DisplayName("matches : les couleurs du nom sont ignorees (texte brut)")
    void matches_ignoresColors() {
        NameContainsRule rule = new NameContainsRule(List.of("Epic Sword"));
        Component colored = Component.text("Epic ").color(NamedTextColor.RED).append(Component.text("Sword").color(NamedTextColor.GOLD));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.itemWithComponents(Material.DIAMOND_SWORD, colored, null, null, null))));
    }

    @Test
    @DisplayName("matches : faux quand aucune aiguille n'est presente")
    void matches_noNeedlePresent_returnsFalse() {
        NameContainsRule rule = new NameContainsRule(List.of("Voucher", "Key"));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Plain paper"))));
    }

    @Test
    @DisplayName("matches : faux pour un item sans nom (sans meta ou meta sans nom)")
    void matches_noDisplayName_returnsFalse() {
        NameContainsRule rule = new NameContainsRule(List.of("a"));

        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.PAPER, "a lore"))));
    }

    @Test
    @DisplayName("matches : plusieurs aiguilles = OU logique")
    void matches_multipleNeedles_anyMatches() {
        NameContainsRule rule = new NameContainsRule(List.of("Key", "Voucher"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.TRIPWIRE_HOOK, "Crate Key"))));
    }

    @Test
    @DisplayName("isValid : vrai si au moins une aiguille")
    void isValid_dependsOnEmptiness() {
        assertTrue(new NameContainsRule(List.of("x")).isValid());
        assertFalse(new NameContainsRule(List.of()).isValid());
    }
}
