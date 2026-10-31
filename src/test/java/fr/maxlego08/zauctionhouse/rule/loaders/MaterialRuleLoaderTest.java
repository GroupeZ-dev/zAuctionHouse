package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialRule;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialRuleLoaderTest {

    private final MaterialRuleLoader loader = new MaterialRuleLoader();

    @Test
    @DisplayName("getType : 'material', sans alias")
    void getType_isMaterial() {
        assertEquals("material", loader.getType());
        assertTrue(loader.getAliases().isEmpty());
    }

    @Test
    @DisplayName("load : liste valide => MaterialRule qui matche exactement ces materiaux")
    void load_validMaterials_returnsMaterialRule() {
        Rule rule = loader.load(Map.of("type", "material", "materials", List.of("DIAMOND", "EMERALD")));

        assertInstanceOf(MaterialRule.class, rule);
        assertTrue(rule.isValid());
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertTrue(rule.matches(RuleTestItems.context(Material.EMERALD)));
        assertFalse(rule.matches(RuleTestItems.context(Material.GOLD_INGOT)));
    }

    @Test
    @DisplayName("load : les noms sont mis en majuscules avant Material.valueOf")
    void load_lowercaseNames_areUppercased() {
        Rule rule = loader.load(Map.of("materials", List.of("diamond", "Nether_Star")));

        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertTrue(rule.matches(RuleTestItems.context(Material.NETHER_STAR)));
    }

    @Test
    @DisplayName("load : les materiaux inconnus sont ignores silencieusement, les valides conserves")
    void load_unknownMaterials_areSkipped() {
        Rule rule = loader.load(Map.of("materials", List.of("NOT_A_MATERIAL", "DIAMOND")));

        assertInstanceOf(MaterialRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
    }

    @Test
    @DisplayName("load : null si tous les materiaux sont inconnus")
    void load_onlyUnknownMaterials_returnsNull() {
        assertNull(loader.load(Map.of("materials", List.of("NOT_A_MATERIAL", "ALSO_WRONG"))));
    }

    @Test
    @DisplayName("load : null si la cle est absente, vide, ou pas une liste")
    void load_missingOrInvalidKey_returnsNull() {
        assertNull(loader.load(Map.of("type", "material")));
        assertNull(loader.load(Map.of("materials", List.of())));
        assertNull(loader.load(Map.of("materials", "DIAMOND")));
        assertNull(loader.load(Map.of("materials", 42)));
    }

    @Test
    @DisplayName("load : les elements null de la liste sont ignores")
    void load_nullElements_areSkipped() {
        Rule rule = loader.load(Map.of("materials", Arrays.asList(null, "DIAMOND")));

        assertInstanceOf(MaterialRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
    }
}
