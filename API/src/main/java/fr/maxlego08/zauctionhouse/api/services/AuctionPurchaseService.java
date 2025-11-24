package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

public interface AuctionPurchaseService {

    void purchaseItem(Player player, Item item);
}
