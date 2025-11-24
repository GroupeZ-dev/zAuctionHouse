package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;

public class AuctionPreRemoveExpiredItemEvent extends CancelledRemoveEvent {

    public AuctionPreRemoveExpiredItemEvent(Item item, Player player) {
        super(item, player);
    }
}
