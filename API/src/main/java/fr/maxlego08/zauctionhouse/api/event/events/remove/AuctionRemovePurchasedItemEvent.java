package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

public class AuctionRemovePurchasedItemEvent extends RemoveEvent {

    public AuctionRemovePurchasedItemEvent(Item item, Player player) {
        super(item, player);
    }
}
