package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public interface AuctionPurchaseService {

    CompletableFuture<Void> purchaseItem(Player player, Item item);
}
