package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.buttons.SortButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SortLoaderTest extends LoaderTestBase {

    private InventoryManager inventoryManager;
    private MenuItemStack loadingItem;
    private SortLoader loader;

    @BeforeEach
    void setUp() {
        this.inventoryManager = mock(InventoryManager.class);
        this.loadingItem = mock(MenuItemStack.class);
        when(this.inventoryManager.loadItemStack(any(YamlConfiguration.class), any(String.class), any())).thenReturn(this.loadingItem);
        this.loader = new SortLoader(this.plugin, this.inventoryManager);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_CHANGE_SORT")
    void name() {
        assertEquals("ZAUCTIONHOUSE_CHANGE_SORT", loader.getName());
    }

    @Test
    @DisplayName("auction.yml livre : 4 tris dans l'ordre, textes et item de chargement")
    void shippedFile() {
        YamlConfiguration configuration = TestResources.yaml("inventories/auction.yml");

        SortButton button = load(loader, configuration, "change-sort", SortButton.class);

        assertEquals(" #F27438➜ %sorting%", field(button, "enableText"));
        assertEquals(" #76CDCD➜ %sorting%", field(button, "disableText"));
        assertEquals(List.of(SortItem.DECREASING_DATE, SortItem.DECREASING_PRICE, SortItem.ASCENDING_DATE, SortItem.ASCENDING_PRICE), field(button, "sortItems"));
        assertSame(loadingItem, field(button, "loadingItemStack"));
        verify(inventoryManager).loadItemStack(eq(configuration), eq("items.change-sort.loading-item."), eq(defaultButtonValue.getFile()));
    }

    @Test
    @DisplayName("textes absents -> null (aucune valeur par defaut), tris absents -> liste vide")
    void missingValues() {
        SortButton button = loadInline(loader, "sort", "slot: 52", SortButton.class);

        assertNull(field(button, "enableText"));
        assertNull(field(button, "disableText"));
        assertTrue(((List<?>) field(button, "sortItems")).isEmpty());
    }

    @Test
    @DisplayName("un tri inconnu est ignore (journalise), les autres conserves")
    void invalidSort_skipped() {
        SortButton button = loadInline(loader, "sort", """
                sorts:
                  - ASCENDING_PRICE
                  - NOPE
                  - DECREASING_DATE
                """, SortButton.class);

        assertEquals(List.of(SortItem.ASCENDING_PRICE, SortItem.DECREASING_DATE), field(button, "sortItems"));
    }

    @Test
    @DisplayName("SortItem.valueOf est sensible a la casse : 'decreasing_date' est rejete")
    void lowercaseSort_rejected() {
        SortButton button = loadInline(loader, "sort", "sorts: [decreasing_date]", SortButton.class);

        assertTrue(((List<?>) field(button, "sortItems")).isEmpty());
    }

    @Test
    @DisplayName("la liste de tris du bouton est une copie immuable")
    void sortItems_immutableCopy() {
        SortButton button = loadInline(loader, "sort", "sorts: [DECREASING_DATE]", SortButton.class);

        List<SortItem> sortItems = field(button, "sortItems");
        assertThrows(UnsupportedOperationException.class, () -> sortItems.add(SortItem.ASCENDING_DATE));
    }
}
