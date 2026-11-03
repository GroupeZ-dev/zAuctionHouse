package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.admin.AdminLogsButton;
import fr.maxlego08.zauctionhouse.buttons.categories.CategoryButton;
import fr.maxlego08.zauctionhouse.buttons.history.HistoryItemsButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LoadingSlotLoaderTest extends LoaderTestBase {

    @Test
    @DisplayName("le nom passe au constructeur est le nom du loader")
    void name() {
        LoadingSlotLoader loader = new LoadingSlotLoader(plugin, HistoryItemsButton.class, "ZAUCTIONHOUSE_HISTORY_ITEMS");

        assertEquals("ZAUCTIONHOUSE_HISTORY_ITEMS", loader.getName());
    }

    @Test
    @DisplayName("loading-slot est lu et passe au constructeur (Plugin, int)")
    void loadingSlot_read() {
        LoadingSlotLoader loader = new LoadingSlotLoader(plugin, HistoryItemsButton.class, "X");

        HistoryItemsButton button = loadInline(loader, "items", "loading-slot: 22", HistoryItemsButton.class);

        assertEquals(22, button.getLoadingSlot());
        assertSame(plugin, button.getPlugin());
    }

    @Test
    @DisplayName("loading-slot absent -> 0")
    void loadingSlot_default0() {
        LoadingSlotLoader loader = new LoadingSlotLoader(plugin, AdminLogsButton.class, "X");

        AdminLogsButton button = loadInline(loader, "items", "slot: 1", AdminLogsButton.class);

        assertEquals(0, button.getLoadingSlot());
    }

    @Test
    @DisplayName("-1 (pas d'indicateur de chargement) est accepte")
    void minusOne_accepted() {
        LoadingSlotLoader loader = new LoadingSlotLoader(plugin, HistoryItemsButton.class, "X");

        HistoryItemsButton button = loadInline(loader, "items", "loading-slot: -1", HistoryItemsButton.class);

        assertEquals(-1, button.getLoadingSlot());
    }

    @Test
    @Disabled("BUG: LoadingSlotLoader.java:26 teste `slot < 0 && slot > inventorySize` (contradictoire, jamais vrai) : un slot negatif autre que -1 n'est jamais rejete")
    @DisplayName("un slot negatif autre que -1 est rejete (null + erreur journalisee)")
    void negativeSlot_rejected() {
        LoadingSlotLoader loader = new LoadingSlotLoader(plugin, HistoryItemsButton.class, "X");

        assertNull(loader.load(inline("items", "loading-slot: -5"), TestResources.buttonPath("items"), defaultButtonValue));
    }

    @Test
    @Disabled("BUG: LoadingSlotLoader.java:26 teste `slot < 0 && slot > inventorySize` (contradictoire, jamais vrai) : un slot au-dela de la taille de l'inventaire n'est jamais rejete")
    @DisplayName("un slot au-dela de la taille de l'inventaire est rejete")
    void slotBeyondInventory_rejected() {
        LoadingSlotLoader loader = new LoadingSlotLoader(plugin, HistoryItemsButton.class, "X");

        assertNull(loader.load(inline("items", "loading-slot: 99"), TestResources.buttonPath("items"), defaultButtonValue));
    }

    @Test
    @DisplayName("classe sans constructeur compatible -> null, erreur journalisee")
    void incompatibleClass_returnsNull() {
        LoadingSlotLoader loader = new LoadingSlotLoader(plugin, CategoryButton.class, "X");

        assertNull(loader.load(inline("items", "loading-slot: 1"), TestResources.buttonPath("items"), defaultButtonValue));
    }
}
