package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/**
 * Le cache de {@code PapiHelper} est STATIQUE et partage : chaque test utilise un joueur
 * (UUID) distinct pour ne jamais lire l'entree d'un autre test.
 */
class PlaceholderUtilsTest {

    private final PlaceholderUtils utils = new PlaceholderUtils() {
    };

    private static Player player(String name) {
        return MockPlugins.player(UUID.randomUUID(), name);
    }

    @Test
    @DisplayName("papi(null) rend null et papi sans joueur rend le texte inchange, sans appeler PlaceholderAPI")
    void papi_nullInputs() {
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            assertNull(PlaceholderUtils.PapiHelper.papi((String) null, player("A")));
            assertEquals("%zah_x%", PlaceholderUtils.PapiHelper.papi("%zah_x%", null));
            api.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("un texte sans % est rendu tel quel sans appeler PlaceholderAPI")
    void papi_noPercent_shortCircuits() {
        Player player = player("B");
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            String text = "plain text";
            assertSame(text, PlaceholderUtils.PapiHelper.papi(text, player));
            api.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("papi delegue a PlaceholderAPI puis remplace %player% par le nom du joueur")
    void papi_delegatesAndReplacesPlayer() {
        Player player = player("Steve");
        String text = "%zah_count% items for %player%";
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            api.when(() -> PlaceholderAPI.setPlaceholders(player, text)).thenReturn("3 items for %player%");

            assertEquals("3 items for Steve", PlaceholderUtils.PapiHelper.papi(text, player));
        }
    }

    @Test
    @DisplayName("deux appels rapproches pour le meme texte et le meme joueur ne sollicitent PlaceholderAPI qu'une fois")
    void papi_cachesWithinWindow() {
        Player player = player("Cached");
        String text = "%zah_cached%";
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            api.when(() -> PlaceholderAPI.setPlaceholders(player, text)).thenReturn("value");

            assertEquals("value", PlaceholderUtils.PapiHelper.papi(text, player));
            assertEquals("value", PlaceholderUtils.PapiHelper.papi(text, player));

            api.verify(() -> PlaceholderAPI.setPlaceholders(player, text), times(1));
        }
    }

    @Test
    @DisplayName("le cache est par joueur : un autre joueur declenche un nouvel appel")
    void papi_cacheIsPerPlayer() {
        Player first = player("One");
        Player second = player("Two");
        String text = "%zah_per_player%";
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            api.when(() -> PlaceholderAPI.setPlaceholders(first, text)).thenReturn("1");
            api.when(() -> PlaceholderAPI.setPlaceholders(second, text)).thenReturn("2");

            assertEquals("1", PlaceholderUtils.PapiHelper.papi(text, first));
            assertEquals("2", PlaceholderUtils.PapiHelper.papi(text, second));
        }
    }

    @Test
    @DisplayName("papi(List) applique le traitement a chaque ligne et rend la liste intacte sans joueur")
    void papiList() {
        Player player = player("ListPlayer");
        List<String> lines = List.of("%zah_a%", "static", "%player%");
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            api.when(() -> PlaceholderAPI.setPlaceholders(player, "%zah_a%")).thenReturn("A");
            api.when(() -> PlaceholderAPI.setPlaceholders(player, "%player%")).thenReturn("%player%");

            assertEquals(List.of("A", "static", "ListPlayer"), PlaceholderUtils.PapiHelper.papi(lines, player));
            assertSame(lines, PlaceholderUtils.PapiHelper.papi(lines, null));
        }
    }

    @Test
    @DisplayName("papi(String, CommandSender) ne resout les placeholders que pour un joueur")
    void papi_commandSender() {
        Player player = player("SenderPlayer");
        CommandSender console = mock(CommandSender.class);
        String text = "%zah_sender%";
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            api.when(() -> PlaceholderAPI.setPlaceholders(player, text)).thenReturn("resolved");

            assertEquals("resolved", utils.papi(text, player));
            assertSame(text, utils.papi(text, console));
            api.verify(() -> PlaceholderAPI.setPlaceholders(player, text), times(1));
        }
    }

    @Test
    @DisplayName("papi(List, Player) de l'instance delegue au helper")
    void papiList_instance() {
        Player player = player("InstanceList");
        try (MockedStatic<PlaceholderAPI> api = Mockito.mockStatic(PlaceholderAPI.class)) {
            api.when(() -> PlaceholderAPI.setPlaceholders(player, "%zah_i%")).thenReturn("I");
            assertEquals(List.of("I", "x"), utils.papi(List.of("%zah_i%", "x"), player));
        }
    }
}
