package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.zauctionhouse.buttons.inventory.ExpiredInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.PurchasedInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.SellingInventoryButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InventoryButtonLoaderTest extends LoaderTestBase {

    @Test
    @DisplayName("le nom passe au constructeur est le nom du loader")
    void name() {
        InventoryButtonLoader loader = new InventoryButtonLoader(plugin, "ZAUCTIONHOUSE_EXPIRED_INVENTORY", ExpiredInventoryButton::new);

        assertEquals("ZAUCTIONHOUSE_EXPIRED_INVENTORY", loader.getName());
    }

    @Test
    @DisplayName("la fabrique recoit le plugin et open-when-empty (false par defaut)")
    void factory_receivesPluginAndFlag() {
        List<Object> received = new ArrayList<>();
        Button produced = mock(Button.class);
        InventoryButtonLoader loader = new InventoryButtonLoader(plugin, "X", (Plugin owner, Boolean openWhenEmpty) -> {
            received.add(owner);
            received.add(openWhenEmpty);
            return produced;
        });

        Button button = loader.load(inline("b", "slot: 1"), "items.b.", defaultButtonValue);

        assertSame(produced, button);
        assertEquals(List.of(plugin, false), received);
    }

    @Test
    @DisplayName("open-when-empty: true est transmis")
    void openWhenEmpty_true() {
        InventoryButtonLoader loader = new InventoryButtonLoader(plugin, "X", ExpiredInventoryButton::new);

        ExpiredInventoryButton button = loadInline(loader, "b", "open-when-empty: true", ExpiredInventoryButton.class);

        assertTrue((boolean) field(button, "openWhenEmpty"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("les trois boutons de section du plugin sont construits avec le drapeau par defaut false")
    void everySectionButton() {
        InventoryButtonLoader expired = new InventoryButtonLoader(plugin, "A", ExpiredInventoryButton::new);
        InventoryButtonLoader selling = new InventoryButtonLoader(plugin, "B", SellingInventoryButton::new);
        InventoryButtonLoader purchased = new InventoryButtonLoader(plugin, "C", PurchasedInventoryButton::new);

        assertFalse((boolean) field(loadInline(expired, "b", "slot: 1", ExpiredInventoryButton.class), "openWhenEmpty"));
        assertFalse((boolean) field(loadInline(selling, "b", "slot: 1", SellingInventoryButton.class), "openWhenEmpty"));
        assertFalse((boolean) field(loadInline(purchased, "b", "slot: 1", PurchasedInventoryButton.class), "openWhenEmpty"));
    }
}
