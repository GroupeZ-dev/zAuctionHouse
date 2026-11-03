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

class LoreContainsRuleTest {

    @Test
    @DisplayName("matches : vrai quand une ligne du lore contient l'aiguille")
    void matches_lineContainsNeedle_returnsTrue() {
        LoreContainsRule rule = new LoreContainsRule(List.of("Untradeable"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "A fine blade", "This item is Untradeable"))));
    }

    @Test
    @DisplayName("matches : insensible a la casse")
    void matches_caseInsensitive() {
        LoreContainsRule rule = new LoreContainsRule(List.of("UNTRADEABLE"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "untradeable item"))));
    }

    @Test
    @DisplayName("matches : les lignes sont jointes par un espace, une aiguille peut chevaucher deux lignes")
    void matches_needleSpanningTwoLines_returnsTrue() {
        // Le lore entier est joint avec " " : "Soul" + " " + "bound" => "soul bound"
        LoreContainsRule rule = new LoreContainsRule(List.of("soul bound"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Soul", "bound"))));
    }

    @Test
    @DisplayName("matches : une aiguille collee (sans espace) ne chevauche pas deux lignes")
    void matches_needleWithoutSeparator_doesNotSpanLines() {
        LoreContainsRule rule = new LoreContainsRule(List.of("soulbound"));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Soul", "bound"))));
    }

    @Test
    @DisplayName("matches : les couleurs du lore sont ignorees")
    void matches_coloredLore_isStripped() {
        LoreContainsRule rule = new LoreContainsRule(List.of("Untradeable"));
        List<Component> lore = List.of(Component.text("Untradeable").color(NamedTextColor.RED));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.itemWithComponents(Material.DIAMOND_SWORD, null, lore, null, null))));
    }

    @Test
    @DisplayName("matches : faux quand aucune aiguille n'est presente")
    void matches_noNeedle_returnsFalse() {
        LoreContainsRule rule = new LoreContainsRule(List.of("Untradeable"));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Tradeable"))));
    }

    @Test
    @DisplayName("matches : faux sans lore (sans meta, meta sans lore, lore vide)")
    void matches_noLore_returnsFalse() {
        LoreContainsRule rule = new LoreContainsRule(List.of("a"));

        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.DIAMOND_SWORD, "a name"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD))));
    }

    @Test
    @DisplayName("matches : plusieurs aiguilles = OU logique")
    void matches_multipleNeedles_anyMatches() {
        LoreContainsRule rule = new LoreContainsRule(List.of("Soulbound", "Untradeable"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Soulbound"))));
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.DIAMOND_SWORD, "Untradeable"))));
    }

    @Test
    @DisplayName("isValid : vrai si au moins une aiguille")
    void isValid_dependsOnEmptiness() {
        assertTrue(new LoreContainsRule(List.of("x")).isValid());
        assertFalse(new LoreContainsRule(List.of()).isValid());
    }
}
