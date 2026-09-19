package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.ZAuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemovePurchasedItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveResult;
import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class RemoveService extends AuctionService implements AuctionRemoveService {

    private final AuctionPlugin plugin;
    private final ZAuctionManager manager;

    public RemoveService(AuctionPlugin plugin, ZAuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public CompletableFuture<RemoveResult> removeListedItem(Player player, Item item) {

        var event = new AuctionPreRemoveListedItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.plugin.getAuctionManager();
        var logger = this.plugin.getLogger();

        if (item.isExpired()) {
            logger.info("Item expired (Remove Listed)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            manager.openMainAuction(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.AVAILABLE && item.getStatus() != ItemStatus.IS_REMOVE_CONFIRM) {
            logger.info("Item not available (Remove Listed)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            manager.openMainAuction(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not available", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        var listedConfig = this.plugin.getConfiguration().getActions().listed();

        // C-083 : LA décision est prise ICI, une seule fois, sur le thread du joueur, et elle
        // est transmise telle quelle à l'implémentation. Avant, ZAuctionManager la réévaluait
        // après plusieurs allers-retours Redis : la destination diffusée au cluster et celle
        // réellement écrite en base pouvaient diverger, ce qui rendait l'item inréclamable
        // pendant toute la durée de vie de la clé Redis.
        //
        // Cette décision reste néanmoins une INTENTION : elle ne survit pas à une remise
        // physique impossible. Le dernier paramètre porte donc le conteneur de compensation
        // réellement utilisé par ZAuctionManager.deliverOrRestore, et c'est lui qui sera
        // diffusé si le lot n'a pas pu être rendu (cf. executeLocalRemovalStep).
        boolean giveItem = listedConfig.giveItem() && item.canReceiveItem(player);
        StorageType destination = giveItem ? StorageType.DELETED : StorageType.EXPIRED;

        return executeRemoval(ItemStatus.IS_BEING_REMOVED, player, item, () -> manager.updateInventory(player), () -> this.manager.removeListedItem(player, item, giveItem), StorageType.LISTED, destination, StorageType.EXPIRED);
    }

    @Override
    public CompletableFuture<RemoveResult> removeSellingItem(Player player, Item item) {
        return removeSellingItem(player, item, true);
    }

    public CompletableFuture<RemoveResult> removeSellingItem(Player player, Item item, boolean updatePlayer) {

        var event = new AuctionPreRemoveListedItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.manager;
        var logger = this.plugin.getLogger();

        var sellingConfig = this.plugin.getConfiguration().getActions().selling();
        if (sellingConfig.freeSpace() && !item.canReceiveItem(player)) {
            if (updatePlayer) message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
            return CompletableFuture.completedFuture(RemoveResult.failure("Not enough space", RemoveFailReason.INSUFFICIENT_SPACE));
        }

        if (item.isExpired()) {
            logger.info("Item expired (Remove Selling)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            if (updatePlayer) manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            logger.info("Item not available (Remove Selling)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            if (updatePlayer) manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not available", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        Runnable onUnavailable = updatePlayer ? () -> manager.updateInventory(player) : () -> { };
        // Dernier paramètre : ZAuctionManager.removeSellingItem se termine par
        // deliverOrRestore(player, item, EXPIRED). Faute de remise, la ligne finit EXPIRED et
        // non DELETED -- c'est cette destination-là qui devra être diffusée.
        return executeRemoval(ItemStatus.IS_BEING_REMOVED, player, item, onUnavailable, () -> manager.removeSellingItem(player, item, updatePlayer), StorageType.LISTED, StorageType.DELETED, StorageType.EXPIRED);
    }
    @Override
    public CompletableFuture<RemoveResult> removeExpiredItem(Player player, Item item) {
        return removeExpiredItem(player, item, true);
    }

    public CompletableFuture<RemoveResult> removeExpiredItem(Player player, Item item, boolean updatePlayer) {

        var event = new AuctionPreRemoveExpiredItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.manager;
        var logger = this.plugin.getLogger();

        var expiredConfig = this.plugin.getConfiguration().getActions().expired();
        if (expiredConfig.freeSpace() && !item.canReceiveItem(player)) {
            if (updatePlayer) message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
            return CompletableFuture.completedFuture(RemoveResult.failure("Not enough space", RemoveFailReason.INSUFFICIENT_SPACE));
        }

        if (item.isExpired()) {
            logger.info("Item expired (Remove Expired)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);
            if (updatePlayer) manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.REMOVED) {
            logger.info("Item not available (Remove Expired), Current status: " + item.getStatus());
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);
            if (updatePlayer) manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not in removed status", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        Runnable onUnavailable = updatePlayer ? () -> manager.updateInventory(player) : () -> { };
        // Compensation vers le conteneur d'ORIGINE : deliverOrRestore(player, item, EXPIRED).
        return executeRemoval(ItemStatus.DELETED, player, item, onUnavailable, () -> manager.removeExpiredItem(player, item, updatePlayer), StorageType.EXPIRED, StorageType.DELETED, StorageType.EXPIRED);
    }
    @Override
    public CompletableFuture<RemoveResult> removePurchasedItem(Player player, Item item) {
        return removePurchasedItem(player, item, true);
    }

    public CompletableFuture<RemoveResult> removePurchasedItem(Player player, Item item, boolean updatePlayer) {

        var event = new AuctionPreRemovePurchasedItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(RemoveResult.failure("Event cancelled", RemoveFailReason.EVENT_CANCELLED));
        }

        var manager = this.manager;
        var logger = this.plugin.getLogger();

        var purchasedConfig = this.plugin.getConfiguration().getActions().purchased();
        if (purchasedConfig.freeSpace() && !item.canReceiveItem(player)) {
            if (updatePlayer) message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
            return CompletableFuture.completedFuture(RemoveResult.failure("Not enough space", RemoveFailReason.INSUFFICIENT_SPACE));
        }

        if (item.isExpired()) {
            logger.info("Item expired (Remove Purchased)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);
            if (updatePlayer) manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item expired", RemoveFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.PURCHASED) {
            logger.info("Item not available (Remove Purchased)");
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);
            if (updatePlayer) manager.updateInventory(player);
            return CompletableFuture.completedFuture(RemoveResult.failure("Item not in purchased status", RemoveFailReason.INVALID_ITEM_STATUS));
        }

        Runnable onUnavailable = updatePlayer ? () -> manager.updateInventory(player) : () -> { };
        // Ici la compensation vise PURCHASED : deliverOrRestore(player, item, PURCHASED).
        return executeRemoval(ItemStatus.DELETED, player, item, onUnavailable, () -> manager.removePurchasedItem(player, item, updatePlayer), StorageType.PURCHASED, StorageType.DELETED, StorageType.PURCHASED);
    }
    /**
     * Executes the removal process with proper locking and cluster notification.
     * This method follows the sequence:
     * 1. Check availability
     * 2. Acquire lock
     * 3. Change status
     * 4. Notify cluster
     * 4 bis. Re-read the authoritative database row under the lock
     * 5. Execute local removal
     * 6. Notify cluster of removal
     * 7. Release lock
     *
     * @param destinationStorageType destination VISÉE lorsque le lot part réellement au joueur
     * @param claimableStorageType   conteneur de compensation réellement écrit par
     *                               {@code ZAuctionManager.deliverOrRestore} quand la remise
     *                               physique n'a pas pu avoir lieu ; c'est lui qui est diffusé
     *                               dans ce cas, jamais la destination visée
     */
    private CompletableFuture<RemoveResult> executeRemoval(ItemStatus targetStatus, Player player, Item item, Runnable onUnavailable, Supplier<CompletableFuture<Boolean>> onLocalRemoval, StorageType storageType, StorageType destinationStorageType, StorageType claimableStorageType) {

        // C-022 : choke point unique des quatre entrees publiques de retrait. Une chaine de
        // retrait engagee apres le debut de onDisable rendrait l'item au joueur pendant que la
        // connexion base se ferme : l'UPDATE serait rejete et le lot serait duplique. Les
        // boutons ignorant le RemoveResult, le refus DOIT etre visible par un message.
        if (this.plugin.isShuttingDown()) {
            message(this.plugin, player, Message.SERVER_SHUTTING_DOWN);
            return CompletableFuture.completedFuture(RemoveResult.failure("Server is shutting down", RemoveFailReason.INTERNAL_ERROR));
        }

        // C-046 : garde d'identité. Un bouton déjà rendu capture dans son setClick la référence
        // Item détenue par le store AU MOMENT DU RENDU, et rien ne redessine ce bouton quand un
        // autre serveur vend/retire l'item (updateListedItems ne cible que ListedItemsButton, et
        // clearPlayersCache ne fait que vider une clé). Si l'instance n'est plus celle du store,
        // c'est un fantôme : son statut mémoire ne prouve plus rien et lui rendre l'item
        // dupliquerait la marchandise. Coût : une lecture O(1).
        if (this.manager.getItem(storageType, item.getId()) != item) {
            this.plugin.getLogger().info("Stale item reference for item " + item.getId() + " in " + storageType + ", removal refused");
            onUnavailable.run();
            return CompletableFuture.completedFuture(RemoveResult.failure("Stale item reference", RemoveFailReason.ITEM_NOT_AVAILABLE));
        }

        var context = new RemovalContext(player, item, targetStatus, storageType, destinationStorageType, claimableStorageType, onUnavailable, onLocalRemoval);
        var performanceConfig = this.plugin.getConfiguration().getPerformance();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();

        return checkAvailabilityStep(context, clusterBridge, performanceConfig)
                .thenCompose(available -> acquireLockStep(context, available, player, clusterBridge, performanceConfig))
                .thenCompose(token -> changeStatusAndNotifyStep(context, token, clusterBridge, performanceConfig))
                .thenCompose(v -> revalidateUnderLockStep(context, clusterBridge, performanceConfig))
                .thenCompose(v -> executeLocalRemovalStep(context, clusterBridge, performanceConfig))
                .thenCompose(v -> unlockAndCompleteStep(context, clusterBridge, performanceConfig))
                .exceptionally(throwable -> handleRemovalException(context, throwable, clusterBridge, logger));
    }

    /**
     * Step 1: Check if the item is available on the cluster.
     */
    private CompletableFuture<Boolean> checkAvailabilityStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        return clusterBridge.checkAvailability(context.item).orTimeout(config.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Step 2: Acquire lock on the item.
     * <p>
     * C-111 : le jeton n'était jusqu'ici enregistré dans le contexte qu'à l'étape 3, donc un
     * dépassement de {@code orTimeout} laissait {@code context.token} à null et la libération
     * compensatoire ne libérait rien — le verrou restait posé jusqu'à expiration de son bail.
     */
    private CompletableFuture<LockToken> acquireLockStep(RemovalContext context, boolean available, Player player, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        if (!available) {
            context.onUnavailable.run();
            context.result = RemoveResult.failure("Item not available", RemoveFailReason.ITEM_NOT_AVAILABLE);
            return failedFuture(new IllegalStateException("Item indisponible"));
        }

        var lockFuture = clusterBridge.lockItem(context.item, player.getUniqueId(), context.storageType);

        lockFuture.whenComplete((lateToken, lockError) -> {
            if (lateToken == null || !lateToken.isAcquired()) return;
            context.token = lateToken;
            if (context.chainAbandoned.get() && context.unlockIssued.compareAndSet(false, true)) {
                clusterBridge.unlockItem(context.item, lateToken, context.storageType).exceptionally(unlockError -> {
                    this.plugin.getLogger().severe("Failed to release late-acquired lock for item " + context.item.getId() + ": " + unlockError.getMessage());
                    return null;
                });
            }
        });

        // copy() : sans elle, orTimeout completerait le futur source et le whenComplete
        // ci-dessus ne verrait jamais le jeton tardif (orTimeout retourne `this`).
        return lockFuture.copy().orTimeout(config.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Step 3: Change item status and notify cluster.
     */
    private CompletableFuture<Void> changeStatusAndNotifyStep(RemovalContext context, LockToken token, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        context.token = token;

        // Un jeton non acquis couvre la contention (noop) comme l'état terminal à portée
        // (unavailable) : dans les deux cas nous ne détenons pas l'item.
        if (token == null || !token.isAcquired()) {
            context.onUnavailable.run();
            context.result = RemoveResult.failure("Lock failed", RemoveFailReason.LOCK_FAILED);
            return failedFuture(new IllegalStateException("Item déjà en cours de traitement"));
        }

        // Change status after acquiring lock to ensure atomicity
        context.item.setStatus(context.targetStatus);
        context.statusChanged = true;

        return clusterBridge.notifyItemStatusChange(context.item, context.oldStatus, context.targetStatus).orTimeout(config.notifyStatusChangeTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Étape 3 bis : relecture autoritaire de la base SOUS VERROU.
     * <p>
     * C'est la protection que le chemin d'achat et le chemin d'expiration appliquent déjà et que
     * le chemin de retrait n'avait pas : les quatre gardes de ce service ne portent que sur
     * {@code item.getStatus()}, un champ mémoire sans colonne dans {@code %prefix%items}, donc
     * invisible des autres serveurs. {@code checkAvailability} et le script de verrouillage
     * laissent passer les états cluster SOLD et REMOVED : sans cette étape, toute référence
     * périmée qui franchit la garde de statut aboutit à {@code giveItem} + {@code updateItem}
     * qui écrase la ligne PURCHASED d'un autre serveur.
     */
    private CompletableFuture<Void> revalidateUnderLockStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        // Mono-serveur : la mémoire EST la vérité et la garde d'identité en tête de
        // executeRemoval suffit. On ne facture pas un aller-retour SQL par retrait — ni x N
        // sur les boutons « tout récupérer » — aux installations sans addon.
        if (!clusterBridge.isDistributed()) {
            return CompletableFuture.completedFuture(null);
        }

        return this.manager.selectItemRow(context.item.getId())
                .orTimeout(config.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(optional -> {

                    // « gone » = la ligne est TERMINALE : détruite (select(int) filtre DELETED)
                    // ou déjà vendue (acheteur posé). Le fantôme local doit alors disparaître.
                    boolean gone = optional.isEmpty()
                            || (context.storageType == StorageType.LISTED && optional.get().buyer_unique_id() != null);

                    // « stale » ajoute le simple décalage de conteneur : la ligne existe encore
                    // mais plus dans le bucket qu'on croit détenir.
                    boolean stale = gone || optional.get().storage_type() != context.storageType;

                    if (stale) {
                        // Interdit à restoreStatusOnError de rediffuser AVAILABLE : cela
                        // remettrait EN VENTE sur tout le cluster (ItemStatusListener ->
                        // updateListedItems(item, true, null)) l'item qu'on constate vendu.
                        context.staleDetected = true;
                        context.result = RemoveResult.failure("Item already processed on another server", RemoveFailReason.ITEM_NOT_AVAILABLE);

                        if (gone) {
                            // On ne purge QUE sur un état terminal. Un décalage de bucket peut
                            // n'être qu'un UPDATE local encore en vol (ExpireService déplace en
                            // mémoire puis écrit en asynchrone) : purger là ferait disparaître
                            // une entrée légitime de l'onglet du joueur jusqu'au redémarrage.
                            this.plugin.getScheduler().runNextTick(w -> {
                                this.manager.removeItem(context.storageType, context.item.getId());
                                this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
                            });
                        }

                        context.onUnavailable.run();
                        return this.<Void>failedFuture(new IllegalStateException("Stale item " + context.item.getId()
                                + " (memory=" + context.storageType + ", db=" + (optional.isEmpty() ? "gone" : optional.get().storage_type()) + ")"));
                    }

                    // Resynchronisation de la date d'expiration sur la vérité base : l'objet
                    // mémoire peut porter un expiredAt divergent après un aller-retour cluster.
                    context.item.setExpiredAt(optional.get().expired_at());
                    return CompletableFuture.<Void>completedFuture(null);
                });
    }

    /**
     * Step 4: Execute the local removal operation and notify cluster.
     * <p>
     * On ne diffuse QUE la destination réellement écrite. La destination pré-calculée à
     * l'entrée n'est qu'une intention : {@code deliverOrRestore} la contredit dès que la remise
     * physique échoue, et diffuser l'intention gèle alors l'item définitivement (cf. ci-dessous).
     */
    private CompletableFuture<Void> executeLocalRemovalStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        return context.onLocalRemoval.get().thenCompose(given -> {
            context.localRemovalCompleted = true;
            // Le booleen vient de ZAuctionManager.deliverOrRestore : false = l'item n'a PAS
            // ete remis (inventaire plein, remise desactivee, stack illisible). Le rapporter
            // tel quel evite de certifier au joueur un retrait qui n'a rien rendu (C-053).
            context.itemGiven = Boolean.TRUE.equals(given);

            // C-083, second volet. `given == false` signifie EXACTEMENT « aucun ItemStack n'a
            // ete remis » (GiveResult.delivered == 0) : deliverOrRestore a alors repositionne la
            // ligne dans le conteneur reclamable via restoreFromDeleted, et son future ne se
            // complete qu'APRES cette ecriture. Base et memoire disent donc EXPIRED/PURCHASED et
            // non DELETED. Diffuser DELETED ici faisait ecrire au bridge un etat terminal,
            // persistant, et terminal pour TOUTES les portees : l'item restait visible et
            // reclamable en base mais checkAvailability refusait chaque tentative -- gele a vie.
            // C'est le cas courant du bouton « tout recuperer », qui boucle sur N lots et sature
            // l'inventaire bien avant le dernier, et celui du joueur deconnecte pendant la remise.
            //
            // Reciproquement, `given == true` prouve qu'au moins un stack est parti : la ligne
            // est bien terminale et la destination visee est la bonne. Le sens du doute est donc
            // toujours le meme -- sans remise on diffuse l'etat NON terminal, qui laisse l'item
            // reclamable et que la relecture autoritaire sous verrou sait rattraper, plutot qu'un
            // etat terminal qui, lui, est irreversible.
            context.effectiveDestinationStorageType = context.itemGiven ? context.destinationStorageType : context.claimableStorageType;

            return clusterBridge.removeItem(context.item, context.storageType, context.effectiveDestinationStorageType).orTimeout(config.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Step 5: Unlock the item and complete the removal.
     * <p>
     * C-061 : le verdict de libération est consommé. Un {@code false} signifie que le jeton
     * présenté n'était plus celui inscrit côté cluster — le verrou a été repris par un autre
     * nœud pendant la section critique, ce qui est le seul signal observable d'un vol de verrou.
     */
    private CompletableFuture<RemoveResult> unlockAndCompleteStep(RemovalContext context, AuctionClusterBridge clusterBridge, PerformanceConfiguration config) {

        var unlock = context.unlockIssued.compareAndSet(false, true)
                ? clusterBridge.releaseLock(context.item, context.token, context.storageType)
                        .orTimeout(config.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                        .thenAccept(released -> {
                            if (!Boolean.TRUE.equals(released)) {
                                this.plugin.getLogger().severe("Cluster lock for item " + context.item.getId()
                                        + " was NOT held by this server at release time; the removal may have raced another node.");
                            }
                        })
                : CompletableFuture.<Void>completedFuture(null);

        return unlock.thenApply(v -> {
            context.result = RemoveResult.success("Item removed successfully", context.itemGiven);
            return context.result;
        });
    }

    /**
     * Handles exceptions during the removal process, including cleanup.
     */
    private RemoveResult handleRemovalException(RemovalContext context, Throwable throwable, AuctionClusterBridge clusterBridge, Logger logger) {

        // Moitie « chaine » de la poignee de main croisee : ecrire AVANT de lire le jeton.
        context.chainAbandoned.set(true);

        // Log appropriately based on exception type
        var stale = StaleItemException.unwrap(throwable);
        if (stale != null) {
            // Course perdue EN BASE : le compare-and-set n'a trouve aucune ligne portant encore
            // l'etat source. Ne JAMAIS restaurer ni rediffuser AVAILABLE ici -- ItemStatusListener
            // (addon) applique le statut en aveugle puis updateListedItems(item, true, null), ce
            // qui remettrait en vente sur tout le cluster un item deja traite ailleurs.
            context.staleDetected = true;
            logger.warning("Removal lost the race for item " + context.item.getId() + " (" + stale.getMessage() + "), converging to database state");
            this.plugin.getScheduler().runNextTick(w -> {
                this.manager.removeItem(context.storageType, context.item.getId());
                this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
            });
            context.result = RemoveResult.failure("Item already handled on another server", RemoveFailReason.ITEM_NOT_AVAILABLE);
        } else if (throwable.getCause() instanceof TimeoutException) {
            logger.warning("Removal operation timed out for item " + context.item.getId());
        } else if (throwable.getCause() instanceof IllegalStateException) {
            logger.warning("Removal unavailable for item " + context.item.getId() + ": " + throwable.getMessage());
        } else {
            logger.severe("Error during removal for item " + context.item.getId() + ": " + throwable.getMessage());
        }

        // Course perdue, quelle qu'en soit l'origine (relecture autoritaire sous verrou ou
        // compare-and-set en base) : le joueur doit savoir que l'annonce vient d'etre traitee
        // ailleurs, sinon il reclique sur une entree qui a disparu de son ecran.
        if (context.staleDetected && context.player != null) {
            var player = context.player;
            onPlayerThread(this.plugin, player, () -> message(this.plugin, player, Message.ITEM_NO_LONGER_AVAILABLE));
        }

        // Release lock if acquired
        releaseLockOnError(context, clusterBridge, logger);

        // Restore status if changed
        restoreStatusOnError(context, clusterBridge);

        // Passe le point de non-retour, l'operation est COMMISE : la ligne en base a bouge et le
        // lot a ete remis ou rendu reclamable. Un incident de transport survenu APRES (diffusion
        // au cluster, deverrouillage) ne doit plus jamais etre rapporte en echec, sinon le joueur
        // retente un retrait deja effectue et le lot est compte a zero dans le retrait en masse.
        if (context.localRemovalCompleted) {
            logger.warning("Removal of item " + context.item.getId()
                    + " is committed but a post-commit step failed: " + throwable.getMessage());
            return RemoveResult.success("Item removed (post-commit transport failure)", context.itemGiven);
        }

        return context.result != null ? context.result : RemoveResult.failure("Internal error", RemoveFailReason.INTERNAL_ERROR);
    }

    /**
     * Releases the lock on error if it was acquired.
     * <p>
     * {@code unlockIssued} garantit qu'un seul acteur libère : sans lui, un unlock retardataire
     * pouvait détruire le verrou fraîchement acquis par un autre appelant (fenêtre 2 de C-008).
     */
    private void releaseLockOnError(RemovalContext context, AuctionClusterBridge clusterBridge, Logger logger) {
        var token = context.token;
        if (token != null && token.isAcquired() && context.unlockIssued.compareAndSet(false, true)) {
            clusterBridge.unlockItem(context.item, token, context.storageType).exceptionally(unlockError -> {
                logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                return null;
            });
        }
    }

    /**
     * Restores the item status on error if it was changed.
     * If the local removal already completed (item given to player, DB updated),
     * we must NOT restore the status as it would create a ghost item on other servers.
     */
    private void restoreStatusOnError(RemovalContext context, AuctionClusterBridge clusterBridge) {
        if (!context.statusChanged || context.localRemovalCompleted) return;

        // Restauration LOCALE systématique : sans elle, un objet non purgé resterait figé en
        // IS_BEING_REMOVED et deviendrait invendable ET inréclamable.
        context.item.setStatus(context.oldStatus);

        // C-009 : mais quand la relecture autoritaire a montré que l'item ne nous appartient
        // plus, on ne DIFFUSE rien. Republier AVAILABLE le remettrait en vente sur tous les
        // nœuds via ItemStatusListener.
        if (context.staleDetected) return;

        clusterBridge.notifyItemStatusChange(context.item, context.targetStatus, context.oldStatus);
    }

    /**
     * Context object holding state for the removal operation.
     * Reduces parameter passing between steps.
     */
    private static class RemovalContext {
        // Le joueur a l'origine du retrait, conserve pour lui dire pourquoi son clic a ete
        // refuse (perte de course). Jamais null sur les quatre entrees publiques.
        final Player player;
        final Item item;
        final ItemStatus oldStatus;
        final ItemStatus targetStatus;
        final StorageType storageType;
        // Destination VISEE, decidee sur le thread du joueur avant le verrou. Intention, pas
        // verite : ne jamais la diffuser telle quelle (cf. executeLocalRemovalStep).
        final StorageType destinationStorageType;
        // Conteneur vers lequel ZAuctionManager.deliverOrRestore compense quand le lot n'a pas
        // pu etre remis : EXPIRED pour les retraits du vendeur, PURCHASED pour la reclamation
        // d'un achat. C'est la destination REELLEMENT ecrite en base dans ce cas.
        final StorageType claimableStorageType;
        final Runnable onUnavailable;
        final Supplier<CompletableFuture<Boolean>> onLocalRemoval;

        // Ecrit par le thread qui complete lockItem, lu par la chaine et par le chemin
        // d'erreur : volatile est obligatoire depuis que la capture est faite hors chaine.
        volatile LockToken token;
        boolean statusChanged;
        boolean localRemovalCompleted;
        // C-053 : « l'item a-t-il reellement ete rendu au joueur ». Ecrit par le thread qui
        // complete la remise locale, relu a la construction du RemoveResult : volatile.
        volatile boolean itemGiven;
        // Destination REELLEMENT appliquee par l'etape de suppression locale : la seule valeur
        // diffusable au cluster. Ecrite par le thread qui complete la remise, relue par la
        // diffusion qui suit : volatile, comme itemGiven.
        volatile StorageType effectiveDestinationStorageType;
        boolean staleDetected;
        RemoveResult result;

        // Poignee de main croisee (cf. PurchaseService) : garantit qu'exactement un acteur
        // libere le verrou, y compris quand il est acquis APRES le orTimeout.
        final AtomicBoolean chainAbandoned = new AtomicBoolean(false);
        final AtomicBoolean unlockIssued = new AtomicBoolean(false);

        RemovalContext(Player player, Item item, ItemStatus targetStatus, StorageType storageType, StorageType destinationStorageType, StorageType claimableStorageType, Runnable onUnavailable, Supplier<CompletableFuture<Boolean>> onLocalRemoval) {
            this.player = player;
            this.item = item;
            this.oldStatus = item.getStatus();
            this.targetStatus = targetStatus;
            this.storageType = storageType;
            this.destinationStorageType = destinationStorageType;
            this.claimableStorageType = claimableStorageType;
            // Valeur de repli : si l'etape de suppression locale n'est jamais atteinte, aucune
            // diffusion de retrait n'a lieu et ce champ n'est pas lu. Il n'est jamais null.
            this.effectiveDestinationStorageType = destinationStorageType;
            this.onUnavailable = onUnavailable;
            this.onLocalRemoval = onLocalRemoval;
            this.statusChanged = false;
        }
    }
}
