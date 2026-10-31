package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.zauctionhouse.buttons.sell.SellConfirmButton;
import fr.maxlego08.zauctionhouse.buttons.sell.SellEconomyButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SellEconomyOptionLoaderTest extends LoaderTestBase {

    @Test
    @DisplayName("le nom passe au constructeur est le nom du loader")
    void name() {
        SellEconomyOptionLoader loader = new SellEconomyOptionLoader(plugin, "ZAUCTIONHOUSE_SELL_CONFIRM", SellConfirmButton::new);

        assertEquals("ZAUCTIONHOUSE_SELL_CONFIRM", loader.getName());
    }

    @Test
    @DisplayName("sans option 'economy' : la fabrique recoit null (economie choisie dans l'inventaire)")
    void noEconomy_null() {
        SellEconomyOptionLoader loader = new SellEconomyOptionLoader(plugin, "X", SellConfirmButton::new);

        SellConfirmButton button = loadInline(loader, "confirm", "slot: 24", SellConfirmButton.class);

        assertNull(field(button, "economyName"));
        assertSame(plugin, field(button, "plugin"));
        verify(plugin.getEconomyManager(), never()).getEconomy(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("economy vide ou blanche -> traitee comme absente")
    void blankEconomy_null() {
        SellEconomyOptionLoader loader = new SellEconomyOptionLoader(plugin, "X", SellEconomyButton::new);

        assertNull(field(loadInline(loader, "eco", "economy: \"\"", SellEconomyButton.class), "economyName"));
        assertNull(field(loadInline(loader, "eco", "economy: \"   \"", SellEconomyButton.class), "economyName"));
    }

    @Test
    @DisplayName("economie connue : transmise telle quelle a la fabrique")
    void knownEconomy_passed() {
        SellEconomyOptionLoader loader = new SellEconomyOptionLoader(plugin, "X", SellConfirmButton::new);

        SellConfirmButton button = loadInline(loader, "confirm", "economy: vault", SellConfirmButton.class);

        assertEquals("vault", field(button, "economyName"));
        verify(plugin.getEconomyManager()).getEconomy("vault");
    }

    @Test
    @DisplayName("economie inconnue : avertissement mais le nom est quand meme transmis (relue a chaque clic)")
    void unknownEconomy_stillPassed() {
        SellEconomyOptionLoader loader = new SellEconomyOptionLoader(plugin, "X", SellEconomyButton::new);

        SellEconomyButton button = loadInline(loader, "eco", "economy: typo_economy", SellEconomyButton.class);

        assertEquals("typo_economy", field(button, "economyName"));
    }

    @Test
    @DisplayName("la fabrique recoit exactement (plugin, nom) et son resultat est rendu")
    void factory_contract() {
        List<Object> received = new ArrayList<>();
        Button produced = mock(Button.class);
        SellEconomyOptionLoader loader = new SellEconomyOptionLoader(plugin, "X", (Plugin owner, String economy) -> {
            received.add(owner);
            received.add(economy);
            return produced;
        });

        Button button = loader.load(inline("b", "economy: level"), "items.b.", defaultButtonValue);

        assertSame(produced, button);
        assertEquals(List.of(plugin, "level"), received);
    }
}
