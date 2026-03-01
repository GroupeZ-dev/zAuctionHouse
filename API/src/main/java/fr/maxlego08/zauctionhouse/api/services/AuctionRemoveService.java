package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public interface AuctionRemoveService {

    CompletableFuture<Void> removeListedItem(Player player, Item item);

    CompletableFuture<Void> removeSellingItem(Player player, Item item);

    CompletableFuture<Void> removeExpiredItem(Player player, Item item);

    CompletableFuture<Void> removePurchasedItem(Player player, Item item);

}
