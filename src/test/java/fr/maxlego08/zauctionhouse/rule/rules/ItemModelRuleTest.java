package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelRuleTest {

    private static NamespacedKey key(String namespace, String key) {
        return new NamespacedKey(namespace, key);
    }

    @Test
    @DisplayName("matches : modele exact 'namespace:id'")
    void matches_exactModel_returnsTrue() {
        ItemModelRule rule = new ItemModelRule(List.of("myplugin:ruby_sword"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("myplugin", "ruby_sword")))));
    }

    @Test
    @DisplayName("matches : la comparaison exacte est insensible a la casse de la config")
    void matches_exactModel_caseInsensitiveConfig() {
        ItemModelRule rule = new ItemModelRule(List.of("MyPlugin:Ruby_Sword"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("myplugin", "ruby_sword")))));
    }

    @Test
    @DisplayName("matches : un autre modele du meme namespace ne matche pas en mode exact")
    void matches_otherModel_returnsFalse() {
        ItemModelRule rule = new ItemModelRule(List.of("myplugin:ruby_sword"));

        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("myplugin", "ruby_axe")))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("other", "ruby_sword")))));
    }

    @Test
    @DisplayName("matches : joker 'namespace:*' accepte tout le namespace")
    void matches_namespaceWildcard() {
        ItemModelRule rule = new ItemModelRule(List.of("myplugin:*"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("myplugin", "ruby_sword")))));
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("myplugin", "anything")))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("other", "ruby_sword")))));
    }

    @Test
    @DisplayName("matches : joker au milieu 'ns:rare_*_sword'")
    void matches_middleWildcard() {
        ItemModelRule rule = new ItemModelRule(List.of("ns:rare_*_sword"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("ns", "rare_ruby_sword")))));
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("ns", "rare__sword")))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("ns", "rare_ruby_axe")))));
    }

    @Test
    @DisplayName("matches : le point du modele est litteral, pas un joker regex")
    void matches_dotIsLiteral() {
        ItemModelRule rule = new ItemModelRule(List.of("ns:item.v1*"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("ns", "item.v1_a")))));
        // Sans le point echappe, "itemxv1_a" matcherait
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.DIAMOND_SWORD, key("ns", "itemxv1_a")))));
    }

    @Test
    @DisplayName("matches : melange exact + joker, OU logique")
    void matches_mixedExactAndWildcard() {
        ItemModelRule rule = new ItemModelRule(List.of("a:exact", "b:*"));

        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.PAPER, key("a", "exact")))));
        assertTrue(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.PAPER, key("b", "whatever")))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withItemModel(Material.PAPER, key("a", "other")))));
    }

    @Test
    @DisplayName("matches : faux sans meta ou avec une meta sans item model")
    void matches_noItemModel_returnsFalse() {
        ItemModelRule rule = new ItemModelRule(List.of("ns:*"));

        assertFalse(rule.matches(RuleTestItems.context(Material.PAPER)));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.named(Material.PAPER, "named but no model"))));
        assertFalse(rule.matches(RuleTestItems.context(RuleTestItems.withModelData(Material.PAPER, 7))));
    }

    @Test
    @DisplayName("isValid : vrai avec au moins un modele exact OU un joker")
    void isValid_dependsOnEmptiness() {
        assertTrue(new ItemModelRule(List.of("a:b")).isValid());
        assertTrue(new ItemModelRule(List.of("a:*")).isValid());
        assertFalse(new ItemModelRule(List.of()).isValid());
    }
}
