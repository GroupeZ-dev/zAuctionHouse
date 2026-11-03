package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.buttons.option.OptionToggleButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OptionToggleLoaderTest extends LoaderTestBase {

    private OptionToggleLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new OptionToggleLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_OPTION_TOGGLE")
    void name() {
        assertEquals("ZAUCTIONHOUSE_OPTION_TOGGLE", loader.getName());
    }

    @Test
    @DisplayName("options.yml livre : broadcast-sell -> BROADCAST_SELL avec ses textes")
    void shippedFile() {
        OptionToggleButton button = load(loader, TestResources.yaml("inventories/options.yml"), "broadcast-sell", OptionToggleButton.class);

        assertSame(PlayerOption.BROADCAST_SELL, field(button, "option"));
        assertEquals("<green>Enabled", field(button, "enableText"));
        assertEquals("<red>Disabled", field(button, "disableText"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("broadcast_purchase, insensible a la casse")
    void broadcastPurchase_caseInsensitive() {
        OptionToggleButton button = loadInline(loader, "b", "option-name: Broadcast_Purchase", OptionToggleButton.class);

        assertSame(PlayerOption.BROADCAST_PURCHASE, field(button, "option"));
    }

    @Test
    @DisplayName("valeurs par defaut : BROADCAST_SELL, <green>Enabled / <red>Disabled")
    void defaults() {
        OptionToggleButton button = loadInline(loader, "b", "slot: 1", OptionToggleButton.class);

        assertSame(PlayerOption.BROADCAST_SELL, field(button, "option"));
        assertEquals("<green>Enabled", field(button, "enableText"));
        assertEquals("<red>Disabled", field(button, "disableText"));
    }

    @Test
    @DisplayName("option inconnue -> repli sur BROADCAST_SELL (avertissement)")
    void unknownOption_fallsBack() {
        OptionToggleButton button = loadInline(loader, "b", """
                option-name: does_not_exist
                enable-text: "on"
                disable-text: "off"
                """, OptionToggleButton.class);
        // Les valeurs sont quotees : en YAML 1.1 (SnakeYAML), on/off non quotes sont des booleens
        // et MemorySection.getString renverrait "true"/"false".

        assertSame(PlayerOption.BROADCAST_SELL, field(button, "option"));
        assertEquals("on", field(button, "enableText"));
        assertEquals("off", field(button, "disableText"));
    }
}
