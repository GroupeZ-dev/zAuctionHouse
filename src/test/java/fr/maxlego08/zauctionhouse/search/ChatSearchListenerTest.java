package fr.maxlego08.zauctionhouse.search;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Le plugin mocke n'est pas un {@code ZAuctionPlugin} : le dialogue zMenu est donc absent et le
 * listener retombe systematiquement sur la saisie dans le tchat, ce qui est exactement le chemin
 * testable sans serveur.
 */
class ChatSearchListenerTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private AuctionManager manager;
    private Player player;
    private ChatSearchListener listener;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.manager = mock(AuctionManager.class);
        lenient().when(this.plugin.getAuctionManager()).thenReturn(this.manager);
        this.player = MockPlugins.player("Alice");
        this.listener = new ChatSearchListener(this.plugin);
    }

    private AsyncChatEvent chat(Player sender, Component message) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        lenient().when(event.getPlayer()).thenReturn(sender);
        lenient().when(event.message()).thenReturn(message);
        return event;
    }

    private PlayerQuitEvent quit(Player who) {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(who);
        return event;
    }

    @Nested
    @DisplayName("startSearch")
    class StartTests {

        @Test
        @DisplayName("ferme l'inventaire et envoie SEARCH_START")
        void startSearch_closesInventoryAndPrompts() {
            listener.startSearch(player);

            verify(player).closeInventory();
            verify(plugin).sendMessage(player, Message.SEARCH_START);
        }

        @Test
        @DisplayName("startDialogSearch sans dialogue disponible retombe sur la saisie tchat")
        void startDialogSearch_withoutDialog_fallsBackToChat() {
            listener.startDialogSearch(player);

            verify(player).closeInventory();
            verify(plugin).sendMessage(player, Message.SEARCH_START);
        }

        @Test
        @DisplayName("reloadDialog et clear sans dialogue ne levent rien")
        void reloadAndClear_withoutDialog_noop() {
            listener.reloadDialog();
            listener.clear();
        }
    }

    @Nested
    @DisplayName("onChat")
    class ChatTests {

        @Test
        @DisplayName("un joueur qui n'attend pas de saisie : message non intercepte")
        void notWaiting_eventUntouched() {
            AsyncChatEvent event = chat(player, Component.text("hello"));

            listener.onChat(event);

            verify(event, never()).setCancelled(true);
            verifyNoInteractions(manager);
        }

        @Test
        @DisplayName("joueur en attente : le message est annule et devient la requete de recherche (texte brut)")
        void waiting_messageBecomesQuery() {
            listener.startSearch(player);
            AsyncChatEvent event = chat(player, Component.text("diamond sword", NamedTextColor.GOLD));

            listener.onChat(event);

            verify(event).setCancelled(true);
            verify(manager).startSearch(player, "diamond sword");
        }

        @Test
        @DisplayName("la saisie n'est consommee qu'une fois : le second message n'est pas intercepte")
        void waiting_consumedOnce() {
            listener.startSearch(player);
            listener.onChat(chat(player, Component.text("first")));
            AsyncChatEvent second = chat(player, Component.text("second"));

            listener.onChat(second);

            verify(second, never()).setCancelled(true);
            verify(manager, never()).startSearch(player, "second");
        }

        @Test
        @DisplayName("un autre joueur n'est pas concerne par l'attente")
        void otherPlayer_notIntercepted() {
            listener.startSearch(player);
            Player other = MockPlugins.player("Bob");
            AsyncChatEvent event = chat(other, Component.text("x"));

            listener.onChat(event);

            verify(event, never()).setCancelled(true);
            verify(manager, never()).startSearch(eq(other), anyString());
            // Alice attend toujours
            listener.onChat(chat(player, Component.text("y")));
            verify(manager).startSearch(player, "y");
        }

        @Test
        @DisplayName("joueur deconnecte avant le tick : la recherche n'est pas lancee")
        void offlineBeforeTick_notStarted() {
            listener.startSearch(player);
            when(player.isOnline()).thenReturn(false);
            AsyncChatEvent event = chat(player, Component.text("x"));

            listener.onChat(event);

            verify(event).setCancelled(true);
            verify(manager, never()).startSearch(any(), anyString());
        }

        @Test
        @DisplayName("message vide : requete vide transmise")
        void emptyMessage_emptyQuery() {
            listener.startSearch(player);

            listener.onChat(chat(player, Component.empty()));

            verify(manager).startSearch(player, "");
        }

        @Test
        @DisplayName("clear() oublie les joueurs en attente")
        void clear_forgetsWaitingPlayers() {
            listener.startSearch(player);
            listener.clear();
            AsyncChatEvent event = chat(player, Component.text("x"));

            listener.onChat(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("onQuit")
    class QuitTests {

        @Test
        @DisplayName("la deconnexion annule l'attente de saisie")
        void quit_cancelsWaiting() {
            listener.startSearch(player);

            listener.onQuit(quit(player));
            AsyncChatEvent event = chat(player, Component.text("x"));
            listener.onChat(event);

            verify(event, never()).setCancelled(true);
            verifyNoInteractions(manager);
        }

        @Test
        @DisplayName("la deconnexion d'un autre joueur ne touche pas l'attente")
        void otherQuit_keepsWaiting() {
            listener.startSearch(player);

            listener.onQuit(quit(MockPlugins.player("Bob")));
            listener.onChat(chat(player, Component.text("x")));

            verify(manager).startSearch(player, "x");
        }
    }
}
