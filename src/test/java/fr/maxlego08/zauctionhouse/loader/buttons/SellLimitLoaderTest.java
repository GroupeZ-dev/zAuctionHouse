package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.buttons.SellLimitButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SellLimitLoaderTest extends LoaderTestBase {

    private SellLimitLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new SellLimitLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_SELL_LIMIT")
    void name() {
        assertEquals("ZAUCTIONHOUSE_SELL_LIMIT", loader.getName());
    }

    @Test
    @DisplayName("types lus dans l'ordre, casse tolerante")
    void types_read() {
        SellLimitButton button = loadInline(loader, "limit", """
                types:
                  - bid
                  - AUCTION
                  - Rent
                """, SellLimitButton.class);

        assertEquals(List.of(ItemType.BID, ItemType.AUCTION, ItemType.RENT), field(button, "itemTypes"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("sans types -> [AUCTION]")
    void noTypes_defaultsToAuction() {
        SellLimitButton button = loadInline(loader, "limit", "slot: 4", SellLimitButton.class);

        assertEquals(List.of(ItemType.AUCTION), field(button, "itemTypes"));
    }

    @Test
    @DisplayName("type invalide ignore ; uniquement des invalides -> [AUCTION]")
    void invalidTypes() {
        assertEquals(List.of(ItemType.RENT), field(loadInline(loader, "limit", "types: [nope, rent]", SellLimitButton.class), "itemTypes"));
        assertEquals(List.of(ItemType.AUCTION), field(loadInline(loader, "limit", "types: [nope]", SellLimitButton.class), "itemTypes"));
    }

    @Test
    @DisplayName("les doublons sont conserves tels quels")
    void duplicates_kept() {
        SellLimitButton button = loadInline(loader, "limit", "types: [AUCTION, AUCTION]", SellLimitButton.class);

        assertEquals(List.of(ItemType.AUCTION, ItemType.AUCTION), field(button, "itemTypes"));
    }
}
