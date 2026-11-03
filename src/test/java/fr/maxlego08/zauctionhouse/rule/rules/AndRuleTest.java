package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.Rules;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Couvre {@link AndRule} et, par opposition, le OU logique porte par {@link Rules}
 * (il n'existe pas de classe OrRule dediee : le OU est implemente par le record Rules).
 */
class AndRuleTest {

    private static final Rule ALWAYS_TRUE = context -> true;
    private static final Rule ALWAYS_FALSE = context -> false;

    @Test
    @DisplayName("matches : vrai seulement si TOUS les enfants matchent")
    void matches_allChildrenMatch_returnsTrue() {
        AndRule rule = new AndRule(List.of(ALWAYS_TRUE, ALWAYS_TRUE, ALWAYS_TRUE));

        assertTrue(rule.matches(RuleTestItems.context(Material.PAPER)));
    }

    @Test
    @DisplayName("matches : faux des qu'un enfant ne matche pas, quelle que soit sa position")
    void matches_oneChildFails_returnsFalse() {
        ItemRuleContext context = RuleTestItems.context(Material.PAPER);

        assertFalse(new AndRule(List.of(ALWAYS_FALSE, ALWAYS_TRUE)).matches(context));
        assertFalse(new AndRule(List.of(ALWAYS_TRUE, ALWAYS_FALSE)).matches(context));
        assertFalse(new AndRule(List.of(ALWAYS_TRUE, ALWAYS_FALSE, ALWAYS_TRUE)).matches(context));
    }

    @Test
    @DisplayName("matches : un AND vide ne matche jamais (pas de 'vrai par vacuite')")
    void matches_empty_returnsFalse() {
        assertFalse(new AndRule(List.of()).matches(RuleTestItems.context(Material.PAPER)));
    }

    @Test
    @DisplayName("matches : un AND a un seul enfant se comporte comme cet enfant")
    void matches_singleChild_delegates() {
        ItemRuleContext context = RuleTestItems.context(Material.PAPER);

        assertTrue(new AndRule(List.of(ALWAYS_TRUE)).matches(context));
        assertFalse(new AndRule(List.of(ALWAYS_FALSE)).matches(context));
    }

    @Test
    @DisplayName("matches : court-circuit, les enfants suivants ne sont pas evalues apres un echec")
    void matches_shortCircuits() {
        AtomicInteger calls = new AtomicInteger();
        Rule counting = context -> {
            calls.incrementAndGet();
            return true;
        };
        AndRule rule = new AndRule(List.of(ALWAYS_FALSE, counting, counting));

        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("matches : combinaison reelle materiau + lore + model data")
    void matches_realRulesCombination() {
        AndRule rule = new AndRule(List.of(
                new MaterialRule(Set.of(Material.PAPER)),
                new LoreContainsRule(List.of("Untradeable")),
                new ModelDataRule(Set.of(1001))
        ));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.item(Material.PAPER, null, List.of("Untradeable"), 1001, null))));
        // Un seul critere manquant suffit a refuser
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.item(Material.PAPER, null, List.of("Untradeable"), 1002, null))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.item(Material.PAPER, null, List.of("Tradeable"), 1001, null))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.item(Material.BOOK, null, List.of("Untradeable"), 1001, null))));
    }

    @Test
    @DisplayName("matches : AND imbrique dans un AND")
    void matches_nestedAnd() {
        AndRule inner = new AndRule(List.of(ALWAYS_TRUE, ALWAYS_TRUE));
        AndRule outer = new AndRule(List.of(inner, ALWAYS_TRUE));
        AndRule outerFailing = new AndRule(List.of(new AndRule(List.of(ALWAYS_TRUE, ALWAYS_FALSE)), ALWAYS_TRUE));
        ItemRuleContext context = RuleTestItems.context(Material.PAPER);

        assertTrue(outer.matches(context));
        assertFalse(outerFailing.matches(context));
    }

    @Test
    @DisplayName("matches : AND contenant un OU (Rules) imbrique")
    void matches_andContainingOr() {
        Rules or = new Rules(true, List.of(new MaterialRule(Set.of(Material.PAPER)), new MaterialRule(Set.of(Material.BOOK))));
        AndRule rule = new AndRule(List.of(or, new NameContainsRule(List.of("Voucher"))));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Voucher"))));
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.BOOK, "Voucher"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.STICK, "Voucher"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "Key"))));
    }

    @Test
    @DisplayName("matches : un OU (Rules) imbrique mais desactive fait echouer le AND")
    void matches_andContainingDisabledOr_returnsFalse() {
        Rules disabledOr = new Rules(false, List.of(ALWAYS_TRUE));
        AndRule rule = new AndRule(List.of(disabledOr, ALWAYS_TRUE));

        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
    }

    @Test
    @DisplayName("isValid : faux si vide, faux si UN enfant invalide, vrai si tous valides")
    void isValid_requiresAllChildrenValid() {
        Rule invalid = new Rule() {
            @Override
            public boolean matches(ItemRuleContext context) {
                return true;
            }

            @Override
            public boolean isValid() {
                return false;
            }
        };

        assertFalse(new AndRule(List.of()).isValid());
        assertFalse(new AndRule(List.of(ALWAYS_TRUE, invalid)).isValid());
        assertTrue(new AndRule(List.of(ALWAYS_TRUE, new MaterialRule(Set.of(Material.PAPER)))).isValid());
        // La validite ne depend pas du resultat de matches : une regle toujours fausse reste valide
        assertTrue(new AndRule(List.of(ALWAYS_FALSE)).isValid());
    }
}
