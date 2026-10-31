package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.categories.CategoryButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CategoryButtonLoaderTest extends LoaderTestBase {

    private CategoryButtonLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new CategoryButtonLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_CATEGORY, appartenant au plugin")
    void name() {
        assertEquals("ZAUCTIONHOUSE_CATEGORY", loader.getName());
        assertSame(plugin, loader.getPlugin());
    }

    @Test
    @DisplayName("categories.yml livre : le bouton 'blocks' porte la categorie blocks")
    void shippedFile_readsCategory() {
        CategoryButton button = load(loader, TestResources.yaml("inventories/categories.yml"), "blocks", CategoryButton.class);

        assertEquals("blocks", button.getCategoryId());
    }

    @Test
    @DisplayName("chaque bouton ZAUCTIONHOUSE_CATEGORY du fichier livre porte sa propre categorie")
    void shippedFile_everyCategoryButton() {
        var configuration = TestResources.yaml("inventories/categories.yml");
        var items = configuration.getConfigurationSection("items");

        for (String key : items.getKeys(false)) {
            if (!"ZAUCTIONHOUSE_CATEGORY".equals(items.getString(key + ".type"))) continue;
            CategoryButton button = load(loader, configuration, key, CategoryButton.class);
            assertEquals(items.getString(key + ".category"), button.getCategoryId(), key);
        }
    }

    @Test
    @DisplayName("'category' absent -> repli sur misc")
    void missingCategory_fallsBackToMisc() {
        CategoryButton button = loadInline(loader, "x", "type: ZAUCTIONHOUSE_CATEGORY", CategoryButton.class);

        assertEquals("misc", button.getCategoryId());
    }

    @Test
    @DisplayName("'category' vide -> repli sur misc")
    void emptyCategory_fallsBackToMisc() {
        CategoryButton button = loadInline(loader, "x", "category: \"\"", CategoryButton.class);

        assertEquals("misc", button.getCategoryId());
    }

    @Test
    @DisplayName("la categorie est prise telle quelle (casse conservee)")
    void category_keptAsIs() {
        CategoryButton button = loadInline(loader, "x", "category: Enchanted-Books", CategoryButton.class);

        assertEquals("Enchanted-Books", button.getCategoryId());
    }
}
