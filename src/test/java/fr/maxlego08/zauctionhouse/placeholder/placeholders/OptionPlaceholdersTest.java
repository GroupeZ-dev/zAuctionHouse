package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.services.AuctionOptionService;
import fr.maxlego08.zauctionhouse.placeholder.LocalPlaceholder;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OptionPlaceholdersTest {

    AuctionPlugin plugin;
    AuctionOptionService optionService;
    Player player;
    LocalPlaceholder placeholder;

    @BeforeEach
    void setUp() {
        optionService = mock(AuctionOptionService.class);
        AuctionManager manager = mock(AuctionManager.class);
        when(manager.getOptionService()).thenReturn(optionService);
        plugin = mock(AuctionPlugin.class);
        when(plugin.getAuctionManager()).thenReturn(manager);
        player = MockPlugins.player("Alice");

        placeholder = new LocalPlaceholder(plugin);
        new OptionPlaceholders().register(placeholder, plugin);
    }

    String resolve(String name) {
        return placeholder.setPlaceholders(player, "%zauctionhouse_" + name + "%");
    }

    @Test
    @DisplayName("option_<cle> rend la valeur du service pour l'option connue (cle insensible a la casse)")
    void option_knownKey_returnsServiceValue() {
        when(optionService.getOption(player, PlayerOption.BROADCAST_SELL)).thenReturn(true);
        when(optionService.getOption(player, PlayerOption.BROADCAST_PURCHASE)).thenReturn(false);

        assertEquals("true", resolve("option_broadcast_sell"));
        assertEquals("false", resolve("option_broadcast_purchase"));
        assertEquals("true", resolve("option_BROADCAST_SELL"));
    }

    @Test
    @DisplayName("option_<cle> rend false pour une option inconnue sans interroger le service")
    void option_unknownKey_returnsFalse() {
        assertEquals("false", resolve("option_unknown"));
        verify(optionService, never()).getOption(any(Player.class), any());
    }

    @Test
    @DisplayName("option_ sans cle rend false")
    void option_emptyKey_returnsFalse() {
        assertEquals("false", resolve("option_"));
        verify(optionService, never()).getOption(any(Player.class), any());
    }

    @Test
    @DisplayName("un seul placeholder est enregistre, documente avec son argument")
    void register_declaresOnePlaceholder() {
        assertEquals(1, placeholder.getAutoPlaceholders().size());
        assertEquals("option_", placeholder.getAutoPlaceholders().getFirst().getStartWith());
        assertEquals("<option_key>", placeholder.getAutoPlaceholders().getFirst().getArgs().getFirst());
    }
}
