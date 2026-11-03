package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.rule.rules.AndRule;
import fr.maxlego08.zauctionhouse.rule.rules.LoreContainsRule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialRule;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Le registre est mocke : AndRuleLoader delegue chaque sous-regle a {@code registry.loadRule}.
 * Les sous-regles rendues sont de vraies regles pour verifier le comportement du AND resultant.
 */
class AndRuleLoaderTest {

    private RuleLoaderRegistry registry;
    private AndRuleLoader loader;

    private final Map<String, Object> materialConfig = Map.of("type", "material", "materials", List.of("PAPER"));
    private final Map<String, Object> loreConfig = Map.of("type", "lore", "values", List.of("Untradeable"));
    private final Map<String, Object> unknownConfig = Map.of("type", "unknown");

    @BeforeEach
    void setUp() {
        // Regles construites AVANT les when(...)
        Rule materialRule = new MaterialRule(java.util.Set.of(Material.PAPER));
        Rule loreRule = new LoreContainsRule(List.of("Untradeable"));

        registry = mock(RuleLoaderRegistry.class);
        lenient().when(registry.loadRule(materialConfig)).thenReturn(materialRule);
        lenient().when(registry.loadRule(loreConfig)).thenReturn(loreRule);
        lenient().when(registry.loadRule(unknownConfig)).thenReturn(null);
        loader = new AndRuleLoader(registry);
    }

    @Test
    @DisplayName("getType : 'and'")
    void getType_isAnd() {
        assertEquals("and", loader.getType());
    }

    @Test
    @DisplayName("load : deux sous-regles => AndRule exigeant les deux")
    void load_twoRules_returnsAndRule() {
        Rule rule = loader.load(Map.of("type", "and", "rules", List.of(materialConfig, loreConfig)));

        assertInstanceOf(AndRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.PAPER, "Untradeable"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.BOOK, "Untradeable"))));
        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
    }

    @Test
    @DisplayName("load : une seule sous-regle => la sous-regle elle-meme, pas un AndRule")
    void load_singleRule_returnsChildDirectly() {
        Rule rule = loader.load(Map.of("rules", List.of(materialConfig)));

        assertInstanceOf(MaterialRule.class, rule);
    }

    @Test
    @DisplayName("load : les sous-regles non resolues (null) sont ignorees")
    void load_unresolvedChildren_areSkipped() {
        Rule rule = loader.load(Map.of("rules", List.of(unknownConfig, materialConfig, loreConfig)));

        assertInstanceOf(AndRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withLore(Material.PAPER, "Untradeable"))));
    }

    @Test
    @DisplayName("load : null si toutes les sous-regles sont non resolues")
    void load_allUnresolved_returnsNull() {
        assertNull(loader.load(Map.of("rules", List.of(unknownConfig, unknownConfig))));
    }

    @Test
    @DisplayName("load : null si 'rules' absent, vide, ou pas une liste de maps")
    void load_missingRules_returnsNull() {
        assertNull(loader.load(Map.of()));
        assertNull(loader.load(Map.of("rules", List.of())));
        assertNull(loader.load(Map.of("rules", "material")));
        assertNull(loader.load(Map.of("rules", List.of("material", 42))), "elements non-Map ignores");
        verify(registry, never()).loadRule(any());
    }

    @Test
    @DisplayName("load : chaque sous-configuration est transmise telle quelle au registre")
    void load_delegatesEachChildToRegistry() {
        loader.load(Map.of("rules", List.of(materialConfig, loreConfig)));

        verify(registry).loadRule(materialConfig);
        verify(registry).loadRule(loreConfig);
    }
}
