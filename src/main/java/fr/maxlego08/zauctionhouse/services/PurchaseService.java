package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.purchase.AuctionPrePurchaseItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseResult;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.tax.ZPurchaseCharge;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class PurchaseService extends AuctionService implements AuctionPurchaseService {

    private final AuctionPlugin plugin;

    public PurchaseService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<PurchaseResult> purchaseItem(Player player, Item item) {

        // Les chaines d'achat tournent sur des pools que le plugin ne controle pas (commonPool,
        // pool Jedis) : elles survivent a onDisable et debiteraient l'acheteur pendant que la
        // connexion base se ferme. ConfirmPurchaseButton ignorant le PurchaseResult, le refus
        // DOIT etre visible par un message.
        if (this.plugin.isShuttingDown()) {
            message(this.plugin, player, Message.SERVER_SHUTTING_DOWN);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Server is shutting down", PurchaseFailReason.INTERNAL_ERROR));
        }

        var event = new AuctionPrePurchaseItemEvent(item, player);
        if (!event.callEvent()) {
            return CompletableFuture.completedFuture(PurchaseResult.failure("Event cancelled", PurchaseFailReason.EVENT_CANCELLED));
        }

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();
        var auctionEconomy = item.getAuctionEconomy();

        // Un seul et unique calcul du montant, avec les ItemStack REELS de l'annonce.
        // requiredBalance est desormais, par construction, exactement ce que ZAuctionManager
        // prelevera : le has() ne peut plus valider un montant inferieur au debit.
        final BigDecimal requiredBalance;
        try {
            requiredBalance = ZPurchaseCharge.resolve(player, item, auctionEconomy).buyerPays();
        } catch (IllegalStateException exception) {
            logger.severe(exception.getMessage());
            return CompletableFuture.completedFuture(
                    PurchaseResult.failure("Invalid tax configuration", PurchaseFailReason.INTERNAL_ERROR));
        }

        var configuration = this.plugin.getConfiguration().getActions().purchased();
        if (configuration.giveItem() && configuration.freeSpace() && !item.canReceiveItem(player)) {
            message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Not enough space", PurchaseFailReason.INSUFFICIENT_SPACE));
        }

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item expired", PurchaseFailReason.ITEM_EXPIRED));
        }

        if (item.getStatus() != ItemStatus.IS_PURCHASE_CONFIRM) {
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(PurchaseResult.failure("Item not in purchase state", PurchaseFailReason.ITEM_NOT_IN_PURCHASE_STATE));
        }

        // Store the lock token for cleanup on exception
        final AtomicReference<LockToken> tokenHolder = new AtomicReference<>(null);
        final AtomicReference<PurchaseResult> resultHolder = new AtomicReference<>(null);
        final AtomicReference<ItemStatus> previousStatusHolder = new AtomicReference<>(item.getStatus());

        // Poignee de main croisee avec le .exceptionally terminal : la chaine ecrit
        // chainAbandoned PUIS lit tokenHolder ; le whenComplete de lockItem ecrit tokenHolder
        // PUIS lit chainAbandoned. Exactement un des deux cotes declenche donc la liberation,
        // meme quand le jeton arrive APRES le orTimeout. unlockIssued garantit qu'un seul
        // unlock part, tous chemins confondus.
        final AtomicBoolean chainAbandoned = new AtomicBoolean(false);
        final AtomicBoolean unlockIssued = new AtomicBoolean(false);

        // C-076 : marqueur de PROPRIETE du changement de statut. `item.getStatus()` est un etat
        // PARTAGE : deux chaines concurrentes (deux joueurs, ou le meme joueur sur deux serveurs,
        // le statut de confirmation etant diffuse au cluster) le lisent toutes les deux a
        // IS_BEING_PURCHASED alors qu'une seule l'a pose. Sans ce drapeau, la chaine PERDANTE
        // « restaure » le statut de la gagnante et rediffuse IS_PURCHASE_CONFIRM a tout le
        // cluster pendant que la gagnante debite l'acheteur ; ConfirmHelper voit alors le statut
        // qu'il attend et re-annonce l'annonce disponible. Meme mecanique que
        // RemovalContext.statusChanged cote RemoveService.
        final AtomicBoolean statusChangedByUs = new AtomicBoolean(false);

        // C-035 : statut TERMINAL lu sous verrou dans la base autoritaire. Arme uniquement quand
        // la revalidation conclut que l'annonce n'est plus achetable ET que la base est formelle.
        // Il remplace alors toute restauration de statut de confirmation : on converge vers la
        // verite base au lieu de re-annoncer disponible une annonce dont on vient d'etablir
        // qu'elle est vendue.
        final AtomicReference<ItemStatus> terminalStatusHolder = new AtomicReference<>(null);

        // Watchdog de bail : arme au point d'engagement, annule sur TOUS les chemins de sortie.
        final AtomicReference<WrappedTask> watchdogHolder = new AtomicReference<>();

        // Load timeout configuration
        var performanceConfig = this.plugin.getConfiguration().getPerformance();

        // 2. Vérifier si l'item est lock (with timeout)
        return clusterBridge.checkAvailability(item)
                .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(available -> {

                    if (!available) {
                        onPlayerThread(this.plugin, player, () -> inventoryManager.updateInventory(player));
                        resultHolder.set(PurchaseResult.failure("Item not available", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                        return this.<LockToken>failedFuture(new IllegalStateException("Item indisponible"));
                    }

                    var lockFuture = clusterBridge.lockItem(item, player.getUniqueId(), StorageType.LISTED);

                    lockFuture.whenComplete((lateToken, lockError) -> {
                        if (lateToken == null || !lateToken.isAcquired()) return;
                        tokenHolder.set(lateToken);
                        if (chainAbandoned.get() && unlockIssued.compareAndSet(false, true)) {
                            // Verrou obtenu APRES l'abandon de la chaine : plus personne en aval
                            // ne le liberera, il resterait pose jusqu'au TTL (30 s par defaut) et
                            // pour toujours en mono-serveur.
                            clusterBridge.unlockItem(item, lateToken, StorageType.LISTED).exceptionally(unlockError -> {
                                logger.severe("Failed to release late-acquired lock for item " + item.getId() + ": " + unlockError.getMessage());
                                return null;
                            });
                        }
                    });

                    // copy() est INDISPENSABLE : orTimeout(...) retourne `this`, il completerait
                    // donc exceptionnellement le futur SOURCE et le whenComplete ci-dessus ne
                    // verrait jamais le jeton tardif.
                    return lockFuture.copy().orTimeout(performanceConfig.lockItemTimeoutMs(), TimeUnit.MILLISECONDS);

                }).thenCompose(token -> {
                    // Store token for exception cleanup
                    tokenHolder.set(token);

                    // Un jeton non acquis couvre les DEUX echecs : contention (noop) et etat
                    // terminal (unavailable). La comparaison litterale sur noop() laissait passer
                    // le second et l'achat repartait sans verrou.
                    if (!token.isAcquired()) {
                        onPlayerThread(this.plugin, player, () -> inventoryManager.updateInventory(player));
                        resultHolder.set(PurchaseResult.failure("Lock failed", PurchaseFailReason.LOCK_FAILED));
                        return this.<Boolean>failedFuture(new IllegalStateException("Item déjà en cours d'achat"));
                    }

                    // Set status AFTER acquiring lock to ensure atomicity.
                    // Le drapeau est arme AVANT la mutation, jamais apres : une restauration
                    // manquante figerait l'annonce en IS_BEING_PURCHASED sur tout le cluster,
                    // ce qui est pire qu'une restauration de trop. Le test sur
                    // IS_BEING_PURCHASED en aval couvre le cas ou la mutation n'aurait pas eu
                    // lieu.
                    statusChangedByUs.set(true);
                    item.setStatus(ItemStatus.IS_BEING_PURCHASED);
                    return clusterBridge.notifyItemStatusChange(item, previousStatusHolder.get(), ItemStatus.IS_BEING_PURCHASED)
                            .orTimeout(performanceConfig.notifyStatusChangeTimeoutMs(), TimeUnit.MILLISECONDS)
                            // Relecture autoritaire SOUS VERROU avant de facturer quoi que ce soit.
                            // L'exemplaire memoire peut etre perime d'un serveur a l'autre ; en
                            // particulier, un achat abouti sur un autre noeud mais tombe avant sa
                            // diffusion SOLD laisse un verrou fantome que la reprise de verrou nous
                            // laisserait reacquerir.
                            .thenCompose(v -> this.plugin.getStorageManager().selectItemState(item.getId())
                                    .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS))
                            .thenCompose(lookup -> {
                                // Garde autoritaire SOUS VERROU : la ligne doit ENCORE etre LISTED.
                                //
                                // ItemRepository.select(int) ne filtre que DELETED, et
                                // ItemLoaderUtils.createAuctionItem traduit storage_type -> ItemStatus
                                // (LISTED -> AVAILABLE, EXPIRED -> REMOVED, PURCHASED -> PURCHASED) :
                                // tester le statut de l'objet relu equivaut donc exactement a tester
                                // storage_type = 'LISTED' en base, sans SQL brut ni nouvelle methode
                                // sur l'interface publiee StorageManager.
                                //
                                // Sans le terme sur le statut, un item deja bascule en EXPIRED par un
                                // autre noeud (destination PAR DEFAUT d'un retrait, ou tache
                                // d'expiration planifiee) revient ici avec buyer_unique_id a null et
                                // franchissait la garde : l'acheteur payait un item que le vendeur
                                // etait en train de recuperer.
                                var state = lookup.state();
                                var dbItem = lookup.item();
                                boolean stillListed = lookup.isFound() && dbItem != null
                                        && dbItem.getBuyerUniqueId() == null
                                        && dbItem.getStatus() == ItemStatus.AVAILABLE;

                                if (!stillListed) {
                                    // Converger vers la base : notre exemplaire local est un fantome,
                                    // le laisser dans le store le rendrait cliquable a nouveau. On ne
                                    // purge QUE lorsque la base est formelle — ligne disparue (GONE)
                                    // ou relue dans un etat terminal. Une ligne simplement illisible
                                    // (UNAVAILABLE) refuse l'achat sans faire disparaitre un
                                    // exemplaire memoire valide.
                                    boolean purgeLocalCopy = state != StorageManager.LookupState.UNAVAILABLE;

                                    // C-035 : quand la base est FORMELLE, l'annonce ne doit plus jamais
                                    // repasser par un statut du cycle LISTED. On arme le statut terminal
                                    // ICI, avant tout retour de la chaine, pour que le .exceptionally
                                    // terminal ne restaure pas IS_PURCHASE_CONFIRM par-dessus. Une ligne
                                    // seulement illisible (UNAVAILABLE) n'arme RIEN : elle peut etre
                                    // parfaitement listee et simplement non reconstructible par ce noeud
                                    // (economie inconnue, contenu illisible), la restauration nominale
                                    // reste alors la bonne conclusion.
                                    if (purgeLocalCopy) {
                                        terminalStatusHolder.set(resolveTerminalStatus(dbItem));
                                    }

                                    onPlayerThread(this.plugin, player, () -> {
                                        if (purgeLocalCopy) {
                                            // ITEM_SHOW est l'UNIQUE reference que ConfirmHelper relit a la
                                            // fermeture de la confirmation ou au clic Retour. La laisser en
                                            // place laisserait ConfirmHelper retrouver l'annonce, la juger
                                            // dans l'etat attendu, la repasser en AVAILABLE, la REDIFFUSER
                                            // au cluster et la reafficher : le serveur re-annoncerait
                                            // disponible une annonce dont il vient d'etablir en base
                                            // qu'elle est vendue. La purge precede removeItem, qui marque
                                            // l'exemplaire memoire DELETED.
                                            auctionManager.getCache(player).remove(PlayerCacheKey.ITEM_SHOW);
                                            auctionManager.removeItem(StorageType.LISTED, item.getId());
                                            auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
                                        }
                                        inventoryManager.updateInventory(player);
                                        // Sans ce message, l'acheteur voit simplement l'annonce disparaitre de son
                                        // ecran sans savoir pourquoi et reclique aussitot : le refus doit etre dit.
                                        message(this.plugin, player, Message.ITEM_NO_LONGER_AVAILABLE);
                                    });
                                    resultHolder.set(PurchaseResult.failure("Item already sold", PurchaseFailReason.ITEM_NOT_AVAILABLE));
                                    return this.<Boolean>failedFuture(new IllegalStateException("Item " + item.getId()
                                            + " is no longer listed on the authoritative database row (db=" + state
                                            + (dbItem == null ? "" : "/" + dbItem.getStatus()) + ")"));
                                }

                                // Resynchroniser l'expiration sur la verite base avant de facturer :
                                // un autre noeud a pu la prolonger ou la raccourcir.
                                item.setExpiredAt(dbItem.getExpiredAt());
                                return auctionEconomy.has(player.getUniqueId(), requiredBalance);
                            });

                }).thenCompose(hasMoney -> {

                    var token = tokenHolder.get();

                    if (hasMoney) {

                        // Le bail n'est jamais prolonge tout seul et la section critique contient des
                        // appels SYNCHRONES BLOQUANTS qu'aucun orTimeout ne borne. Le watchdog tient
                        // le bail pendant toute sa duree ; il ne s'arme pas en mono-serveur.
                        watchdogHolder.set(startLeaseWatchdog(this.plugin, clusterBridge, item, token, StorageType.LISTED));

                        return clusterBridge.isHeldBy(item, token, StorageType.LISTED)
                                .orTimeout(performanceConfig.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                                .thenCompose(stillHeld -> {
                                    if (!Boolean.TRUE.equals(stillHeld)) {
                                        // Bail perdu AVANT tout debit : abandon gratuit, rien n'a bouge.
                                        resultHolder.set(PurchaseResult.failure("Lock lease lost", PurchaseFailReason.LOCK_FAILED));
                                        return this.<Void>failedFuture(new IllegalStateException("Lease expired before commit"));
                                    }
                                    // AUCUN orTimeout ici, JAMAIS : purchaseItem ne rend que son
                                    // updateFuture, l'argent est deja debite et l'item deja remis
                                    // quand il revient. Un timeout rapporterait un achat commis en
                                    // echec et declencherait en prime la restauration de statut.
                                    return auctionManager.purchaseItem(player, item);
                                })
                                .thenCompose(v -> clusterBridge.notifyItemBought(player, item)
                                        .orTimeout(performanceConfig.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS))
                                .thenCompose(v -> unlockIssued.compareAndSet(false, true)
                                        ? clusterBridge.releaseLock(item, token, StorageType.LISTED)
                                        .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                                        .thenAccept(released -> warnIfLockLost(logger, item, released))
                                        : CompletableFuture.<Void>completedFuture(null))
                                .thenApply(v -> {
                                    resultHolder.set(PurchaseResult.success("Purchase successful", true));
                                    return resultHolder.get();
                                });
                    }

                    // Insufficient funds - unlock, restore status, and notify
                    onPlayerThread(this.plugin, player, () -> message(this.plugin, player, Message.NOT_ENOUGH_MONEY));
                    resultHolder.set(PurchaseResult.failure("Insufficient funds", PurchaseFailReason.INSUFFICIENT_FUNDS));
                    var previousStatus = previousStatusHolder.get();
                    item.setStatus(previousStatus);
                    return clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)
                            .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                            .thenCompose(v -> unlockIssued.compareAndSet(false, true)
                                    ? clusterBridge.releaseLock(item, token, StorageType.LISTED)
                                    .orTimeout(performanceConfig.unlockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                                    .thenAccept(released -> warnIfLockLost(logger, item, released))
                                    : CompletableFuture.<Void>completedFuture(null))
                            .thenApply(v -> resultHolder.get());

                }).exceptionally(e -> {

                    // Signale l'abandon AVANT de lire le jeton : c'est la moitie « chaine » de la
                    // poignee de main croisee avec le whenComplete de lockItem.
                    chainAbandoned.set(true);

                    // Handle timeout exceptions specifically
                    if (e.getCause() instanceof TimeoutException) {
                        logger.warning("Purchase operation timed out for item " + item.getId());
                    } else if (e.getCause() instanceof IllegalStateException) {
                        logger.warning("Purchase unavailable for item " + item.getId() + ": " + e.getMessage());
                    } else {
                        logger.severe("Error during purchase for item " + item.getId() + ": " + e.getMessage());
                    }

                    // Ensure lock is released on any exception
                    var token = tokenHolder.get();
                    if (token != null && token.isAcquired() && unlockIssued.compareAndSet(false, true)) {
                        clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(unlockError -> {
                            logger.severe("Failed to unlock item after error: " + unlockError.getMessage());
                            return null;
                        });
                    }

                    // --- Convergence de statut : trois cas EXCLUSIFS, un seul point de decision ---
                    var terminalStatus = terminalStatusHolder.get();
                    if (!statusChangedByUs.get()) {

                        // C-076 : cette chaine-ci n'a JAMAIS pose IS_BEING_PURCHASED (echec de
                        // disponibilite, ou verrou perdu au profit d'une autre chaine). Le statut
                        // qu'on lirait ici appartient a la chaine GAGNANTE : le restaurer
                        // rediffuserait IS_PURCHASE_CONFIRM au cluster pendant qu'elle debite
                        // l'acheteur, et ConfirmHelper re-annoncerait ensuite l'annonce
                        // disponible. On ne touche a rien : c'est a la gagnante de conclure.

                    } else if (terminalStatus != null) {

                        // C-035 : la relecture SOUS VERROU a etabli que l'annonce n'est plus
                        // achetable. On ne restaure surtout pas le statut de confirmation ; on
                        // corrige au contraire le IS_BEING_PURCHASED deja diffuse plus haut,
                        // faute de quoi les copies distantes y resteraient bloquees.
                        //
                        // L'ancien statut annonce est IS_BEING_PURCHASED et non le statut local
                        // courant : c'est exactement ce que detiennent les noeuds que NOUS avons
                        // notifies, et le compare-and-swap de l'addon Redis rejette tout message
                        // dont l'ancien statut ne correspond pas — la correction se limite donc
                        // aux copies effectivement polluees. En local, on ne rabaisse jamais un
                        // statut deja terminal pose entre-temps par removeItem (DELETED).
                        if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {
                            item.setStatus(terminalStatus);
                        }
                        clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, terminalStatus)
                                .exceptionally(restoreError -> {
                                    logger.severe("Failed to converge item status for item " + item.getId() + ": " + restoreError.getMessage());
                                    return null;
                                });

                    } else if (item.getStatus() == ItemStatus.IS_BEING_PURCHASED) {

                        // Echec sans verdict de la base (transport, fonds, bail perdu) : notre
                        // mutation est annulable, l'annonce revient a l'etat d'avant l'achat.
                        var previousStatus = previousStatusHolder.get();
                        item.setStatus(previousStatus);
                        clusterBridge.notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, previousStatus)
                                .exceptionally(restoreError -> {
                                    logger.severe("Failed to restore item status for item " + item.getId() + ": " + restoreError.getMessage());
                                    return null;
                                });
                    }

                    // Return the previously set result or a generic error
                    var result = resultHolder.get();
                    return result != null ? result : PurchaseResult.failure("Internal error", PurchaseFailReason.INTERNAL_ERROR);

                    // Dernier maillon de la chaine : seul point traverse par TOUS les chemins,
                    // nominaux comme exceptionnels. Le watchdog de bail y est annule sans condition.
                }).whenComplete((result, throwable) -> stopLeaseWatchdog(watchdogHolder.getAndSet(null)));
    }

    /**
     * Traduit la ligne autoritaire relue sous verrou en statut TERMINAL a poser et a diffuser
     * quand l'annonce n'est plus achetable.
     * <p>
     * Le contrat de cette methode est de ne JAMAIS rendre un statut du cycle LISTED
     * ({@code AVAILABLE}, {@code IS_*_CONFIRM}, {@code IS_BEING_*}) : elle n'est appelee que sur
     * le chemin ou la base a formellement tranche, et rendre un tel statut re-annoncerait
     * l'annonce comme disponible.
     * <p>
     * {@code ItemLoaderUtils} traduit deja {@code storage_type} en statut (LISTED -&gt; AVAILABLE,
     * EXPIRED -&gt; REMOVED, PURCHASED -&gt; PURCHASED, DELETED -&gt; DELETED). Un {@code AVAILABLE}
     * ne peut donc arriver ici qu'avec un acheteur deja inscrit sur une ligne encore LISTED
     * (commit partiel d'un autre noeud) : l'annonce est vendue, pas disponible.
     *
     * @param dbItem l'exemplaire reconstruit depuis la base, {@code null} si la ligne a disparu
     * @return le statut terminal a poser localement et a diffuser au cluster
     */
    private ItemStatus resolveTerminalStatus(Item dbItem) {
        if (dbItem == null) return ItemStatus.DELETED; // ligne disparue : LookupState.GONE

        var databaseStatus = dbItem.getStatus();
        if (databaseStatus == ItemStatus.REMOVED || databaseStatus == ItemStatus.PURCHASED
                || databaseStatus == ItemStatus.DELETED) {
            return databaseStatus;
        }
        return ItemStatus.PURCHASED;
    }

    /**
     * Journalise en SEVERE la perte d'un verrou : le script de deverrouillage a repondu que le
     * jeton presente n'etait plus celui inscrit dans le cluster. C'est le seul signal
     * exploitable d'un vol de verrou (bail expire pendant la section critique, ou reprise par
     * un autre noeud).
     *
     * @param logger   le journal du plugin
     * @param item     l'annonce dont le verrou vient d'etre relache
     * @param released le verdict rendu par {@code releaseLock}
     */
    private void warnIfLockLost(Logger logger, Item item, Boolean released) {
        if (!Boolean.TRUE.equals(released)) {
            logger.severe("Cluster lock for item " + item.getId() + " was NOT held by this server at release time."
                    + " Another node may have taken it over: check lock-ttl-seconds against the duration of the critical section.");
        }
    }
}
