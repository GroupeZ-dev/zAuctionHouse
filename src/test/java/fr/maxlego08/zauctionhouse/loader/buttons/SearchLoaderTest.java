package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.SearchButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchLoaderTest extends LoaderTestBase {

    private SearchLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new SearchLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_SEARCH")
    void name() {
        assertEquals("ZAUCTIONHOUSE_SEARCH", loader.getName());
    }

    @Test
    @DisplayName("auction.yml livre : None / true / false, dialogue desactive")
    void shippedFile() {
        SearchButton button = load(loader, TestResources.yaml("inventories/auction.yml"), "search", SearchButton.class);

        assertEquals("None", field(button, "noneValue"));
        assertEquals("true", field(button, "activeValue"));
        assertEquals("false", field(button, "inactiveValue"));
        assertFalse((boolean) field(button, "dialogEnabled"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("valeurs par defaut identiques au fichier livre")
    void defaults() {
        SearchButton button = loadInline(loader, "search", "slot: 47", SearchButton.class);

        assertEquals("None", field(button, "noneValue"));
        assertEquals("true", field(button, "activeValue"));
        assertEquals("false", field(button, "inactiveValue"));
        assertFalse((boolean) field(button, "dialogEnabled"));
    }

    @Test
    @DisplayName("valeurs personnalisees et dialog.enabled: true")
    void custom() {
        SearchButton button = loadInline(loader, "search", """
                none-value: "Aucune"
                active-value: "oui"
                inactive-value: "non"
                dialog:
                  enabled: true
                """, SearchButton.class);

        assertEquals("Aucune", field(button, "noneValue"));
        assertEquals("oui", field(button, "activeValue"));
        assertEquals("non", field(button, "inactiveValue"));
        assertTrue((boolean) field(button, "dialogEnabled"));
    }

    @Test
    @DisplayName("la cle plate 'dialog-enabled' n'est PAS reconnue : seul dialog.enabled compte")
    void flatDialogKey_ignored() {
        SearchButton button = loadInline(loader, "search", "dialog-enabled: true", SearchButton.class);

        assertFalse((boolean) field(button, "dialogEnabled"));
    }
}
