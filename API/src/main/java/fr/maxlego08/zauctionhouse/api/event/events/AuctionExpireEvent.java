package fr.maxlego08.zauctionhouse.api.event.events;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

public class AuctionExpireEvent extends AuctionEvent {

    private final Item item;
    private final StorageType type;

    public AuctionExpireEvent(Item item, StorageType type) {
        this.item = item;
        this.type = type;
    }

    public Item getItem() {
        return item;
    }

    public StorageType getType() {
        return type;
    }
}
