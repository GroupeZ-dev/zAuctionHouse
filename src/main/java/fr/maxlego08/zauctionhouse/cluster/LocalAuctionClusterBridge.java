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

    private final ConcurrentHashMap<UUID, UUID> itemLocks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.completedFuture(!itemLocks.containsKey(item.getId()));
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId, StorageType storageType) {
        UUID existingLock = itemLocks.putIfAbsent(item.getId(), buyerId);
        
        if (existingLock != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Item already locked by another player"));
        }
        
        return CompletableFuture.completedFuture(LockToken.of(item));
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken, StorageType storageType) {
        itemLocks.remove(item.getId());
        return CompletableFuture.completedFuture(null);
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
