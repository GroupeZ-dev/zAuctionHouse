package fr.maxlego08.zauctionhouse.cluster;

import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LocalAuctionClusterBridge implements AuctionClusterBridge {

    // On memorise la VALEUR DU JETON, pas l'UUID de l'acheteur : c'est ce qui permet une
    // liberation CONDITIONNELLE. Avec l'ancienne map indexee par acheteur, un deverrouillage
    // retardataire faisait un remove inconditionnel et cassait le verrou fraichement acquis
    // par un autre appelant. L'identite du detenteur reste connue : elle est inscrite dans le
    // jeton par LockToken.issue(item, buyerId).
    private final ConcurrentHashMap<Integer, String> itemLocks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.completedFuture(!this.itemLocks.containsKey(item.getId()));
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        var token = LockToken.issue(item, buyerId);

        // Convention commune aux deux bridges (cf. javadoc AuctionClusterBridge#lockItem) :
        // un echec d'acquisition se signale par LockToken.noop() et NON par un future en
        // erreur, qui est reserve aux pannes de transport. Avant ce correctif, une contention
        // mono-serveur remontait en INTERNAL_ERROR et interrompait la chaine de retrait de
        // masse au lieu de simplement sauter l'annonce concernee.
        var existingLock = this.itemLocks.putIfAbsent(item.getId(), token.value());
        if (existingLock != null) {
            return CompletableFuture.completedFuture(LockToken.noop());
        }

        return CompletableFuture.completedFuture(token);
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        release(item, lockToken);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> releaseLock(Item item, LockToken lockToken, StorageType storageType) {
        return CompletableFuture.completedFuture(release(item, lockToken));
    }

    /**
     * Liberation conditionnelle : on ne retire l'entree que si elle porte EXACTEMENT notre
     * jeton. Implementation partagee par unlockItem et releaseLock, sans deleguer de l'une a
     * l'autre — le default de releaseLock appelle unlockItem, l'inverse bouclerait a l infini.
     */
    private boolean release(Item item, LockToken lockToken) {
        if (lockToken == null || !lockToken.isAcquired()) {
            return false;
        }
        return this.itemLocks.remove(item.getId(), lockToken.value());
    }

    @Override
    public CompletableFuture<Void> notifyItemBought(Player player, Item item) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> notifyItemListed(Item item) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> notifyItemStatusChange(Item item, ItemStatus oldStatus, ItemStatus newStatus) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeItem(Item item, StorageType storageType) {
        return CompletableFuture.completedFuture(null);
    }
}
