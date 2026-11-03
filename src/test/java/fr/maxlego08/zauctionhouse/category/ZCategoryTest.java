package fr.maxlego08.zauctionhouse.category;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialRule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialSuffixRule;
import fr.maxlego08.zauctionhouse.rule.rules.ModelDataRule;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZCategoryTest {

    private static final Rule ALWAYS_TRUE = context -> true;
    private static final Rule ALWAYS_FALSE = context -> false;

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("id null => NullPointerException")
        void nullId_throws() {
            assertThrows(NullPointerException.class, () -> new ZCategory(null, "x", List.of(), List.of(), false));
        }

        @Test
        @DisplayName("display-name null => retombe sur l'id")
        void nullDisplayName_fallsBackToId() {
            ZCategory category = new ZCategory("weapons", null, List.of(), List.of(), false);

            assertEquals("weapons", category.getDisplayName());
        }

        @Test
        @DisplayName("listes null => listes vides ; listes fournies => copiees et immuables")
        void lists_areDefensive() {
            ZCategory nulls = new ZCategory("a", "A", null, null, false);
            assertTrue(nulls.getRules().isEmpty());
            assertTrue(nulls.getBannedRules().isEmpty());

            List<Rule> source = new ArrayList<>(List.of(ALWAYS_TRUE));
            ZCategory copied = new ZCategory("b", "B", source, source, false);
            source.add(ALWAYS_FALSE);

            assertEquals(1, copied.getRules().size(), "copie defensive");
            assertEquals(1, copied.getBannedRules().size());
            assertThrows(UnsupportedOperationException.class, () -> copied.getRules().add(ALWAYS_FALSE));
        }

        @Test
        @DisplayName("getters : id, displayName, miscellaneous")
        void getters() {
            ZCategory category = new ZCategory("weapons", "Weapons", List.of(ALWAYS_TRUE), List.of(), false);

            assertEquals("weapons", category.getId());
            assertEquals("Weapons", category.getDisplayName());
            assertFalse(category.isMiscellaneous());
        }

        @Test
        @DisplayName("miscellaneous() : fabrique sans regles, marquee fallback")
        void miscellaneousFactory() {
            ZCategory misc = ZCategory.miscellaneous("misc", "Misc");

            assertEquals("misc", misc.getId());
            assertEquals("Misc", misc.getDisplayName());
            assertTrue(misc.isMiscellaneous());
            assertTrue(misc.getRules().isEmpty());
            assertTrue(misc.getBannedRules().isEmpty());
        }
    }

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("contexte null => faux, meme pour misc")
        void nullContext_returnsFalse() {
            assertFalse(new ZCategory("a", "A", List.of(ALWAYS_TRUE), List.of(), false).matches(null));
            assertFalse(ZCategory.miscellaneous("misc", "Misc").matches(null));
        }

        @Test
        @DisplayName("categorie normale : OU logique entre les regles")
        void regular_anyRuleMatches() {
            ZCategory category = new ZCategory("weapons", "W", List.of(
                    new MaterialSuffixRule(List.of("_SWORD")),
                    new MaterialRule(Set.of(Material.BOW))
            ), List.of(), false);

            assertTrue(category.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
            assertTrue(category.matches(RuleTestItems.context(Material.BOW)));
            assertFalse(category.matches(RuleTestItems.context(Material.STONE)));
        }

        @Test
        @DisplayName("categorie normale sans regles : ne matche jamais")
        void regular_noRules_neverMatches() {
            ZCategory category = new ZCategory("empty", "E", List.of(), List.of(), false);

            assertFalse(category.matches(RuleTestItems.context(Material.STONE)));
        }

        @Test
        @DisplayName("misc : matche tout ce qui n'est pas banni")
        void misc_matchesEverything() {
            ZCategory misc = ZCategory.miscellaneous("misc", "M");

            assertTrue(misc.matches(RuleTestItems.context(Material.STONE)));
            assertTrue(misc.matches(RuleTestItems.context(RuleTestItems.named(Material.NETHER_STAR, "x"))));
        }

        @Test
        @DisplayName("banned-rules prioritaires : un item banni est exclu meme s'il matche une regle")
        void banned_overridesRules() {
            ZCategory tools = new ZCategory("tools", "T",
                    List.of(new MaterialSuffixRule(List.of("_HOE"))),
                    List.of(new ModelDataRule(Set.of(300))), false);

            assertTrue(tools.matches(RuleTestItems.context(Material.NETHERITE_HOE)));
            assertFalse(tools.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.NETHERITE_HOE, 300))));
            assertTrue(tools.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.NETHERITE_HOE, 301))));
        }

        @Test
        @DisplayName("banned-rules s'appliquent aussi a misc")
        void banned_appliesToMisc() {
            ZCategory misc = new ZCategory("misc", "M", List.of(), List.of(new MaterialRule(Set.of(Material.BEDROCK))), true);

            assertTrue(misc.matches(RuleTestItems.context(Material.STONE)));
            assertFalse(misc.matches(RuleTestItems.context(Material.BEDROCK)));
        }

        @Test
        @DisplayName("banned-rules : court-circuit, les regles d'inclusion ne sont pas evaluees apres un ban")
        void banned_shortCircuitsRules() {
            AtomicInteger ruleCalls = new AtomicInteger();
            Rule counting = context -> {
                ruleCalls.incrementAndGet();
                return true;
            };
            ZCategory category = new ZCategory("c", "C", List.of(counting), List.of(ALWAYS_TRUE), false);

            assertFalse(category.matches(RuleTestItems.context(Material.STONE)));
            assertEquals(0, ruleCalls.get());
        }

        @Test
        @DisplayName("regles d'inclusion : court-circuit a la premiere qui matche")
        void rules_shortCircuitOnFirstMatch() {
            AtomicInteger calls = new AtomicInteger();
            Rule counting = context -> {
                calls.incrementAndGet();
                return true;
            };
            ZCategory category = new ZCategory("c", "C", List.of(ALWAYS_TRUE, counting), List.of(), false);

            assertTrue(category.matches(RuleTestItems.context(Material.STONE)));
            assertEquals(0, calls.get());
        }
    }

    @Nested
    @DisplayName("equals / hashCode / toString")
    class Identity {

        @Test
        @DisplayName("egalite par id uniquement, independamment du reste")
        void equals_byIdOnly() {
            ZCategory a = new ZCategory("weapons", "A", List.of(ALWAYS_TRUE), List.of(), false);
            ZCategory b = new ZCategory("weapons", "B", List.of(), List.of(ALWAYS_FALSE), true);
            ZCategory c = new ZCategory("armor", "A", List.of(ALWAYS_TRUE), List.of(), false);

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertNotEquals(a, c);
            assertEquals(a, a);
        }

        @Test
        @DisplayName("l'id est sensible a la casse pour equals")
        void equals_idCaseSensitive() {
            assertNotEquals(new ZCategory("Weapons", "x", null, null, false), new ZCategory("weapons", "x", null, null, false));
        }

        @Test
        @DisplayName("non egal a null ni a un autre type")
        void equals_nullAndOtherTypes() {
            ZCategory a = new ZCategory("weapons", "A", null, null, false);

            assertNotEquals(null, a);
            assertNotEquals("weapons", a);
        }

        @Test
        @DisplayName("utilisable dans un HashSet : doublons d'id dedupliques")
        void usableInHashSet() {
            Set<ZCategory> set = new HashSet<>();
            set.add(new ZCategory("weapons", "A", null, null, false));
            set.add(new ZCategory("weapons", "B", null, null, false));
            set.add(new ZCategory("armor", "C", null, null, false));

            assertEquals(2, set.size());
        }

        @Test
        @DisplayName("toString contient l'id, le nom, le nombre de regles et le drapeau misc")
        void toString_containsKeyFacts() {
            String text = new ZCategory("weapons", "Weapons", List.of(ALWAYS_TRUE, ALWAYS_FALSE), List.of(), false).toString();

            assertTrue(text.contains("weapons"));
            assertTrue(text.contains("Weapons"));
            assertTrue(text.contains("rulesCount=2"));
            assertTrue(text.contains("miscellaneous=false"));
        }
    }

    @Test
    @DisplayName("matches ne depend que du contexte passe, pas de l'ItemStack d'origine")
    void matches_usesContextOnly() {
        ItemRuleContext context = RuleTestItems.context(Material.DIAMOND_SWORD);
        ZCategory category = new ZCategory("w", "W", List.of(new MaterialSuffixRule(List.of("_SWORD"))), List.of(), false);

        assertTrue(category.matches(context));
    }
}
