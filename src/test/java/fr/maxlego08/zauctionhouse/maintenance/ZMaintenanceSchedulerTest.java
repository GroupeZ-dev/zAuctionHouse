package fr.maxlego08.zauctionhouse.maintenance;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.configuration.records.MaintenanceConfiguration;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L'ordonnanceur est une doublure CONTROLEE : {@code runTimerAsync} capture le balayage par
 * periode au lieu de l'executer, {@code runNextTick} empile la mutation jusqu'a l'appel de
 * {@link #runPendingTicks()}. Le temps est pilote en vieillissant directement la map privee
 * {@code confirmationSince} (la seule horloge du balayage est {@code System.currentTimeMillis()}).
 */
class ZMaintenanceSchedulerTest {

    private static final long CONFIRMATION_INTERVAL = 10L;
    private static final long EXPIRATION_INTERVAL = 60L;
    private static final long CONFIRMATION_TIMEOUT = 60L;
    private static final int BATCH_SIZE = 3;

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private PlatformScheduler scheduler;
    private AuctionManager manager;
    private AuctionExpireService expireService;
    private AuctionClusterBridge clusterBridge;
    private ZMaintenanceScheduler maintenance;

    /** Balayages captures, indexes par leur periode en secondes. */
    private final Map<Long, Runnable> timers = new HashMap<>();
    private final List<WrappedTask> timerTasks = new ArrayList<>();
    private final List<Consumer<WrappedTask>> pendingTicks = new ArrayList<>();

    private final List<Item> listed = new ArrayList<>();
    private final List<Item> expired = new ArrayList<>();
    private final List<Item> purchased = new ArrayList<>();

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.scheduler = mock(PlatformScheduler.class);
        this.manager = mock(AuctionManager.class);
        this.expireService = mock(AuctionExpireService.class);
        this.clusterBridge = mock(AuctionClusterBridge.class);

        when(this.plugin.getScheduler()).thenReturn(this.scheduler);
        lenient().when(this.plugin.getAuctionManager()).thenReturn(this.manager);
        lenient().when(this.plugin.getAuctionClusterBridge()).thenReturn(this.clusterBridge);
        lenient().when(this.manager.getExpireService()).thenReturn(this.expireService);
        lenient().when(this.manager.getItems(StorageType.LISTED)).thenReturn(this.listed);
        lenient().when(this.manager.getItems(StorageType.EXPIRED)).thenReturn(this.expired);
        lenient().when(this.manager.getItems(StorageType.PURCHASED)).thenReturn(this.purchased);
        lenient().when(this.clusterBridge.notifyItemStatusChange(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        lenient().when(this.scheduler.runTimerAsync(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            this.timers.put(invocation.<Long>getArgument(2), invocation.<Runnable>getArgument(0));
            WrappedTask task = mock(WrappedTask.class);
            this.timerTasks.add(task);
            return task;
        });
        lenient().when(this.scheduler.runNextTick(any())).thenAnswer(invocation -> {
            this.pendingTicks.add(invocation.<Consumer<WrappedTask>>getArgument(0));
            return CompletableFuture.completedFuture(null);
        });

        configure(new MaintenanceConfiguration(EXPIRATION_INTERVAL, BATCH_SIZE, CONFIRMATION_TIMEOUT, CONFIRMATION_INTERVAL));
        this.maintenance = new ZMaintenanceScheduler(this.plugin);
    }

    private void configure(MaintenanceConfiguration configuration) {
        when(this.plugin.getConfiguration().getMaintenance()).thenReturn(configuration);
    }

    private Runnable confirmationSweep() {
        Runnable sweep = this.timers.get(CONFIRMATION_INTERVAL);
        assertNotNull(sweep, "confirmation sweep not scheduled");
        return sweep;
    }

    private Runnable expirationSweep() {
        Runnable sweep = this.timers.get(EXPIRATION_INTERVAL);
        assertNotNull(sweep, "expiration sweep not scheduled");
        return sweep;
    }

    private void runPendingTicks() {
        List<Consumer<WrappedTask>> ticks = new ArrayList<>(this.pendingTicks);
        this.pendingTicks.clear();
        WrappedTask task = mock(WrappedTask.class);
        ticks.forEach(tick -> tick.accept(task));
    }

    private Map<Integer, Long> confirmationSince() {
        return ReflectionTestUtils.field(this.maintenance, "confirmationSince");
    }

    /** Vieillit l'observation d'un item au-dela du delai configure. */
    private void ageConfirmation(int itemId) {
        confirmationSince().put(itemId, System.currentTimeMillis() - CONFIRMATION_TIMEOUT * 1000L - 1L);
    }

    /**
     * Item mocke a statut MUTABLE : {@code setStatus} est reflete par {@code getStatus}, comme
     * sur un vrai item, sans quoi les gardes du tick ne seraient pas testables.
     */
    private static Item item(int id, ItemStatus status, boolean expired) {
        Item item = mock(Item.class);
        AtomicReference<ItemStatus> current = new AtomicReference<>(status);
        lenient().when(item.getId()).thenReturn(id);
        lenient().when(item.getStatus()).thenAnswer(invocation -> current.get());
        lenient().doAnswer(invocation -> {
            current.set(invocation.getArgument(0));
            return null;
        }).when(item).setStatus(any());
        lenient().when(item.isExpired()).thenReturn(expired);
        return item;
    }

    @Nested
    @DisplayName("start / stop")
    class LifecycleTests {

        @Test
        @DisplayName("start planifie les deux balayages avec leurs intervalles en secondes")
        void start_schedulesBothSweeps() {
            maintenance.start();

            verify(scheduler).runTimerAsync(any(Runnable.class), eq(CONFIRMATION_INTERVAL), eq(CONFIRMATION_INTERVAL), eq(TimeUnit.SECONDS));
            verify(scheduler).runTimerAsync(any(Runnable.class), eq(EXPIRATION_INTERVAL), eq(EXPIRATION_INTERVAL), eq(TimeUnit.SECONDS));
            assertEquals(2, timers.size());
        }

        @Test
        @DisplayName("timeout de confirmation a 0 -> seul le balayage d'expiration est planifie")
        void start_confirmationDisabled() {
            configure(new MaintenanceConfiguration(EXPIRATION_INTERVAL, BATCH_SIZE, 0L, CONFIRMATION_INTERVAL));

            maintenance.start();

            assertEquals(1, timers.size());
            assertTrue(timers.containsKey(EXPIRATION_INTERVAL));
        }

        @Test
        @DisplayName("intervalle d'expiration a 0 -> seul le balayage de confirmation est planifie")
        void start_expirationDisabled() {
            configure(new MaintenanceConfiguration(0L, BATCH_SIZE, CONFIRMATION_TIMEOUT, CONFIRMATION_INTERVAL));

            maintenance.start();

            assertEquals(1, timers.size());
            assertTrue(timers.containsKey(CONFIRMATION_INTERVAL));
        }

        @Test
        @DisplayName("configuration de maintenance absente -> rien n'est planifie")
        void start_nullConfiguration_noop() {
            configure(null);

            maintenance.start();

            verify(scheduler, never()).runTimerAsync(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("ordonnanceur absent -> rien n'est planifie, pas d'exception")
        void start_nullScheduler_noop() {
            when(plugin.getScheduler()).thenReturn(null);

            maintenance.start();

            assertTrue(timers.isEmpty());
        }

        @Test
        @DisplayName("stop annule les deux taches et oublie les observations")
        void stop_cancelsTasksAndClearsMemory() {
            maintenance.start();
            confirmationSince().put(1, 123L);

            maintenance.stop();

            assertEquals(2, timerTasks.size());
            timerTasks.forEach(task -> verify(task).cancel());
            assertTrue(confirmationSince().isEmpty());
        }

        @Test
        @DisplayName("stop sans start ne fait rien")
        void stop_withoutStart_noop() {
            maintenance.stop();

            assertTrue(timerTasks.isEmpty());
        }

        @Test
        @DisplayName("start est idempotent : un second appel annule les anciennes taches avant d'en creer de nouvelles")
        void start_twice_cancelsPreviousTasks() {
            maintenance.start();
            List<WrappedTask> first = new ArrayList<>(timerTasks);

            maintenance.start();

            first.forEach(task -> verify(task).cancel());
            assertEquals(4, timerTasks.size());
            verify(scheduler, times(4)).runTimerAsync(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("Balayage des confirmations")
    class ConfirmationSweepTests {

        @BeforeEach
        void start() {
            maintenance.start();
        }

        @Test
        @DisplayName("premier passage : l'item est seulement observe, rien n'est modifie")
        void firstPass_onlyRecords() {
            Item item = item(1, ItemStatus.IS_PURCHASE_CONFIRM, false);
            listed.add(item);

            confirmationSweep().run();

            assertTrue(confirmationSince().containsKey(1));
            assertTrue(pendingTicks.isEmpty());
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
        }

        @Test
        @DisplayName("avant l'expiration du delai, un second passage ne libere pas")
        void beforeTimeout_noRelease() {
            listed.add(item(1, ItemStatus.IS_PURCHASE_CONFIRM, false));

            confirmationSweep().run();
            confirmationSweep().run();

            assertTrue(pendingTicks.isEmpty());
        }

        @Test
        @DisplayName("item LISTED bloque trop longtemps : rearme AVAILABLE, diffuse au cluster, caches reconstruits")
        void listedItem_afterTimeout_releasedAndBroadcast() {
            Item item = item(1, ItemStatus.IS_PURCHASE_CONFIRM, false);
            listed.add(item);
            confirmationSweep().run();
            ageConfirmation(1);

            confirmationSweep().run();
            assertEquals(1, pendingTicks.size());
            runPendingTicks();

            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            verify(clusterBridge).notifyItemStatusChange(item, ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.AVAILABLE);
            verify(manager).rebuildSortedItemsCache();
            verify(manager).clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);
            assertFalse(confirmationSince().containsKey(1));
        }

        @Test
        @DisplayName("IS_REMOVE_CONFIRM est aussi rearme")
        void removeConfirm_released() {
            Item item = item(2, ItemStatus.IS_REMOVE_CONFIRM, false);
            listed.add(item);
            confirmationSweep().run();
            ageConfirmation(2);

            confirmationSweep().run();
            runPendingTicks();

            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            verify(clusterBridge).notifyItemStatusChange(item, ItemStatus.IS_REMOVE_CONFIRM, ItemStatus.AVAILABLE);
        }

        @Test
        @DisplayName("item EXPIRED : repare localement vers REMOVED, sans diffusion ni reconstruction du cache trie")
        void expiredContainer_repairedLocally() {
            Item item = item(3, ItemStatus.IS_REMOVE_CONFIRM, false);
            expired.add(item);
            confirmationSweep().run();
            ageConfirmation(3);

            confirmationSweep().run();
            runPendingTicks();

            assertEquals(ItemStatus.REMOVED, item.getStatus());
            verifyNoInteractions(clusterBridge);
            verify(manager, never()).rebuildSortedItemsCache();
            verify(manager, never()).clearPlayersCache(any(PlayerCacheKey[].class));
        }

        @Test
        @DisplayName("item PURCHASED : repare localement vers PURCHASED")
        void purchasedContainer_repairedLocally() {
            Item item = item(4, ItemStatus.IS_PURCHASE_CONFIRM, false);
            purchased.add(item);
            confirmationSweep().run();
            ageConfirmation(4);

            confirmationSweep().run();
            runPendingTicks();

            assertEquals(ItemStatus.PURCHASED, item.getStatus());
            verifyNoInteractions(clusterBridge);
        }

        @Test
        @DisplayName("IS_BEING_PURCHASED / IS_BEING_REMOVED ne sont jamais observes ni rearmes")
        void criticalSectionStatuses_ignored() {
            listed.add(item(5, ItemStatus.IS_BEING_PURCHASED, false));
            listed.add(item(6, ItemStatus.IS_BEING_REMOVED, false));
            listed.add(item(7, ItemStatus.AVAILABLE, false));

            confirmationSweep().run();

            assertTrue(confirmationSince().isEmpty());
            assertTrue(pendingTicks.isEmpty());
        }

        @Test
        @DisplayName("un item sorti de confirmation entre deux passages est oublie (map bornee)")
        void itemLeavingConfirmation_isForgotten() {
            Item item = item(1, ItemStatus.IS_PURCHASE_CONFIRM, false);
            listed.add(item);
            confirmationSweep().run();
            assertTrue(confirmationSince().containsKey(1));

            item.setStatus(ItemStatus.AVAILABLE);
            confirmationSweep().run();

            assertFalse(confirmationSince().containsKey(1));
        }

        @Test
        @DisplayName("un item disparu du store est oublie")
        void itemRemovedFromStore_isForgotten() {
            Item item = item(1, ItemStatus.IS_PURCHASE_CONFIRM, false);
            listed.add(item);
            confirmationSweep().run();

            listed.clear();
            confirmationSweep().run();

            assertTrue(confirmationSince().isEmpty());
        }

        @Test
        @DisplayName("le statut a bouge entre le balayage et le tick : rien n'est force")
        void statusChangedBeforeTick_notForced() {
            Item item = item(1, ItemStatus.IS_PURCHASE_CONFIRM, false);
            listed.add(item);
            confirmationSweep().run();
            ageConfirmation(1);
            confirmationSweep().run();

            // Une autre chaine (achat distant) a pris la main avant le tick principal.
            item.setStatus(ItemStatus.IS_BEING_PURCHASED);
            runPendingTicks();

            assertEquals(ItemStatus.IS_BEING_PURCHASED, item.getStatus());
            verifyNoInteractions(clusterBridge);
            verify(manager, never()).rebuildSortedItemsCache();
        }

        @Test
        @DisplayName("plusieurs items : seuls ceux au-dela du delai sont rearmes, en un seul tick")
        void mixedAges_onlyTimedOutReleased() {
            Item old = item(1, ItemStatus.IS_PURCHASE_CONFIRM, false);
            Item fresh = item(2, ItemStatus.IS_PURCHASE_CONFIRM, false);
            listed.add(old);
            listed.add(fresh);
            confirmationSweep().run();
            ageConfirmation(1);

            confirmationSweep().run();
            assertEquals(1, pendingTicks.size());
            runPendingTicks();

            assertEquals(ItemStatus.AVAILABLE, old.getStatus());
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, fresh.getStatus());
            verify(manager, times(1)).rebuildSortedItemsCache();
        }

        @Test
        @DisplayName("echec de diffusion au cluster : avale, le statut local reste rearme")
        void broadcastFailure_isSwallowed() {
            when(clusterBridge.notifyItemStatusChange(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("redis down")));
            Item item = item(1, ItemStatus.IS_PURCHASE_CONFIRM, false);
            listed.add(item);
            confirmationSweep().run();
            ageConfirmation(1);

            confirmationSweep().run();
            runPendingTicks();

            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            verify(manager).rebuildSortedItemsCache();
        }

        @Test
        @DisplayName("plugin en cours d'arret : le balayage ne fait rien")
        void shuttingDown_noop() {
            when(plugin.isShuttingDown()).thenReturn(true);
            listed.add(item(1, ItemStatus.IS_PURCHASE_CONFIRM, false));

            confirmationSweep().run();

            assertTrue(confirmationSince().isEmpty());
            verify(manager, never()).getItems(any());
        }

        @Test
        @DisplayName("balayage desactive apres rechargement de la configuration : ne fait rien")
        void disabledAtSweepTime_noop() {
            listed.add(item(1, ItemStatus.IS_PURCHASE_CONFIRM, false));
            configure(new MaintenanceConfiguration(EXPIRATION_INTERVAL, BATCH_SIZE, 0L, CONFIRMATION_INTERVAL));

            confirmationSweep().run();

            assertTrue(confirmationSince().isEmpty());
            verify(manager, never()).getItems(any());
        }

        @Test
        @DisplayName("configuration devenue null au moment du balayage : ne fait rien")
        void nullConfigurationAtSweepTime_noop() {
            configure(null);

            confirmationSweep().run();

            verify(manager, never()).getItems(any());
        }
    }

    @Nested
    @DisplayName("Balayage des expirations")
    class ExpirationSweepTests {

        @BeforeEach
        void start() {
            maintenance.start();
        }

        @Test
        @DisplayName("aucune annonce arrivee a terme : aucun tick planifie")
        void nothingExpired_noTick() {
            listed.add(item(1, ItemStatus.AVAILABLE, false));

            expirationSweep().run();

            assertTrue(pendingTicks.isEmpty());
            verifyNoInteractions(expireService);
        }

        @Test
        @DisplayName("les annonces AVAILABLE arrivees a terme sont routees vers le service d'expiration sur le tick")
        void expiredAvailable_routedToExpireService() {
            Item a = item(1, ItemStatus.AVAILABLE, true);
            Item b = item(2, ItemStatus.AVAILABLE, true);
            listed.add(a);
            listed.add(item(3, ItemStatus.AVAILABLE, false));
            listed.add(b);

            expirationSweep().run();
            verifyNoInteractions(expireService); // pas encore : hors thread principal
            runPendingTicks();

            verify(expireService).processExpiredItems(List.of(a, b), StorageType.LISTED);
        }

        @Test
        @DisplayName("les annonces expirees mais engagees (IS_*_CONFIRM, IS_BEING_*) ne sont pas touchees")
        void expiredButBusy_skipped() {
            listed.add(item(1, ItemStatus.IS_PURCHASE_CONFIRM, true));
            listed.add(item(2, ItemStatus.IS_REMOVE_CONFIRM, true));
            listed.add(item(3, ItemStatus.IS_BEING_PURCHASED, true));
            listed.add(item(4, ItemStatus.IS_BEING_REMOVED, true));

            expirationSweep().run();

            assertTrue(pendingTicks.isEmpty());
        }

        @Test
        @DisplayName("le lot est borne par expiration-sweep-batch-size, dans l'ordre du store")
        void batch_isBounded() {
            List<Item> items = new ArrayList<>();
            for (int index = 1; index <= BATCH_SIZE + 2; index++) {
                Item item = item(index, ItemStatus.AVAILABLE, true);
                items.add(item);
                listed.add(item);
            }

            expirationSweep().run();
            runPendingTicks();

            verify(expireService).processExpiredItems(items.subList(0, BATCH_SIZE), StorageType.LISTED);
        }

        @Test
        @DisplayName("exactement batch-size annonces expirees : toutes traitees")
        void batch_exactSize() {
            List<Item> items = new ArrayList<>();
            for (int index = 1; index <= BATCH_SIZE; index++) {
                Item item = item(index, ItemStatus.AVAILABLE, true);
                items.add(item);
                listed.add(item);
            }

            expirationSweep().run();
            runPendingTicks();

            verify(expireService).processExpiredItems(items, StorageType.LISTED);
        }

        @Test
        @DisplayName("seul le store LISTED est balaye pour l'expiration")
        void onlyListedStore_isScanned() {
            expired.add(item(1, ItemStatus.AVAILABLE, true));
            purchased.add(item(2, ItemStatus.AVAILABLE, true));

            expirationSweep().run();

            assertTrue(pendingTicks.isEmpty());
            verify(manager, never()).getItems(StorageType.EXPIRED);
            verify(manager, never()).getItems(StorageType.PURCHASED);
        }

        @Test
        @DisplayName("plugin en cours d'arret : ne fait rien")
        void shuttingDown_noop() {
            when(plugin.isShuttingDown()).thenReturn(true);
            listed.add(item(1, ItemStatus.AVAILABLE, true));

            expirationSweep().run();

            assertTrue(pendingTicks.isEmpty());
            verify(manager, never()).getItems(any());
        }

        @Test
        @DisplayName("balayage desactive apres rechargement : ne fait rien")
        void disabledAtSweepTime_noop() {
            configure(new MaintenanceConfiguration(0L, BATCH_SIZE, CONFIRMATION_TIMEOUT, CONFIRMATION_INTERVAL));
            listed.add(item(1, ItemStatus.AVAILABLE, true));

            expirationSweep().run();

            assertTrue(pendingTicks.isEmpty());
        }

        @Test
        @DisplayName("configuration null au moment du balayage : ne fait rien")
        void nullConfiguration_noop() {
            configure(null);

            expirationSweep().run();

            assertNull(timers.get(999L));
            verify(manager, never()).getItems(any());
        }
    }
}
