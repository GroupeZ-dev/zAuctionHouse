package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de la classe abstraite {@link AuctionService} : futures en erreur, confinement sur le
 * thread du joueur et watchdog de bail du verrou distribue.
 * <p>
 * L'ordonnanceur est ici un mock PASSIF (il n'execute rien) : les taches sont capturees puis
 * invoquees a la main pour controler exactement le nombre de renouvellements.
 */
class AuctionServiceTest {

    private static final int MAX_LEASE_RENEWALS = 30;

    /**
     * Expose les methodes protegees de {@link AuctionService}.
     */
    private static final class TestableService extends AuctionService {

        <T> CompletableFuture<T> failed(Throwable throwable) {
            return failedFuture(throwable);
        }

        void onThread(AuctionPlugin plugin, Player player, Runnable runnable) {
            onPlayerThread(plugin, player, runnable);
        }

        WrappedTask start(AuctionPlugin plugin, AuctionClusterBridge bridge, Item item, LockToken token, StorageType storageType) {
            return startLeaseWatchdog(plugin, bridge, item, token, storageType);
        }

        void stop(WrappedTask task) {
            stopLeaseWatchdog(task);
        }
    }

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private PlatformScheduler scheduler;
    private Player player;
    private Item item;
    private LockToken token;
    private TestableService service;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.scheduler = mock(PlatformScheduler.class);
        when(this.plugin.getScheduler()).thenReturn(this.scheduler);
        this.player = MockPlugins.player("Alice");
        this.item = mock(Item.class);
        when(this.item.getId()).thenReturn(42);
        this.token = LockToken.issue(this.item, UUID.randomUUID());
        this.service = new TestableService();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Consumer<WrappedTask>> consumerCaptor() {
        return ArgumentCaptor.forClass(Consumer.class);
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("failedFuture rend un future deja complete en erreur avec l'exception fournie")
    void failedFuture_completesExceptionally() {
        IllegalStateException cause = new IllegalStateException("boom");

        CompletableFuture<String> future = this.service.failed(cause);

        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertSame(cause, exception.getCause());
    }

    @Nested
    @DisplayName("onPlayerThread")
    class OnPlayerThread {

        @Test
        @DisplayName("deja sur la region du joueur : le traitement s'execute en ligne, sans ordonnancement")
        void onPlayerThread_ownedRegion_runsInline() {
            when(scheduler.isOwnedByCurrentRegion(player)).thenReturn(true);
            AtomicInteger runs = new AtomicInteger();

            service.onThread(plugin, player, runs::incrementAndGet);

            assertEquals(1, runs.get());
            verify(scheduler, never()).runAtEntity(any(), any());
        }

        @Test
        @DisplayName("hors de la region du joueur : le traitement est confie a runAtEntity et ne s'execute qu'avec la tache")
        void onPlayerThread_otherThread_schedulesAtEntity() {
            when(scheduler.isOwnedByCurrentRegion(player)).thenReturn(false);
            AtomicInteger runs = new AtomicInteger();

            service.onThread(plugin, player, runs::incrementAndGet);

            assertEquals(0, runs.get(), "rien ne doit s'executer avant la tache de region");
            ArgumentCaptor<Consumer<WrappedTask>> captor = consumerCaptor();
            verify(scheduler).runAtEntity(eq(player), captor.capture());
            captor.getValue().accept(mock(WrappedTask.class));
            assertEquals(1, runs.get());
        }
    }

    @Nested
    @DisplayName("startLeaseWatchdog : cas ou rien n'est arme")
    class WatchdogNotArmed {

        @Test
        @DisplayName("jeton null : aucun watchdog")
        void nullToken_returnsNull() {
            AuctionClusterBridge bridge = mock(AuctionClusterBridge.class);

            assertNull(service.start(plugin, bridge, item, null, StorageType.LISTED));

            verifyNoInteractions(scheduler, bridge);
        }

        @Test
        @DisplayName("jeton noop (contention) ou unavailable : aucun watchdog")
        void sentinelTokens_returnNull() {
            AuctionClusterBridge bridge = mock(AuctionClusterBridge.class);

            assertNull(service.start(plugin, bridge, item, LockToken.noop(), StorageType.LISTED));
            assertNull(service.start(plugin, bridge, item, LockToken.unavailable(), StorageType.LISTED));

            verifyNoInteractions(scheduler, bridge);
        }

        @Test
        @DisplayName("bridge mono-serveur (bail ZERO) : aucun watchdog, comportement d'avant le correctif")
        void localBridge_zeroLease_returnsNull() {
            AuctionClusterBridge bridge = new LocalAuctionClusterBridge();

            assertNull(service.start(plugin, bridge, item, token, StorageType.LISTED));

            verifyNoInteractions(scheduler);
        }

        @Test
        @DisplayName("bail null ou negatif : aucun watchdog")
        void nullOrNegativeLease_returnsNull() {
            AuctionClusterBridge nullLease = mock(AuctionClusterBridge.class);
            when(nullLease.lockLeaseDuration()).thenReturn(null);
            AuctionClusterBridge negativeLease = mock(AuctionClusterBridge.class);
            when(negativeLease.lockLeaseDuration()).thenReturn(Duration.ofSeconds(-5));

            assertNull(service.start(plugin, nullLease, item, token, StorageType.LISTED));
            assertNull(service.start(plugin, negativeLease, item, token, StorageType.LISTED));

            verifyNoInteractions(scheduler);
        }
    }

    @Nested
    @DisplayName("startLeaseWatchdog : watchdog arme")
    class WatchdogArmed {

        private AuctionClusterBridge bridge;
        private WrappedTask task;

        @BeforeEach
        void setUpBridge() {
            this.bridge = mock(AuctionClusterBridge.class);
            this.task = mock(WrappedTask.class);
            when(this.bridge.renewLock(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(true));
            when(scheduler.runTimerAsync(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(this.task);
        }

        private Runnable armAndCapture(Duration lease) {
            when(this.bridge.lockLeaseDuration()).thenReturn(lease);
            WrappedTask returned = service.start(plugin, this.bridge, item, token, StorageType.LISTED);
            assertSame(this.task, returned, "la tache rendue est celle de l'ordonnanceur");
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTimerAsync(captor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
            return captor.getValue();
        }

        @ParameterizedTest(name = "bail {0} ms -> periode {1} ms")
        @CsvSource({
                "30000, 10000",
                "6000, 2000",
                "3000, 1000",
                "1500, 1000",
                "2, 1000"
        })
        @DisplayName("la periode vaut bail / 3, avec un plancher de 1000 ms")
        void period_isThirdOfLease_withFloor(long leaseMs, long expectedPeriod) {
            when(this.bridge.lockLeaseDuration()).thenReturn(Duration.ofMillis(leaseMs));

            service.start(plugin, this.bridge, item, token, StorageType.PURCHASED);

            verify(scheduler).runTimerAsync(any(Runnable.class), eq(expectedPeriod), eq(expectedPeriod), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("chaque tick renouvelle le bail avec l'item, le jeton et la portee d'origine")
        void tick_renewsLock() {
            Runnable tick = armAndCapture(Duration.ofSeconds(30));

            tick.run();
            tick.run();

            verify(this.bridge, times(2)).renewLock(item, token, StorageType.LISTED);
            verify(this.task, never()).cancel();
        }

        @Test
        @DisplayName("un renouvellement refuse ou en erreur est journalise sans arreter le watchdog")
        void tick_renewalLostOrFailed_doesNotThrow() {
            Runnable tick = armAndCapture(Duration.ofSeconds(30));
            when(this.bridge.renewLock(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(false))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("redis down")));

            tick.run();
            tick.run();

            verify(this.bridge, times(2)).renewLock(item, token, StorageType.LISTED);
            verify(this.task, never()).cancel();
        }

        @Test
        @DisplayName("au-dela de 30 renouvellements, le watchdog s'annule lui-meme et ne renouvelle plus")
        void tick_exceedsMaxRenewals_cancelsItself() {
            Runnable tick = armAndCapture(Duration.ofSeconds(30));

            for (int index = 0; index < MAX_LEASE_RENEWALS + 1; index++) tick.run();

            verify(this.bridge, times(MAX_LEASE_RENEWALS)).renewLock(item, token, StorageType.LISTED);
            verify(this.task).cancel();
        }

        @Test
        @DisplayName("exactement 30 renouvellements : aucune annulation")
        void tick_exactlyMaxRenewals_keepsRunning() {
            Runnable tick = armAndCapture(Duration.ofSeconds(30));

            for (int index = 0; index < MAX_LEASE_RENEWALS; index++) tick.run();

            verify(this.bridge, times(MAX_LEASE_RENEWALS)).renewLock(any(), any(), any());
            verify(this.task, never()).cancel();
        }
    }

    @Nested
    @DisplayName("stopLeaseWatchdog")
    class StopWatchdog {

        @Test
        @DisplayName("tolere null")
        void stop_null_isNoop() {
            service.stop(null);
        }

        @Test
        @DisplayName("annule la tache fournie")
        void stop_task_cancelsIt() {
            WrappedTask task = mock(WrappedTask.class);

            service.stop(task);

            verify(task).cancel();
            assertFalse(task.isCancelled(), "mock passif : isCancelled n'est pas simule, seul cancel() compte");
        }
    }
}
