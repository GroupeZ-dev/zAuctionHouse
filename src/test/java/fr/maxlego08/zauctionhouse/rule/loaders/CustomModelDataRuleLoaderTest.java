package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.AndRule;
import fr.maxlego08.zauctionhouse.rule.rules.ModelDataRangeRule;
import fr.maxlego08.zauctionhouse.rule.rules.ModelDataRule;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomModelDataRuleLoaderTest {

    private final CustomModelDataRuleLoader loader = new CustomModelDataRuleLoader();

    private static boolean matches(Rule rule, int modelData) {
        return rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, modelData)));
    }

    @Test
    @DisplayName("getType : 'custom-model-data'")
    void getType_isCustomModelData() {
        assertEquals("custom-model-data", loader.getType());
    }

    @Test
    @DisplayName("load : values seules => ModelDataRule exacte")
    void load_valuesOnly_returnsModelDataRule() {
        Rule rule = loader.load(Map.of("values", List.of(1001, 1002)));

        assertInstanceOf(ModelDataRule.class, rule);
        assertTrue(matches(rule, 1001));
        assertTrue(matches(rule, 1002));
        assertFalse(matches(rule, 1003));
    }

    @Test
    @DisplayName("load : values en chaines ou doubles sont converties en int ; les non-numeriques ignorees")
    void load_valuesMixedTypes_areCoerced() {
        Rule rule = loader.load(Map.of("values", List.of("1001", 1002.9, "abc")));

        assertInstanceOf(ModelDataRule.class, rule);
        assertTrue(matches(rule, 1001));
        assertTrue(matches(rule, 1002), "1002.9 est tronque en 1002");
        assertFalse(matches(rule, 1003));
    }

    @Test
    @DisplayName("load : une seule plage => ModelDataRangeRule, bornes incluses")
    void load_singleRange_returnsRangeRule() {
        Rule rule = loader.load(Map.of("ranges", List.of(Map.of("min", 100, "max", 200))));

        assertInstanceOf(ModelDataRangeRule.class, rule);
        assertTrue(matches(rule, 100));
        assertTrue(matches(rule, 200));
        assertFalse(matches(rule, 99));
        assertFalse(matches(rule, 201));
    }

    @Test
    @DisplayName("load : plage sans min => 0 ; sans max => Integer.MAX_VALUE")
    void load_rangeDefaults() {
        Rule noMin = loader.load(Map.of("ranges", List.of(Map.of("max", 10))));
        Rule noMax = loader.load(Map.of("ranges", List.of(Map.of("min", 10))));

        assertTrue(matches(noMin, 0));
        assertTrue(matches(noMin, 10));
        assertFalse(matches(noMin, 11));

        assertTrue(matches(noMax, 10));
        assertTrue(matches(noMax, Integer.MAX_VALUE));
        assertFalse(matches(noMax, 9));
    }

    @Test
    @DisplayName("load : plusieurs plages => AndRule (toutes doivent matcher : intersection)")
    void load_multipleRanges_returnsAndRule() {
        Rule rule = loader.load(Map.of("ranges", List.of(Map.of("min", 100, "max", 200), Map.of("min", 150, "max", 300))));

        assertInstanceOf(AndRule.class, rule);
        assertTrue(matches(rule, 150));
        assertTrue(matches(rule, 200));
        assertFalse(matches(rule, 120), "dans la 1re plage mais pas la 2e => AND echoue");
        assertFalse(matches(rule, 250), "dans la 2e plage mais pas la 1re => AND echoue");
    }

    @Test
    @DisplayName("load : plage + values => AndRule combinant les deux")
    void load_rangeAndValues_returnsAndRule() {
        Rule rule = loader.load(Map.of("ranges", List.of(Map.of("min", 1000, "max", 2000)), "values", List.of(1001, 5000)));

        assertInstanceOf(AndRule.class, rule);
        assertTrue(matches(rule, 1001), "dans la plage ET dans values");
        assertFalse(matches(rule, 5000), "dans values mais hors plage");
        assertFalse(matches(rule, 1500), "dans la plage mais pas dans values");
    }

    @Test
    @DisplayName("load : plage inversee produit une regle invalide (detectee par isValid)")
    void load_invertedRange_isInvalid() {
        Rule rule = loader.load(Map.of("ranges", List.of(Map.of("min", 200, "max", 100))));

        assertInstanceOf(ModelDataRangeRule.class, rule);
        assertFalse(rule.isValid());
    }

    @Test
    @DisplayName("load : null si ni values ni ranges exploitables")
    void load_nothingUsable_returnsNull() {
        assertNull(loader.load(Map.of()));
        assertNull(loader.load(Map.of("values", List.of())));
        assertNull(loader.load(Map.of("values", List.of("abc"))), "aucune valeur numerique");
        assertNull(loader.load(Map.of("ranges", List.of())));
        assertNull(loader.load(Map.of("ranges", List.of("not-a-map"))), "les elements non-Map sont ignores");
        assertNull(loader.load(Map.of("values", 1001)), "un scalaire n'est pas une liste");
    }

    @Test
    @DisplayName("load : un item sans custom model data ne matche jamais la regle chargee")
    void load_ruleRejectsItemWithoutModelData() {
        Rule rule = loader.load(Map.of("values", List.of(0)));

        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
    }
}
