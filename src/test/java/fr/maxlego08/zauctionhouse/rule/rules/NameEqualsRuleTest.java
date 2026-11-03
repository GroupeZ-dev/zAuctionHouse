package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameEqualsRuleTest {

    @Nested
    @DisplayName("ignoreCase = true")
    class IgnoreCase {

        private final NameEqualsRule rule = new NameEqualsRule(List.of("Voucher", "Crate Key"), true);

        @Test
        @DisplayName("matches : egalite exacte")
        void matches_exactName_returnsTrue() {
            assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Voucher"))));
            assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Crate Key"))));
        }

        @Test
        @DisplayName("matches : casse differente acceptee")
        void matches_differentCase_returnsTrue() {
            assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "VOUCHER"))));
            assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "crate key"))));
        }

        @Test
        @DisplayName("matches : un nom qui ne fait que contenir la valeur ne matche pas")
        void matches_containsOnly_returnsFalse() {
            assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Rank Voucher"))));
            assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Voucher "))));
        }

        @Test
        @DisplayName("matches : les couleurs sont retirees avant comparaison")
        void matches_coloredName_isStripped() {
            Component colored = Component.text("Voucher").color(NamedTextColor.GOLD);
            assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.itemWithComponents(Material.PAPER, colored, null, null, null))));
        }

        @Test
        @DisplayName("matches : faux sans nom d'affichage")
        void matches_noDisplayName_returnsFalse() {
            assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
        }
    }

    @Nested
    @DisplayName("ignoreCase = false")
    class CaseSensitive {

        private final NameEqualsRule rule = new NameEqualsRule(List.of("Voucher"), false);

        @Test
        @DisplayName("matches : casse identique requise")
        void matches_sameCase_returnsTrue() {
            assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Voucher"))));
        }

        @Test
        @DisplayName("matches : casse differente refusee")
        void matches_differentCase_returnsFalse() {
            assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "voucher"))));
            assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "VOUCHER"))));
        }
    }

    @Test
    @DisplayName("isValid : vrai si au moins un nom, quel que soit ignoreCase")
    void isValid_dependsOnEmptiness() {
        assertTrue(new NameEqualsRule(List.of("x"), true).isValid());
        assertTrue(new NameEqualsRule(List.of("x"), false).isValid());
        assertFalse(new NameEqualsRule(List.of(), true).isValid());
        assertFalse(new NameEqualsRule(List.of(), false).isValid());
    }
}
