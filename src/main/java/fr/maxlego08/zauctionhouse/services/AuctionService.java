package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AuctionService extends ZUtils {

    /**
     * Plafond de renouvellements : au-dela, la section critique est consideree bloquee.
     */
    private static final int MAX_LEASE_RENEWALS = 30;

    /**
     * Return a failed CompletableFuture with the given exception.
     *
     * @param <T> the type of the future
     * @param ex  the exception to complete exceptionally
     * @return a failed CompletableFuture
     */
    protected <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    /**
     * Confine un traitement sur le thread proprietaire du joueur.
     * <p>
     * Toute interaction Bukkit / zMenu declenchee depuis une chaine asynchrone doit y repasser :
     * les continuations s'executent sur le thread qui complete le future amont — le commonPool
     * via le bridge Redis, l'executor de stockage via une relecture — jamais sur le thread
     * principal. {@code message(...)} en fait partie : il resout des placeholders PlaceholderAPI
     * et peut construire une BossBar ou un Title.
     *
     * @param plugin   le plugin, pour son ordonnanceur
     * @param player   le joueur dont la region possede l'interaction
     * @param runnable le traitement a confiner
     */
    protected void onPlayerThread(AuctionPlugin plugin, Player player, Runnable runnable) {
        var scheduler = plugin.getScheduler();
        if (scheduler.isOwnedByCurrentRegion(player)) {
            runnable.run();
            return;
        }
        scheduler.runAtEntity(player, wrappedTask -> runnable.run());
    }

    /**
     * Arme un watchdog qui prolonge le bail du verrou distribue toutes les {@code bail / 3}
     * pendant toute la duree de la section critique.
     * <p>
     * Rend {@code null} — et n'ordonnance donc RIEN — quand le bridge n'expose aucun bail
     * ({@link AuctionClusterBridge#lockLeaseDuration()} == ZERO) : c'est le cas du bridge
     * mono-serveur et de tout addon compile contre une API anterieure. Le comportement est
     * alors strictement celui d'avant ce correctif.
     *
     * @param plugin      le plugin, pour l'ordonnanceur et le journal
     * @param bridge      le bridge cluster qui detient le verrou
     * @param item        l'annonce verrouillee
     * @param token       le jeton de l'acquisition ; {@code null} ou non acquis = aucun watchdog
     * @param storageType la portee du verrou
     * @return la tache a annuler en fin de section critique, ou {@code null} si rien n'a ete arme
     */
    protected WrappedTask startLeaseWatchdog(AuctionPlugin plugin, AuctionClusterBridge bridge, Item item, LockToken token, StorageType storageType) {
        if (token == null || !token.isAcquired()) return null;

        var lease = bridge.lockLeaseDuration();
        if (lease == null || lease.isZero() || lease.isNegative()) return null;

        var logger = plugin.getLogger();
        // Math.max : sans lui, un bail configure sous 3 ms produirait une periode de 0.
        long periodMs = Math.max(1000L, lease.toMillis() / 3);
        var renewals = new AtomicInteger();
        var taskHolder = new AtomicReference<WrappedTask>();

        var task = plugin.getScheduler().runTimerAsync(() -> {
            if (renewals.incrementAndGet() > MAX_LEASE_RENEWALS) {
                // Sans ce plafond, un thread de section critique mort laisserait le watchdog
                // prolonger le bail indefiniment : l'item serait verrouille pour toujours.
                logger.severe("Lease watchdog for item " + item.getId() + " exceeded " + MAX_LEASE_RENEWALS
                        + " renewals; the critical section looks stuck, stopping renewal.");
                stopLeaseWatchdog(taskHolder.get());
                return;
            }
            bridge.renewLock(item, token, storageType).whenComplete((renewed, error) -> {
                if (error != null) {
                    logger.warning("Failed to renew cluster lock for item " + item.getId() + ": " + error.getMessage());
                } else if (!Boolean.TRUE.equals(renewed)) {
                    logger.severe("Cluster lock lease LOST for item " + item.getId() + ": another node may hold it now.");
                }
            });
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);

        taskHolder.set(task);
        return task;
    }

    /**
     * Annule un watchdog de bail. Tolere {@code null} : les appelants passent directement le
     * resultat de {@link #startLeaseWatchdog}, qui vaut {@code null} en mono-serveur.
     *
     * @param task la tache a annuler, eventuellement {@code null}
     */
    protected void stopLeaseWatchdog(WrappedTask task) {
        if (task != null) task.cancel();
    }

}
