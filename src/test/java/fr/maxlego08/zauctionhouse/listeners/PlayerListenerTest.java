package fr.maxlego08.zauctionhouse.listeners;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.command.CommandManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionClaimService;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import fr.maxlego08.zauctionhouse.api.services.AuctionHistoryService;
import fr.maxlego08.zauctionhouse.api.services.AuctionOptionService;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlayerListenerTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private StorageManager storageManager;
    private AuctionManager auctionManager;
    private AuctionClaimService claimService;
    private AuctionHistoryService historyService;
    private AuctionOptionService optionService;
    private AuctionExpireService expireService;
    private CommandManager commandManager;
    private AuctionClusterBridge clusterBridge;
    private PlayerCache cache;
    private Player player;
    private PlayerListener listener;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.storageManager = mock(StorageManager.class);
        this.auctionManager = mock(AuctionManager.class);
        this.claimService = mock(AuctionClaimService.class);
        this.historyService = mock(AuctionHistoryService.class);
        this.optionService = mock(AuctionOptionService.class);
        this.expireService = mock(AuctionExpireService.class);
        this.commandManager = mock(CommandManager.class);
        this.clusterBridge = mock(AuctionClusterBridge.class);
        this.cache = mock(PlayerCache.class);
        this.player = MockPlugins.player("Alice");

        lenient().when(this.plugin.getStorageManager()).thenReturn(this.storageManager);
        lenient().when(this.plugin.getAuctionManager()).thenReturn(this.auctionManager);
        lenient().when(this.plugin.getCommandManager()).thenReturn(this.commandManager);
        lenient().when(this.plugin.getAuctionClusterBridge()).thenReturn(this.clusterBridge);
        lenient().when(this.auctionManager.getClaimService()).thenReturn(this.claimService);
        lenient().when(this.auctionManager.getHistoryService()).thenReturn(this.historyService);
        lenient().when(this.auctionManager.getOptionService()).thenReturn(this.optionService);
        lenient().when(this.auctionManager.getExpireService()).thenReturn(this.expireService);
        lenient().when(this.auctionManager.getCache(this.player)).thenReturn(this.cache);
        lenient().when(this.optionService.loadPlayerOptions(any())).thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(this.clusterBridge.notifyItemStatusChange(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        this.listener = new PlayerListener(this.plugin);
    }

    private PlayerJoinEvent joinEvent() {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(this.player);
        return event;
    }

    private PlayerQuitEvent quitEvent() {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(this.player);
        return event;
    }

    private PlayerDeathEvent deathEvent() {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(this.player);
        return event;
    }

    /**
     * Item mocke en statut de confirmation, encore detenu en LISTED par ce serveur : c'est le
     * cas que {@code ConfirmHelper.releaseConfirmation} doit rearmer.
     */
    private Item confirmingItem(ItemStatus status) {
        Item item = mock(Item.class);
        lenient().when(item.getId()).thenReturn(5);
        lenient().when(item.getStatus()).thenReturn(status);
        lenient().when(item.isExpired()).thenReturn(false);
        lenient().when(this.cache.get(PlayerCacheKey.ITEM_SHOW)).thenReturn(item);
        lenient().when(this.auctionManager.getItem(StorageType.LISTED, 5)).thenReturn(item);
        return item;
    }

    @Nested
    @DisplayName("onConnect")
    class ConnectTests {

        @Test
        @DisplayName("upsert du joueur, puis claim, historique et options sont declenches")
        void connect_callsEveryService() {
            listener.onConnect(joinEvent());

            verify(storageManager).upsertPlayer(player);
            verify(claimService).handlePlayerJoin(player);
            verify(historyService).handlePlayerJoin(player);
            verify(optionService).loadPlayerOptions(player.getUniqueId());
        }

        @Test
        @DisplayName("l'upsert precede les services (ils peuvent lire la ligne joueur)")
        void connect_upsertFirst() {
            listener.onConnect(joinEvent());

            InOrder order = inOrder(storageManager, claimService, historyService, optionService);
            order.verify(storageManager).upsertPlayer(player);
            order.verify(claimService).handlePlayerJoin(player);
            order.verify(historyService).handlePlayerJoin(player);
            order.verify(optionService).loadPlayerOptions(player.getUniqueId());
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("un joueur ordinaire ne recoit pas le message de version differe")
        void connect_regularPlayer_noDelayedMessage() {
            listener.onConnect(joinEvent());

            verify(plugin.getScheduler(), never()).runLater(any(Consumer.class), anyLong());
            verify(player, never()).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("l'auteur du plugin recoit un message de version 40 ticks plus tard s'il est encore en ligne")
        void connect_author_receivesVersionMessage() {
            player = MockPlugins.player("Maxlego08");
            lenient().when(auctionManager.getCache(player)).thenReturn(cache);
            PluginDescriptionFile description = mock(PluginDescriptionFile.class);
            when(description.getVersion()).thenReturn("4.0.1.6");
            when(plugin.getDescription()).thenReturn(description);

            listener.onConnect(joinEvent());

            // L'ordonnanceur mocke execute immediatement : le message a ete envoye.
            verify(player).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("l'auteur deconnecte avant l'echeance ne recoit rien")
        void connect_authorOffline_noMessage() {
            player = MockPlugins.player("Maxlego08");
            when(player.isOnline()).thenReturn(false);
            lenient().when(auctionManager.getCache(player)).thenReturn(cache);

            listener.onConnect(joinEvent());

            verify(player, never()).sendMessage(any(Component.class));
        }
    }

    @Nested
    @DisplayName("onQuit")
    class QuitTests {

        @Test
        @DisplayName("cache joueur, options et cooldowns sont purges")
        void quit_clearsEverything() {
            listener.onQuit(quitEvent());

            verify(auctionManager).removeCache(player);
            verify(optionService).clearPlayerOptions(player.getUniqueId());
            verify(commandManager).clearCooldowns(player.getUniqueId());
        }

        @Test
        @DisplayName("ORDRE : la confirmation est liberee (lecture ITEM_SHOW) AVANT la destruction du cache")
        void quit_releasesConfirmationBeforeRemovingCache() {
            listener.onQuit(quitEvent());

            InOrder order = inOrder(cache, auctionManager);
            order.verify(cache).get(PlayerCacheKey.ITEM_SHOW);
            order.verify(auctionManager).removeCache(player);
        }

        @Test
        @DisplayName("sans confirmation ouverte : aucun statut modifie, rien diffuse au cluster")
        void quit_withoutConfirmation_noRelease() {
            listener.onQuit(quitEvent());

            verifyNoInteractions(clusterBridge);
            verify(auctionManager, never()).clearPlayersCache(any(PlayerCacheKey[].class));
        }

        @Test
        @DisplayName("confirmation d'achat ouverte sur un item encore en vente : rearme AVAILABLE et diffuse")
        void quit_withPurchaseConfirmation_releasesItem() {
            Item item = confirmingItem(ItemStatus.IS_PURCHASE_CONFIRM);

            listener.onQuit(quitEvent());

            verify(cache).remove(PlayerCacheKey.ITEM_SHOW);
            verify(item).setStatus(ItemStatus.AVAILABLE);
            verify(clusterBridge).notifyItemStatusChange(item, ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.AVAILABLE);
            verify(auctionManager).clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            verify(auctionManager).updateListedItems(item, true, player);
            verify(auctionManager).removeCache(player);
        }

        @Test
        @DisplayName("confirmation de retrait ouverte : meme rearmement")
        void quit_withRemoveConfirmation_releasesItem() {
            Item item = confirmingItem(ItemStatus.IS_REMOVE_CONFIRM);

            listener.onQuit(quitEvent());

            verify(item).setStatus(ItemStatus.AVAILABLE);
            verify(clusterBridge).notifyItemStatusChange(item, ItemStatus.IS_REMOVE_CONFIRM, ItemStatus.AVAILABLE);
        }

        @Test
        @DisplayName("section critique engagee (IS_BEING_PURCHASED) : le statut n'est JAMAIS restaure")
        void quit_duringCriticalSection_doesNotRelease() {
            Item item = confirmingItem(ItemStatus.IS_BEING_PURCHASED);

            listener.onQuit(quitEvent());

            verify(item, never()).setStatus(any());
            verify(cache, never()).remove(PlayerCacheKey.ITEM_SHOW);
            verifyNoInteractions(clusterBridge);
            verify(auctionManager).removeCache(player);
        }

        @Test
        @DisplayName("item plus detenu en LISTED par ce serveur : reference purgee, rien rediffuse")
        void quit_itemNoLongerHeld_doesNotRebroadcast() {
            Item item = confirmingItem(ItemStatus.IS_PURCHASE_CONFIRM);
            when(auctionManager.getItem(StorageType.LISTED, 5)).thenReturn(null);

            listener.onQuit(quitEvent());

            verify(cache).remove(PlayerCacheKey.ITEM_SHOW);
            verify(item, never()).setStatus(any());
            verifyNoInteractions(clusterBridge);
        }

        @Test
        @DisplayName("item expire pendant la confirmation : route vers l'expiration au lieu d'etre rediffuse")
        void quit_expiredItem_isExpiredNotRebroadcast() {
            Item item = confirmingItem(ItemStatus.IS_PURCHASE_CONFIRM);
            when(item.isExpired()).thenReturn(true);

            listener.onQuit(quitEvent());

            verify(expireService).processExpiredItem(item, StorageType.LISTED);
            verify(auctionManager).clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            verify(item, never()).setStatus(any());
            verifyNoInteractions(clusterBridge);
        }
    }

    @Nested
    @DisplayName("onDeath")
    class DeathTests {

        @Test
        @DisplayName("la mort libere la confirmation ouverte (zMenu ne declenche pas onInventoryClose)")
        void death_releasesConfirmation() {
            Item item = confirmingItem(ItemStatus.IS_PURCHASE_CONFIRM);

            listener.onDeath(deathEvent());

            verify(item).setStatus(ItemStatus.AVAILABLE);
            verify(clusterBridge).notifyItemStatusChange(item, ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.AVAILABLE);
        }

        @Test
        @DisplayName("la mort ne detruit PAS le cache joueur ni les cooldowns")
        void death_doesNotClearCache() {
            listener.onDeath(deathEvent());

            verify(cache).get(PlayerCacheKey.ITEM_SHOW);
            verify(auctionManager, never()).removeCache(any());
            verifyNoInteractions(commandManager, optionService);
        }
    }
}
