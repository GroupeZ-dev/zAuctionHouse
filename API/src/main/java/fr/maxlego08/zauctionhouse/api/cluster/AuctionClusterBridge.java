package fr.maxlego08.zauctionhouse.api.cluster;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuctionClusterBridge {

    CompletableFuture<Boolean> checkAvailability(Item item);

    CompletableFuture<LockToken> lockItem(Item item, UUID buyerId);

    CompletableFuture<Void> unlockItem(Item item, LockToken lockToken);

    CompletableFuture<Void> notifyItemBought(Player player, Item item);

    CompletableFuture<Void> notifyItemSold(Item item);

    CompletableFuture<Void> removeItem(Item item);
}
