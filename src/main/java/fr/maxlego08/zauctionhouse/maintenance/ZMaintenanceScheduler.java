package fr.maxlego08.zauctionhouse.maintenance;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Taches de maintenance periodiques : rearmement des statuts de confirmation oublies et
 * balayage des annonces arrivees a terme.
 * <p>
 * Les deux balayages s'executent hors thread principal et n'y reviennent que pour muter
 * l'etat memoire, via {@code runNextTick}.
 */
public class ZMaintenanceScheduler {

    /**
     * Statuts rearmes par le balayage. IS_BEING_PURCHASED et IS_BEING_REMOVED en sont
     * volontairement absents : passe ce point la section critique est engagee (argent
     * debite, ligne DB en cours de mutation) et restaurer le statut dupliquerait l'item.
     */
    private static final Set<ItemStatus> CONFIRMATION_STATUSES = EnumSet.of(ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.IS_REMOVE_CONFIRM);

    private static final List<StorageType> SCANNED_STORAGES = List.of(StorageType.LISTED, StorageType.EXPIRED, StorageType.PURCHASED);

    private final AuctionPlugin plugin;

    /**
     * Date de PREMIERE OBSERVATION d'un statut de confirmation, par identifiant d'item.
     * <p>
     * L'horodatage est observe par le balayage lui-meme et non pose aux sites d'appel de
     * {@code setStatus} : le filet fonctionne donc quel que soit l'auteur du statut (bouton
     * local, message recu du cluster, commande admin) et ne touche aucune ligne du chemin
     * chaud d'achat.
     */
    private final Map<Integer, Long> confirmationSince = new ConcurrentHashMap<>();

    private WrappedTask confirmationTask;
    private WrappedTask expirationTask;

    /**
     * @param plugin l'instance du plugin, jamais {@code null}
     */
    public ZMaintenanceScheduler(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Demarre les balayages actifs dans la configuration. Idempotent : un appel supplementaire
     * arrete les taches en cours avant de les recreer (utilise par {@code reload()}).
     */
    public void start() {
        stop();

        var configuration = this.plugin.getConfiguration().getMaintenance();
        var scheduler = this.plugin.getScheduler();
        if (configuration == null || scheduler == null) return;

        if (configuration.isConfirmationSweepEnabled()) {
            long interval = configuration.confirmationSweepIntervalSeconds();
            this.confirmationTask = scheduler.runTimerAsync(this::sweepConfirmations, interval, interval, TimeUnit.SECONDS);
            this.plugin.getLogger().info("Confirmation status sweep scheduled every " + interval + "s (timeout " + configuration.confirmationTimeoutSeconds() + "s).");
        }

        if (configuration.isExpirationSweepEnabled()) {
            long interval = configuration.expirationSweepIntervalSeconds();
            this.expirationTask = scheduler.runTimerAsync(this::sweepExpirations, interval, interval, TimeUnit.SECONDS);
            this.plugin.getLogger().info("Expiration sweep scheduled every " + interval + "s (batch " + configuration.expirationSweepBatchSize() + ").");
        }
    }

    /**
     * Arrete les balayages et oublie les horodatages observes.
     */
    public void stop() {
        if (this.confirmationTask != null) {
            this.confirmationTask.cancel();
            this.confirmationTask = null;
        }
        if (this.expirationTask != null) {
            this.expirationTask.cancel();
            this.expirationTask = null;
        }
        this.confirmationSince.clear();
    }

    /**
     * Rearme tout statut de confirmation observe depuis plus de
     * {@code maintenance.confirmation-timeout-seconds}.
     * <p>
     * Le rearmement est diffuse au cluster UNIQUEMENT pour les items detenus en LISTED :
     * pour un item detenu en EXPIRED ou PURCHASED, on se contente de reparer l'etat local
     * vers le statut coherent avec son conteneur (la barriere de cycle de vie du listener
     * refuserait de toute facon un statut du cycle LISTED sur un tel item).
     */
    private void sweepConfirmations() {
        if (this.plugin.isShuttingDown()) return;

        var configuration = this.plugin.getConfiguration().getMaintenance();
        if (configuration == null || !configuration.isConfirmationSweepEnabled()) return;

        long timeoutMs = configuration.confirmationTimeoutSeconds() * 1000L;
        long now = System.currentTimeMillis();
        var manager = this.plugin.getAuctionManager();

        List<PendingRelease> toRelease = new ArrayList<>();
        Set<Integer> stillPending = new HashSet<>();

        for (StorageType storageType : SCANNED_STORAGES) {
            for (Item item : manager.getItems(storageType)) {
                if (!CONFIRMATION_STATUSES.contains(item.getStatus())) continue;

                stillPending.add(item.getId());
                Long since = this.confirmationSince.putIfAbsent(item.getId(), now);
                if (since != null && now - since >= timeoutMs) {
                    toRelease.add(new PendingRelease(storageType, item));
                }
            }
        }

        // Les items qui ne portent plus de statut de confirmation sortent de la map : elle
        // reste bornee par le nombre de confirmations reellement ouvertes.
        this.confirmationSince.keySet().retainAll(stillPending);
        if (toRelease.isEmpty()) return;

        this.plugin.getScheduler().runNextTick(w -> {
            boolean listedChanged = false;

            for (PendingRelease pending : toRelease) {
                var item = pending.item();
                var status = item.getStatus();
                // Le statut a pu bouger entre le balayage et le tick : ne rien forcer.
                if (!CONFIRMATION_STATUSES.contains(status)) continue;

                this.confirmationSince.remove(item.getId());
                var target = targetStatusFor(pending.storageType());
                item.setStatus(target);

                this.plugin.getLogger().warning("Item " + item.getId() + " stayed in " + status + " for more than "
                        + configuration.confirmationTimeoutSeconds() + "s, releasing it to " + target + ".");

                if (pending.storageType() == StorageType.LISTED) {
                    listedChanged = true;
                    this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, status, target)
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().warning("Failed to broadcast the released confirmation status of item "
                                        + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                }
            }

            if (!listedChanged) return;

            // item.setStatus(...) n'invalide pas le cache trie : sans ce rebuild explicite,
            // l'item rearme resterait absent de la liste affichee.
            manager.rebuildSortedItemsCache();
            manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH, PlayerCacheKey.ITEMS_SELLING);
        });
    }

