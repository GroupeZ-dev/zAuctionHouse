package fr.maxlego08.zauctionhouse.search;

import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seule la migration de configuration ({@code prepareConfiguration}) est pure : le reste de la
 * classe exige le DialogManager de zMenu et l'API Dialog de Paper, indisponibles sans serveur.
 */
class SearchDialogTest {

    @Test
    @DisplayName("sans section multi-actions : les trois actions sont creees depuis les labels et la config est marquee modifiee")
    void missingMultiActions_createdFromLabels() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                search-label: "<aqua>Chercher"
                clear-label: "<yellow>Effacer"
                cancel-label: "<red>Annuler"
                """);

        boolean changed = SearchDialog.prepareConfiguration(configuration);

        assertTrue(changed);
        assertEquals("<aqua>Chercher", configuration.getString("multi-actions.search.label"));
        assertEquals("<yellow>Effacer", configuration.getString("multi-actions.clear.label"));
        assertEquals("<red>Annuler", configuration.getString("multi-actions.cancel.label"));
    }

    @Test
    @DisplayName("sans labels ni multi-actions : valeurs par defaut Search / Clear / Cancel")
    void missingEverything_defaults() {
        YamlConfiguration configuration = new YamlConfiguration();

        assertTrue(SearchDialog.prepareConfiguration(configuration));
        assertEquals("<aqua>Search", configuration.getString("multi-actions.search.label"));
        assertEquals("<yellow>Clear", configuration.getString("multi-actions.clear.label"));
        assertEquals("<red>Cancel", configuration.getString("multi-actions.cancel.label"));
    }

    @Test
    @DisplayName("section multi-actions vide (sans cle) : traitee comme absente")
    void emptyMultiActions_treatedAsMissing() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.createSection("multi-actions");

        assertTrue(SearchDialog.prepareConfiguration(configuration));
        assertEquals("<aqua>Search", configuration.getString("multi-actions.search.label"));
    }

    @Test
    @DisplayName("section multi-actions deja presente : rien n'est touche, pas de modification")
    void existingMultiActions_untouched() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                search-label: "<aqua>Chercher"
                multi-actions:
                  go:
                    label: "Go"
                """);

        assertFalse(SearchDialog.prepareConfiguration(configuration));
        assertEquals("Go", configuration.getString("multi-actions.go.label"));
        assertNull(configuration.getString("multi-actions.search.label"));
    }

    @Test
    @DisplayName("after_action (ancien nom) est copie vers after-action")
    void legacyAfterAction_migrated() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                multi-actions:
                  search:
                    label: "s"
                after_action: CLOSE
                """);

        assertTrue(SearchDialog.prepareConfiguration(configuration));
        assertEquals("CLOSE", configuration.getString("after-action"));
        // l'ancienne cle n'est pas supprimee
        assertEquals("CLOSE", configuration.getString("after_action"));
    }

    @Test
    @DisplayName("after-action deja present : after_action n'ecrase pas la nouvelle cle")
    void afterAction_notOverwritten() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                multi-actions:
                  search:
                    label: "s"
                after-action: NONE
                after_action: CLOSE
                """);

        assertFalse(SearchDialog.prepareConfiguration(configuration));
        assertEquals("NONE", configuration.getString("after-action"));
    }

    @Test
    @DisplayName("columns (ancien nom) est copie vers number-of-columns en entier")
    void legacyColumns_migrated() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                multi-actions:
                  search:
                    label: "s"
                columns: 2
                """);

        assertTrue(SearchDialog.prepareConfiguration(configuration));
        assertEquals(2, configuration.getInt("number-of-columns"));
    }

    @Test
    @DisplayName("number-of-columns deja present : columns ignore")
    void columns_notOverwritten() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                multi-actions:
                  search:
                    label: "s"
                number-of-columns: 3
                columns: 2
                """);

        assertFalse(SearchDialog.prepareConfiguration(configuration));
        assertEquals(3, configuration.getInt("number-of-columns"));
    }

    @Test
    @DisplayName("configuration deja moderne et complete : aucune modification")
    void modernConfiguration_unchanged() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                query-label: "Search query"
                multi-actions:
                  search:
                    label: "<aqua>Search"
                  clear:
                    label: "<yellow>Clear"
                  cancel:
                    label: "<red>Cancel"
                after-action: CLOSE
                number-of-columns: 3
                """);
        String before = configuration.saveToString();

        assertFalse(SearchDialog.prepareConfiguration(configuration));
        assertEquals(before, configuration.saveToString());
    }

    @Test
    @DisplayName("les trois migrations peuvent s'appliquer en une passe")
    void allMigrations_inOnePass() {
        YamlConfiguration configuration = TestResources.yamlOf("""
                after_action: CLOSE
                columns: 1
                """);

        assertTrue(SearchDialog.prepareConfiguration(configuration));
        assertEquals("<aqua>Search", configuration.getString("multi-actions.search.label"));
        assertEquals("CLOSE", configuration.getString("after-action"));
        assertEquals(1, configuration.getInt("number-of-columns"));
    }
}
