package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.event.CancelledAuctionEvent;
import fr.maxlego08.zauctionhouse.api.items.Item;
import org.bukkit.entity.Player;

public abstract class CancelledRemoveEvent extends CancelledAuctionEvent {

    private final Item item;
    private final Player player;

    public CancelledRemoveEvent(Item item, Player player) {
        this.item = item;
        this.player = player;
    }

    public Item getItem() {
        return item;
    }

    public Player getPlayer() {
        return player;
    }
}