    /**
     * Statut de repos coherent avec le conteneur qui detient l'item.
     *
     * @param storageType le conteneur qui detient l'item
     * @return le statut a reposer
     */
    private static ItemStatus targetStatusFor(StorageType storageType) {
        return switch (storageType) {
            case EXPIRED -> ItemStatus.REMOVED;
            case PURCHASED -> ItemStatus.PURCHASED;
            default -> ItemStatus.AVAILABLE;
        };
    }

    /**
     * Balaye les annonces actives arrivees a terme et les route vers le service d'expiration.
     * <p>
     * Sans cette tache, l'expiration n'est declenchee que par {@code ZAuctionManager.getItemIds},
     * que la liste principale de l'hotel des ventes n'emprunte jamais (elle lit
     * {@code SortedItemsCache}) : une annonce peut rester en vente indefiniment apres sa date
     * limite, jusqu'a ce qu'un joueur ouvre par hasard un onglet qui itere le store.
     * <p>
     * <b>Surete en cluster</b> : en mode distribue,
     * {@code ExpireService.processExpiredItems} route chaque item vers le chemin
     * {@code expireListedItemClustered}, qui acquiert le verrou distribue, relit la ligne
     * autoritaire en base et abandonne si un autre noeud detient deja le verrou. L'exclusion
     * est donc portee par item, ce qui est strictement plus fin qu'un noeud elu : deux serveurs
     * executant ce balayage en meme temps ne peuvent pas expirer deux fois le meme item, et un
     * item en cours d'achat n'est pas expire sous les pieds de l'acheteur.
     * <p>
     * Le lot est borne : un arriere important est resorbe progressivement sur plusieurs
     * passages plutot qu'en une rafale de verrouillages distribues.
     */
    private void sweepExpirations() {
        if (this.plugin.isShuttingDown()) return;

        var configuration = this.plugin.getConfiguration().getMaintenance();
        if (configuration == null || !configuration.isExpirationSweepEnabled()) return;

        int batchSize = configuration.expirationSweepBatchSize();
        var manager = this.plugin.getAuctionManager();

        List<Item> expired = new ArrayList<>(batchSize);
        for (Item item : manager.getItems(StorageType.LISTED)) {
            if (!item.isExpired()) continue;

            // On ne touche QUE les annonces au repos. Un item en IS_*_CONFIRM ou IS_BEING_*
            // appartient a une chaine en cours (confirmation ouverte, achat ou retrait
            // engage) : c'est a cette chaine de conclure, et le balayage de confirmation
            // se charge de la debloquer si elle ne revient jamais.
            if (item.getStatus() != ItemStatus.AVAILABLE) continue;

            expired.add(item);
            if (expired.size() >= batchSize) break;
        }

        if (expired.isEmpty()) return;

        this.plugin.getLogger().info("Expiration sweep: processing " + expired.size() + " expired listing(s).");
        this.plugin.getScheduler().runNextTick(w -> manager.getExpireService().processExpiredItems(expired, StorageType.LISTED));
    }

    /**
     * Couple (conteneur, item) retenu par le balayage de confirmation entre l'observation
     * asynchrone et le tick de mutation.
     *
     * @param storageType le conteneur qui detient l'item
     * @param item        l'item a rearmer
     */
    private record PendingRelease(StorageType storageType, Item item) {
    }
}
