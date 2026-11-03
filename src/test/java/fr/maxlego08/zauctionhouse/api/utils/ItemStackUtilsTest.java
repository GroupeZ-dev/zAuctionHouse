package fr.maxlego08.zauctionhouse.api.utils;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sans serveur, la voie NMS par reflexion est inutilisable : ces tests verifient precisement
 * que l'echec est ABSORBE (null + journal SEVERE) et jamais propage, et que le repli Bukkit
 * direct est emprunte sans recursion.
 */
class ItemStackUtilsTest {

    private static MockedStatic<Bukkit> mockBukkit(Logger logger) {
        Server server = mock(Server.class);
        MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.21.10-R0.1-SNAPSHOT");
        bukkit.when(Bukkit::getLogger).thenReturn(logger);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        return bukkit;
    }

    @Test
    @DisplayName("serializeItemStack(null) rend la chaine \"null\" sans toucher au serveur")
    void serialize_null_returnsNullString() {
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> ignored = mockBukkit(logger)) {
            assertEquals("null", ItemStackUtils.serializeItemStack(null));
            verify(logger, never()).log(any(Level.class), anyString(), any(Throwable.class));
        }
    }

    @Test
    @DisplayName("serializeItemStack rend null (jamais une chaine vide) quand la voie NMS echoue, et journalise en SEVERE")
    void serialize_nmsFailure_returnsNullAndLogs() {
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> ignored = mockBukkit(logger)) {
            ItemStack itemStack = mock(ItemStack.class);

            String result = ItemStackUtils.serializeItemStack(itemStack);

            assertNull(result, "une charge vide partirait silencieusement en base");
            verify(logger).log(eq(Level.SEVERE), anyString(), any(Throwable.class));
        }
    }

    @Test
    @DisplayName("tryDeserializeItemStack propage l'erreur sur une charge non Base64")
    void tryDeserialize_invalidPayload_throws() {
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> ignored = mockBukkit(logger)) {
            assertThrows(Exception.class, () -> ItemStackUtils.tryDeserializeItemStack("%%%"));
        }
    }

    @Test
    @DisplayName("safeDeserializeItemStack absorbe l'echec NMS, retombe sur le flux Bukkit et rend null sur une charge illisible")
    void safeDeserialize_invalidPayload_returnsNull() {
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> ignored = mockBukkit(logger)) {
            assertNull(ItemStackUtils.safeDeserializeItemStack("%%%"));
            // Le repli Bukkit journalise un WARNING (sans trace) sur la charge illisible.
            verify(logger).log(eq(Level.WARNING), anyString());
        }
    }

    @Test
    @DisplayName("safeDeserializeItemStack sur une chaine vide rend null sans lever")
    void safeDeserialize_empty_returnsNull() {
        Logger logger = mock(Logger.class);
        try (MockedStatic<Bukkit> ignored = mockBukkit(logger)) {
            assertNull(ItemStackUtils.safeDeserializeItemStack(""));
        }
    }
}
