package fr.maxlego08.zauctionhouse.api.cluster;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Defines the contract used to synchronize auction house actions across multiple server instances.
 * Implementations are responsible for coordinating locks and broadcasting state changes so that
 * concurrent purchases remain consistent in clustered environments.
 */
public interface AuctionClusterBridge {

    /**
     * Checks whether the item can still be purchased across the cluster, preventing stale views.
     * <p>
     * Predicat d'AFFICHAGE, NON ENGAGEANT : entre sa resolution et l'acquisition du verrou il
     * subsiste une fenetre TOCTOU. Tout chemin mutant doit passer par
     * {@link #checkAndLock(Item, UUID, StorageType)} — ou, a defaut, revalider en base sous
     * verrou — plutot que de se fier a ce seul predicat.
     *
     * @param item item being evaluated
     * @return future resolving to {@code true} if the item is still available
     */
    CompletableFuture<Boolean> checkAvailability(Item item);

    /**
     * Attempts to lock the item for the given buyer in the specified storage context, preventing
     * other servers from selling it simultaneously.
     * <p>
     * CONTRAT (seul point de variation entre implementations, a respecter imperativement) :
     * <ul>
     *   <li>acquisition reussie : future complete avec un jeton tel que
     *       {@link LockToken#isAcquired()} vaut {@code true} ;</li>
     *   <li>acquisition REFUSEE (contention, ou item dans un etat terminal) : future complete
     *       NORMALEMENT avec {@link LockToken#noop()} — jamais un future en erreur ;</li>
     *   <li>future en ERREUR : reserve aux pannes de transport (Redis injoignable, timeout).</li>
     * </ul>
     * Un appelant qui confond les deux derniers cas transforme une contention banale en
     * INTERNAL_ERROR et interrompt les chaines de retrait de masse.
     *
     * @param item        item to lock
     * @param buyerId     UUID of the buyer
     * @param storageType storage bucket the item resides in
     * @return future containing a lock token to be used when unlocking
     */
    CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType);

    /**
     * Releases a previously acquired lock, allowing other nodes to act on the item again.
     *
     * @param item        item to unlock
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future completing once the unlock has been propagated
     */
    CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType);

    /**
     * Notifies the cluster that a player bought an item so caches and live views can be updated.
     *
     * @param player buyer who completed the purchase
     * @param item   item that was purchased
     * @return future completing after the notification is processed
     */
    CompletableFuture<Void> notifyItemBought(Player player, Item item);

    /**
     * Notifies the cluster that a new item has been listed for sale.
     *
     * @param item item that was listed
     * @return future completing after the notification is processed
     */
    CompletableFuture<Void> notifyItemListed(Item item);

    /**
     * Broadcasts a status change for an item so other nodes can mirror the new state.
     *
     * @param item      item whose status changed
     * @param oldStatus previous status value
     * @param newStatus new status value
     * @return future completing after the notification is processed
     */
    CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus);

    /**
     * Removes an item from the specified storage type across the cluster.
     *
     * @param item        item to remove
     * @param storageType storage bucket the item currently resides in
     * @return future completing after the deletion is processed
     */
    CompletableFuture<Void> removeItem(Item item, StorageType storageType);

    /**
     * Removes an item from the specified storage type across the cluster,
     * including the destination storage type so other nodes know the final state.
     *
     * @param item                 item to remove
     * @param sourceStorageType    storage bucket the item was removed from
     * @param destinationStorageType storage bucket the item was moved to (e.g. DELETED or EXPIRED), or null if unknown
     * @return future completing after the deletion is processed
     */
    default CompletableFuture<Void> removeItem(Item item, StorageType sourceStorageType, StorageType destinationStorageType) {
        return removeItem(item, sourceStorageType);
    }

    /**
     * Variante de {@link #checkAvailability(Item)} portant la PORTEE de l'operation.
     * <p>
     * La disponibilite n'est pas absolue : un item vendu (etat SOLD) doit etre refuse a un
     * nouvel ACHAT (portee {@code LISTED}) mais rester verrouillable pour la RECLAMATION par
     * son acheteur (portee {@code PURCHASED}). De meme un item expire (etat REMOVED) doit
     * etre refuse a l'achat mais reclamable par son vendeur (portee {@code EXPIRED}).
     * <p>
     * L'implementation par defaut delegue a la variante sans portee, qui reste le predicat
     * permissif historique : seule la destruction definitive y bloque.
     *
     * @param item        item being evaluated
     * @param storageType portee de l'operation envisagee, {@code null} pour un simple affichage
     * @return future resolving to {@code true} if the item can still be locked for that scope
     */
    default CompletableFuture<Boolean> checkAvailability(Item item, StorageType storageType) {
        return checkAvailability(item);
    }

    /**
     * Verifie la disponibilite ET acquiert le verrou en UNE SEULE operation atomique.
     * <p>
     * Supprime la fenetre TOCTOU entre {@link #checkAvailability(Item, StorageType)} et
     * {@link #lockItem(Item, UUID, StorageType)}, ainsi qu'un aller-retour reseau et un emprunt
     * de connexion sur chacun des quatre chemins chauds.
     * <p>
     * Trois issues, distinguees par le jeton rendu :
     * <ul>
     *   <li>{@link LockToken#isAcquired()} : verrou obtenu ;</li>
     *   <li>{@link LockToken#isUnavailable()} : item dans un etat terminal pour cette portee,
     *       l'appelant doit remonter ITEM_NOT_AVAILABLE ;</li>
     *   <li>{@link LockToken#isNoop()} : contention, l'appelant doit remonter LOCK_FAILED.</li>
     * </ul>
     * L'implementation par defaut enchaine les deux appels historiques : elle est correcte pour
     * tout bridge respectant la convention documentee sur
     * {@link #lockItem(Item, UUID, StorageType)}.
     *
     * @param item        item to evaluate and lock
     * @param lockerId    identite du demandeur (UUID du joueur, ou de l'instance de serveur)
     * @param storageType portee de l'operation envisagee
     * @return future containing the acquired token, or the {@code noop} / {@code unavailable} sentinel
     */
    default CompletableFuture<LockToken> checkAndLock(Item item, UUID lockerId, StorageType storageType) {
        return checkAvailability(item, storageType).thenCompose(available -> Boolean.TRUE.equals(available)
                ? lockItem(item, lockerId, storageType)
                : CompletableFuture.completedFuture(LockToken.unavailable()));
    }

    /**
     * Libere le verrou ET indique s'il nous appartenait encore au moment de la liberation.
     * <p>
     * {@code false} est le seul signal exploitable de « verrou perdu ou vole » : il doit etre
     * journalise en SEVERE par l'appelant. Le code retour existait deja cote script Lua, il
     * etait simplement jete.
     * <p>
     * Methode {@code default} et NON un changement de type de retour de
     * {@link #unlockItem(Item, LockToken, StorageType)} : le type de retour fait partie du
     * descripteur JVM, le modifier leverait AbstractMethodError sur tout bridge compile contre
     * une version anterieure — c'est-a-dire un verrou jamais relache a chaque achat.
     * L'implementation par defaut delegue et rend {@code true} (optimisme assume, strictement
     * equivalent au comportement actuel).
     *
     * @param item        item to unlock
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future resolving to {@code true} if the lock was still held by this token
     */
    default CompletableFuture<Boolean> releaseLock(Item item, LockToken lockToken, StorageType storageType) {
        return unlockItem(item, lockToken, storageType).thenApply(ignored -> Boolean.TRUE);
    }

    /**
     * Duree du bail pose par {@link #lockItem(Item, UUID, StorageType)}.
     * <p>
     * {@link Duration#ZERO} signifie « pas de bail » : le verrou n'expire pas tout seul (cas du
     * bridge mono-serveur) ou l'implementation ne sait pas le prolonger. Les appelants
     * n'arment alors AUCUN watchdog de renouvellement, ce qui rend cette mecanique totalement
     * inerte face a un bridge non mis a jour.
     *
     * @return la duree du bail, ou {@link Duration#ZERO} si la notion ne s'applique pas
     */
    default Duration lockLeaseDuration() {
        return Duration.ZERO;
    }

    /**
     * Prolonge le bail du verrou, si et seulement si ce jeton le detient toujours.
     * <p>
     * L'implementation par defaut rend {@code false} : « je ne sais pas prolonger ». Combinee a
     * {@link #lockLeaseDuration()} == ZERO, elle garantit qu'aucun appelant ne s'appuiera sur
     * une prolongation qui n'a pas eu lieu.
     *
     * @param item        item whose lock must be extended
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future resolving to {@code true} if the lease was actually extended
     */
    default CompletableFuture<Boolean> renewLock(Item item, LockToken lockToken, StorageType storageType) {
        return CompletableFuture.completedFuture(Boolean.FALSE);
    }

    /**
     * Indique si ce jeton detient encore le verrou, a verifier juste avant toute ecriture
     * engageante (debit, remise d'item).
     * <p>
     * L'implementation par defaut rend {@code true} : elle ne peut pas faire pire que le
     * comportement actuel, qui ne verifie rien du tout.
     *
     * @param item        item to check
     * @param lockToken   token returned by {@link #lockItem(Item, UUID, StorageType)}
     * @param storageType storage bucket the item resides in
     * @return future resolving to {@code true} if the lock is still held by this token
     */
    default CompletableFuture<Boolean> isHeldBy(Item item, LockToken lockToken, StorageType storageType) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    /**
     * Indicates whether this bridge operates in a distributed (multi-server) environment.
     * When {@code true}, money deposits to offline sellers should be deferred to the claim
     * system rather than executed locally, because the seller may have never joined this server
     * and the economy plugin may not recognize their account.
     *
     * @return {@code true} if the cluster spans multiple servers, {@code false} for single-server
     */
    default boolean isDistributed() {
        return false;
    }
}
