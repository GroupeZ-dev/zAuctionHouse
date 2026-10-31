package fr.maxlego08.zauctionhouse.placeholder;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Expansion PlaceholderAPI : pure delegation vers le {@link Placeholder} local et le descripteur
 * du plugin. Le constructeur de PlaceholderExpansion n'appelle pas Bukkit.
 */
class DistantPlaceholderTest {

    AuctionPlugin plugin;
    PluginDescriptionFile description;
    Placeholder placeholder;
    Player player;

    @BeforeEach
    void setUp() {
        description = mock(PluginDescriptionFile.class);
        plugin = mock(AuctionPlugin.class);
        when(plugin.getDescription()).thenReturn(description);
        placeholder = mock(Placeholder.class);
        when(placeholder.getPrefix()).thenReturn("zauctionhouse");
        player = mock(Player.class);
    }

    @Test
    @DisplayName("getIdentifier est le prefixe du placeholder local")
    void getIdentifier_isPrefix() {
        assertEquals("zauctionhouse", new DistantPlaceholder(plugin, placeholder).getIdentifier());
    }

    @Test
    @DisplayName("getAuthor rend le premier auteur du plugin.yml, ou 'Unknown' sans auteur")
    void getAuthor_firstOrUnknown() {
        when(description.getAuthors()).thenReturn(List.of("Maxlego08", "Someone"));
        assertEquals("Maxlego08", new DistantPlaceholder(plugin, placeholder).getAuthor());

        when(description.getAuthors()).thenReturn(List.of());
        assertEquals("Unknown", new DistantPlaceholder(plugin, placeholder).getAuthor());
    }

    @Test
    @DisplayName("getVersion lit la version du descripteur")
    void getVersion_fromDescription() {
        when(description.getVersion()).thenReturn("4.0.1.6");
        assertEquals("4.0.1.6", new DistantPlaceholder(plugin, placeholder).getVersion());
    }

    @Test
    @DisplayName("persist est vrai : l'expansion survit a /papi reload")
    void persist_isTrue() {
        assertTrue(new DistantPlaceholder(plugin, placeholder).persist());
    }

    @Test
    @DisplayName("onPlaceholderRequest delegue au placeholder local avec le joueur et les parametres bruts")
    void onPlaceholderRequest_delegates() {
        when(placeholder.onRequest(player, "listed_items")).thenReturn("42");
        DistantPlaceholder distant = new DistantPlaceholder(plugin, placeholder);

        assertEquals("42", distant.onPlaceholderRequest(player, "listed_items"));
        assertNull(distant.onPlaceholderRequest(player, "unknown"));
        verify(placeholder).onRequest(player, "listed_items");
        verify(placeholder).onRequest(player, "unknown");
    }

    @Test
    @DisplayName("un joueur nul est transmis tel quel (le placeholder local rend alors null)")
    void onPlaceholderRequest_nullPlayer_passedThrough() {
        DistantPlaceholder distant = new DistantPlaceholder(plugin, placeholder);
        assertNull(distant.onPlaceholderRequest(null, "listed_items"));
        verify(placeholder).onRequest(null, "listed_items");
    }
}
