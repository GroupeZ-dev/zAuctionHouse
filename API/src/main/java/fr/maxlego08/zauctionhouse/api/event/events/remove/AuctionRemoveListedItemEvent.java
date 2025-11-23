package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.items.Item;
import org.bukkit.entity.Player;

public class AuctionRemoveListedItemEvent extends RemoveEvent {

    public AuctionRemoveListedItemEvent(Item item, Player player) {
        super(item, player);
    }
}
