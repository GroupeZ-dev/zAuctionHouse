package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.categories.CategorySwitcherButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategorySwitcherLoaderTest extends LoaderTestBase {

    private CategorySwitcherLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new CategorySwitcherLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_CATEGORY_SWITCHER")
    void name() {
        assertEquals("ZAUCTIONHOUSE_CATEGORY_SWITCHER", loader.getName());
    }

    @Test
    @DisplayName("textes et liste de categories lus dans l'ordre")
    void fullConfiguration() {
        CategorySwitcherButton button = loadInline(loader, "switch", """
                enable-text: "<green>%category%"
                disable-text: "<gray>%category%"
                categories:
                  - all
                  - weapons
                  - tools
                """, CategorySwitcherButton.class);

        assertEquals("<green>%category%", field(button, "enableText"));
        assertEquals("<gray>%category%", field(button, "disableText"));
        assertEquals(List.of("all", "weapons", "tools"), field(button, "categories"));
    }

    @Test
    @DisplayName("textes absents -> &a%category% / &7%category%")
    void defaultTexts() {
        CategorySwitcherButton button = loadInline(loader, "switch", "categories: [a]", CategorySwitcherButton.class);

        assertEquals("&a%category%", field(button, "enableText"));
        assertEquals("&7%category%", field(button, "disableText"));
    }

    @Test
    @DisplayName("sans categories : le bouton est quand meme construit avec une liste vide (avertissement)")
    void noCategories_stillBuilt() {
        CategorySwitcherButton button = loadInline(loader, "switch", "enable-text: x", CategorySwitcherButton.class);

        assertTrue(((List<?>) field(button, "categories")).isEmpty());
    }
}
