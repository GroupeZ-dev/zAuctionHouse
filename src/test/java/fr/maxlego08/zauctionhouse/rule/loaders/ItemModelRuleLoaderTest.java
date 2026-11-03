package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.ItemModelRule;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelRuleLoaderTest {

    private final ItemModelRuleLoader loader = new ItemModelRuleLoader();

    private static boolean matches(Rule rule, String namespace, String key) {
        return rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.PAPER, new NamespacedKey(namespace, key))));
    }

    @Test
    @DisplayName("getType : 'item-model'")
    void getType_isItemModel() {
        assertEquals("item-model", loader.getType());
    }

    @Test
    @DisplayName("load : liste 'models' => ItemModelRule")
    void load_modelsList_returnsRule() {
        Rule rule = loader.load(Map.of("models", List.of("ns:a", "ns:b")));

        assertInstanceOf(ItemModelRule.class, rule);
        assertTrue(matches(rule, "ns", "a"));
        assertTrue(matches(rule, "ns", "b"));
        assertFalse(matches(rule, "ns", "c"));
    }

    @Test
    @DisplayName("load : cle singuliere 'model' acceptee en secours")
    void load_singleModelKey_returnsRule() {
        Rule rule = loader.load(Map.of("model", "ns:only"));

        assertInstanceOf(ItemModelRule.class, rule);
        assertTrue(matches(rule, "ns", "only"));
        assertFalse(matches(rule, "ns", "other"));
    }

    @Test
    @DisplayName("load : 'models' non vide a priorite sur 'model'")
    void load_modelsTakesPrecedenceOverModel() {
        Rule rule = loader.load(Map.of("models", List.of("ns:list"), "model", "ns:single"));

        assertTrue(matches(rule, "ns", "list"));
        assertFalse(matches(rule, "ns", "single"));
    }

    @Test
    @DisplayName("load : 'models' vide retombe sur 'model'")
    void load_emptyModelsFallsBackToModel() {
        Rule rule = loader.load(Map.of("models", List.of(), "model", "ns:single"));

        assertInstanceOf(ItemModelRule.class, rule);
        assertTrue(matches(rule, "ns", "single"));
    }

    @Test
    @DisplayName("load : jokers conserves")
    void load_wildcards_preserved() {
        Rule rule = loader.load(Map.of("models", List.of("ns:*")));

        assertTrue(matches(rule, "ns", "anything"));
        assertFalse(matches(rule, "other", "anything"));
    }

    @Test
    @DisplayName("load : null si aucune des deux cles n'est exploitable")
    void load_noModel_returnsNull() {
        assertNull(loader.load(Map.of()));
        assertNull(loader.load(Map.of("models", List.of())));
        assertNull(loader.load(Map.of("models", "ns:a")), "'models' doit etre une liste ; un scalaire est ignore et 'model' est absent");
    }
}
