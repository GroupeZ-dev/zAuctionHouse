package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.items.Item;
import org.bukkit.entity.Player;

public class AuctionPreRemoveListedItemEvent extends CancelledRemoveEvent {

    public AuctionPreRemoveListedItemEvent(Item item, Player player) {
        super(item, player);
    }
}
