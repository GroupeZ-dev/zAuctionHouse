package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.sell.SellPriceButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SellPriceButtonLoaderTest extends LoaderTestBase {

    private SellPriceButtonLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new SellPriceButtonLoader(this.plugin);
    }

    private static void assertAmount(String expected, Object actual) {
        BigDecimal value = (BigDecimal) actual;
        assertEquals(0, new BigDecimal(expected).compareTo(value), "expected " + expected + " but was " + value);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_SELL_PRICE")
    void name() {
        assertEquals("ZAUCTIONHOUSE_SELL_PRICE", loader.getName());
    }

    @Test
    @DisplayName("sell-inventory.yml livre : price-increase = +500 / +100 / +1000 / +2500")
    void shippedFile_increase() {
        SellPriceButton button = load(loader, TestResources.yaml("inventories/sell-inventory.yml"), "price-increase", SellPriceButton.class);

        assertAmount("500", field(button, "leftClickAmount"));
        assertAmount("100", field(button, "rightClickAmount"));
        assertAmount("1000", field(button, "shiftLeftClickAmount"));
        assertAmount("2500", field(button, "shiftRightClickAmount"));
    }

    @Test
    @DisplayName("sell-inventory.yml livre : private-decrease = -500 / -100 / -1000 / -2500")
    void shippedFile_decrease() {
        SellPriceButton button = load(loader, TestResources.yaml("inventories/sell-inventory.yml"), "private-decrease", SellPriceButton.class);

        assertAmount("-500", field(button, "leftClickAmount"));
        assertAmount("-100", field(button, "rightClickAmount"));
        assertAmount("-1000", field(button, "shiftLeftClickAmount"));
        assertAmount("-2500", field(button, "shiftRightClickAmount"));
    }

    @Test
    @DisplayName("sans section amounts : 100 / -100 / 1000 / -1000")
    void defaults() {
        SellPriceButton button = loadInline(loader, "price", "slot: 14", SellPriceButton.class);

        assertAmount("100", field(button, "leftClickAmount"));
        assertAmount("-100", field(button, "rightClickAmount"));
        assertAmount("1000", field(button, "shiftLeftClickAmount"));
        assertAmount("-1000", field(button, "shiftRightClickAmount"));
    }

    @Test
    @DisplayName("montants decimaux et chaines numeriques acceptes")
    void decimalsAndStrings() {
        SellPriceButton button = loadInline(loader, "price", """
                amounts:
                  left-click: 2.5
                  right-click: "1e3"
                  shift-left-click: "-0.01"
                """, SellPriceButton.class);

        assertAmount("2.5", field(button, "leftClickAmount"));
        assertAmount("1000", field(button, "rightClickAmount"));
        assertAmount("-0.01", field(button, "shiftLeftClickAmount"));
        assertAmount("-1000", field(button, "shiftRightClickAmount"));
    }

    @Test
    @DisplayName("valeur non numerique -> valeur par defaut de CE clic seulement (avertissement)")
    void invalidNumber_fallsBackPerKey() {
        SellPriceButton button = loadInline(loader, "price", """
                amounts:
                  left-click: abc
                  right-click: 7
                """, SellPriceButton.class);

        assertAmount("100", field(button, "leftClickAmount"));
        assertAmount("7", field(button, "rightClickAmount"));
    }
}
