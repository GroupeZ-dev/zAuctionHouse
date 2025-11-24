package fr.maxlego08.zauctionhouse.cluster;

import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LocalAuctionClusterBridge implements AuctionClusterBridge {

    @Override
    public CompletableFuture<Boolean> checkAvailability(Item item) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<LockToken> lockItem(Item item, UUID buyerId) {
        return CompletableFuture.completedFuture(LockToken.of(item));
    }

    @Override
    public CompletableFuture<Void> unlockItem(Item item, LockToken lockToken) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> notifyItemBought(Item item) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> notifyItemSold(Item item) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeItem(Item item) {
        return CompletableFuture.completedFuture(null);
    }
}
