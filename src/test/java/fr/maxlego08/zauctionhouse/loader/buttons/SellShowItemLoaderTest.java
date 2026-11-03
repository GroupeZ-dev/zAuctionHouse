package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.sell.SellShowItemButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SellShowItemLoaderTest extends LoaderTestBase {

    private SellShowItemLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new SellShowItemLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_SELL_SHOW_ITEM")
    void name() {
        assertEquals("ZAUCTIONHOUSE_SELL_SHOW_ITEM", loader.getName());
    }

    @Test
    @DisplayName("empty-slot lu")
    void emptySlot_read() {
        SellShowItemButton button = loadInline(loader, "show", "empty-slot: 13", SellShowItemButton.class);

        assertEquals(13, (int) field(button, "emptySlot"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("empty-slot absent -> 0")
    void emptySlot_default0() {
        SellShowItemButton button = loadInline(loader, "show", "slot: 13", SellShowItemButton.class);

        assertEquals(0, (int) field(button, "emptySlot"));
    }

    @Test
    @DisplayName("sell-inventory.yml livre : le bouton SELL_SHOW_ITEM se charge")
    void shippedFile() {
        var configuration = TestResources.yaml("inventories/sell-inventory.yml");
        var items = configuration.getConfigurationSection("items");
        String key = items.getKeys(false).stream().filter(k -> "ZAUCTIONHOUSE_SELL_SHOW_ITEM".equals(items.getString(k + ".type"))).findFirst().orElseThrow();

        SellShowItemButton button = load(loader, configuration, key, SellShowItemButton.class);

        assertEquals(items.getInt(key + ".empty-slot", 0), (int) field(button, "emptySlot"));
    }
}
