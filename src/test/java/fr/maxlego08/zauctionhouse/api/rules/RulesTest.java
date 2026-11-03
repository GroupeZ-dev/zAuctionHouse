package fr.maxlego08.zauctionhouse.api.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Rules} est le OU logique du systeme (une liste activable de regles) ; couvre aussi la
 * valeur par defaut de {@link Rule#isValid()}.
 */
class RulesTest {

    private static final Rule ALWAYS_TRUE = context -> true;
    private static final Rule ALWAYS_FALSE = context -> false;
    private static final Rule INVALID = new Rule() {
        @Override
        public boolean matches(ItemRuleContext context) {
            return true;
        }

        @Override
        public boolean isValid() {
            return false;
        }
    };

    private static ItemRuleContext anyContext() {
        return RuleTestItems.context(Material.PAPER);
    }

    @Test
    @DisplayName("Rule.isValid : vrai par defaut")
    void rule_isValid_defaultsToTrue() {
        assertTrue(ALWAYS_TRUE.isValid());
        assertTrue(ALWAYS_FALSE.isValid());
    }

    @Test
    @DisplayName("constructeur : copie defensive et liste immuable")
    void constructor_copiesList() {
        List<Rule> source = new ArrayList<>(List.of(ALWAYS_TRUE));
        Rules rules = new Rules(true, source);
        source.add(ALWAYS_FALSE);

        assertEquals(1, rules.rules().size());
        assertThrows(UnsupportedOperationException.class, () -> rules.rules().add(ALWAYS_FALSE));
    }

    @Test
    @DisplayName("constructeur : liste null => NullPointerException")
    void constructor_nullList_throws() {
        assertThrows(NullPointerException.class, () -> new Rules(true, null));
    }

    @Test
    @DisplayName("matches : desactive => faux meme si une regle matche")
    void matches_disabled_returnsFalse() {
        assertFalse(new Rules(false, List.of(ALWAYS_TRUE)).matches(anyContext()));
    }

    @Test
    @DisplayName("matches : active => vrai des qu'UNE regle matche (OU)")
    void matches_enabled_anyMatch() {
        assertTrue(new Rules(true, List.of(ALWAYS_FALSE, ALWAYS_TRUE)).matches(anyContext()));
        assertTrue(new Rules(true, List.of(ALWAYS_TRUE, ALWAYS_FALSE)).matches(anyContext()));
        assertFalse(new Rules(true, List.of(ALWAYS_FALSE, ALWAYS_FALSE)).matches(anyContext()));
    }

    @Test
    @DisplayName("matches : active mais vide => faux")
    void matches_enabledEmpty_returnsFalse() {
        assertFalse(new Rules(true, List.of()).matches(anyContext()));
    }

    @Test
    @DisplayName("matches : court-circuit a la premiere regle qui matche")
    void matches_shortCircuits() {
        AtomicInteger calls = new AtomicInteger();
        Rule counting = context -> {
            calls.incrementAndGet();
            return true;
        };

        assertTrue(new Rules(true, List.of(ALWAYS_TRUE, counting)).matches(anyContext()));
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("withEnabled : nouvelle instance, memes regles, drapeau change")
    void withEnabled_returnsCopyWithFlag() {
        Rules original = new Rules(false, List.of(ALWAYS_TRUE));

        Rules enabled = original.withEnabled(true);

        assertNotSame(original, enabled);
        assertFalse(original.enabled());
        assertTrue(enabled.enabled());
        assertEquals(original.rules(), enabled.rules());
        assertTrue(enabled.matches(anyContext()));
        assertFalse(original.matches(anyContext()));
    }

    @Test
    @DisplayName("withAddedRule : ajoute en fin, conserve le drapeau, ne modifie pas l'original")
    void withAddedRule_appendsImmutably() {
        Rules original = new Rules(true, List.of(ALWAYS_FALSE));

        Rules added = original.withAddedRule(ALWAYS_TRUE);

        assertEquals(1, original.rules().size());
        assertEquals(List.of(ALWAYS_FALSE, ALWAYS_TRUE), added.rules());
        assertTrue(added.enabled());
        assertTrue(added.matches(anyContext()));
        assertFalse(original.matches(anyContext()));
    }

    @Test
    @DisplayName("isValid : faux si vide, vrai si AU MOINS une regle valide (contrairement a AndRule)")
    void isValid_anyValid() {
        assertFalse(new Rules(true, List.of()).isValid());
        assertFalse(new Rules(true, List.of(INVALID)).isValid());
        assertTrue(new Rules(true, List.of(INVALID, ALWAYS_TRUE)).isValid());
        // La validite ne depend pas du drapeau enabled
        assertTrue(new Rules(false, List.of(ALWAYS_TRUE)).isValid());
    }

    @Test
    @DisplayName("emptyDisabled : desactive, vide, invalide, ne matche rien")
    void emptyDisabled_state() {
        Rules rules = Rules.emptyDisabled();

        assertFalse(rules.enabled());
        assertTrue(rules.rules().isEmpty());
        assertFalse(rules.isValid());
        assertFalse(rules.matches(anyContext()));
    }

    @Test
    @DisplayName("egalite de record : memes drapeau et regles => egaux")
    void recordEquality() {
        assertEquals(new Rules(true, List.of(ALWAYS_TRUE)), new Rules(true, List.of(ALWAYS_TRUE)));
        assertNotEquals(new Rules(true, List.of(ALWAYS_TRUE)), new Rules(false, List.of(ALWAYS_TRUE)));
        assertNotEquals(new Rules(true, List.of(ALWAYS_TRUE)), new Rules(true, List.of(ALWAYS_FALSE)));
    }
}
