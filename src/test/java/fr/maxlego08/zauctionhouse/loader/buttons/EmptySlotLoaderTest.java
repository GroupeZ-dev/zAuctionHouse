package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.zauctionhouse.buttons.categories.CategoryButton;
import fr.maxlego08.zauctionhouse.buttons.list.ExpiredItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.PurchasedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.SellingItemsButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EmptySlotLoaderTest extends LoaderTestBase {

    @Test
    @DisplayName("le nom passe au constructeur est le nom du loader")
    void name() {
        EmptySlotLoader loader = new EmptySlotLoader(plugin, ListedItemsButton.class, "ZAUCTIONHOUSE_LISTED_ITEMS");

        assertEquals("ZAUCTIONHOUSE_LISTED_ITEMS", loader.getName());
        assertSame(plugin, loader.getPlugin());
    }

    @Test
    @DisplayName("empty-slot est lu et passe au constructeur (Plugin, int)")
    void emptySlot_read() {
        EmptySlotLoader loader = new EmptySlotLoader(plugin, ListedItemsButton.class, "X");

        ListedItemsButton button = loadInline(loader, "items", "empty-slot: 31", ListedItemsButton.class);

        assertEquals(31, (int) field(button, "emptySlot"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("empty-slot absent -> 22")
    void emptySlot_default22() {
        EmptySlotLoader loader = new EmptySlotLoader(plugin, ExpiredItemsButton.class, "X");

        ExpiredItemsButton button = loadInline(loader, "items", "slot: 1", ExpiredItemsButton.class);

        assertEquals(22, (int) field(button, "emptySlot"));
    }

    @Test
    @DisplayName("les quatre boutons de liste du plugin sont instanciables par ce loader")
    void everyListButton_instantiable() {
        for (Class<? extends Button> type : java.util.List.of(ListedItemsButton.class, ExpiredItemsButton.class, SellingItemsButton.class, PurchasedItemsButton.class)) {
            EmptySlotLoader loader = new EmptySlotLoader(plugin, type, "X");

            Button button = load(loader, inline("items", "empty-slot: 13"), "items", type);

            assertEquals(13, (int) field(button, "emptySlot"), type.getSimpleName());
        }
    }

    @Test
    @DisplayName("le fichier auction.yml livre fournit un bouton de liste valide")
    void shippedAuctionFile() {
        var configuration = TestResources.yaml("inventories/auction.yml");
        var items = configuration.getConfigurationSection("items");
        String key = items.getKeys(false).stream().filter(k -> "ZAUCTIONHOUSE_LISTED_ITEMS".equals(items.getString(k + ".type"))).findFirst().orElseThrow();
        EmptySlotLoader loader = new EmptySlotLoader(plugin, ListedItemsButton.class, "ZAUCTIONHOUSE_LISTED_ITEMS");

        ListedItemsButton button = load(loader, configuration, key, ListedItemsButton.class);

        assertEquals(items.getInt(key + ".empty-slot", 22), (int) field(button, "emptySlot"));
    }

    @Test
    @DisplayName("classe sans constructeur (Plugin, int) ni constructeur par defaut -> null, erreur journalisee")
    void incompatibleClass_returnsNull() {
        EmptySlotLoader loader = new EmptySlotLoader(plugin, CategoryButton.class, "X");

        assertNull(loader.load(inline("items", "empty-slot: 1"), TestResources.buttonPath("items"), defaultButtonValue));
    }
}
