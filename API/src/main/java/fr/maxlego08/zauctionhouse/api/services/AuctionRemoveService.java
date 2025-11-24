package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

public interface AuctionRemoveService {

    void removeListedItem(Player player, Item item);

    void removeExpiredItem(Player player, Item item);

}
