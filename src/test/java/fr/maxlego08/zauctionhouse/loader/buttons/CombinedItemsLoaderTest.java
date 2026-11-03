package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.list.CombinedItemsButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinedItemsLoaderTest extends LoaderTestBase {

    private CombinedItemsLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new CombinedItemsLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_COMBINED_ITEMS")
    void name() {
        assertEquals("ZAUCTIONHOUSE_COMBINED_ITEMS", loader.getName());
    }

    @Test
    @DisplayName("combined-items.yml livre : slot vide 22 et les trois sections incluses")
    void shippedFile() {
        CombinedItemsButton button = load(loader, TestResources.yaml("inventories/combined-items.yml"), "items", CombinedItemsButton.class);

        assertEquals(22, (int) field(button, "emptySlot"));
        assertTrue((boolean) field(button, "includeSelling"));
        assertTrue((boolean) field(button, "includeExpired"));
        assertTrue((boolean) field(button, "includePurchased"));
    }

    @Test
    @DisplayName("valeurs par defaut : empty-slot -1, toutes les sections incluses")
    void defaults() {
        CombinedItemsButton button = loadInline(loader, "items", "type: ZAUCTIONHOUSE_COMBINED_ITEMS", CombinedItemsButton.class);

        assertEquals(-1, (int) field(button, "emptySlot"));
        assertTrue((boolean) field(button, "includeSelling"));
        assertTrue((boolean) field(button, "includeExpired"));
        assertTrue((boolean) field(button, "includePurchased"));
    }

    @Test
    @DisplayName("chaque section peut etre exclue individuellement")
    void exclusions() {
        CombinedItemsButton button = loadInline(loader, "items", """
                empty-slot: 4
                include-selling: false
                include-expired: true
                include-purchased: false
                """, CombinedItemsButton.class);

        assertEquals(4, (int) field(button, "emptySlot"));
        assertFalse((boolean) field(button, "includeSelling"));
        assertTrue((boolean) field(button, "includeExpired"));
        assertFalse((boolean) field(button, "includePurchased"));
    }
}
