package fr.maxlego08.zauctionhouse.api.event.events.remove;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.items.Item;
import org.bukkit.entity.Player;

public abstract class RemoveEvent extends AuctionEvent {

    private final Item item;
    private final Player player;

    public RemoveEvent(Item item, Player player) {
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
