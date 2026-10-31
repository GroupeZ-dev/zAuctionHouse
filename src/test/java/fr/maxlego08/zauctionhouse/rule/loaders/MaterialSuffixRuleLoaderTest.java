package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialSuffixRule;
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

class MaterialSuffixRuleLoaderTest {

    private final MaterialSuffixRuleLoader loader = new MaterialSuffixRuleLoader();

    @Test
    @DisplayName("getType : 'material-suffix'")
    void getType_isMaterialSuffix() {
        assertEquals("material-suffix", loader.getType());
    }

    @Test
    @DisplayName("load : liste valide => MaterialSuffixRule operationnelle")
    void load_validSuffixes_returnsRule() {
        Rule rule = loader.load(Map.of("type", "material-suffix", "suffixes", List.of("_SWORD", "_AXE")));

        assertInstanceOf(MaterialSuffixRule.class, rule);
        assertTrue(rule.isValid());
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
        assertTrue(rule.matches(RuleTestItems.context(Material.IRON_AXE)));
        assertFalse(rule.matches(RuleTestItems.context(Material.IRON_HELMET)));
    }

    @Test
    @DisplayName("load : les valeurs non-String sont converties en chaine")
    void load_nonStringValues_areStringified() {
        // "1" n'est suffixe d'aucun materiau courant mais ne doit pas faire echouer le chargement
        Rule rule = loader.load(Map.of("suffixes", List.of(1, "_SWORD")));

        assertInstanceOf(MaterialSuffixRule.class, rule);
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND_SWORD)));
    }

    @Test
    @DisplayName("load : null si cle absente, vide, ou mal typee")
    void load_missingOrInvalidKey_returnsNull() {
        assertNull(loader.load(Map.of()));
        assertNull(loader.load(Map.of("suffixes", List.of())));
        assertNull(loader.load(Map.of("suffixes", "_SWORD")));
        assertNull(loader.load(Map.of("suffix", List.of("_SWORD"))), "la cle est 'suffixes', pas 'suffix'");
    }
}
