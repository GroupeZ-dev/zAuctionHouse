package fr.maxlego08.zauctionhouse.api.event.events;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.util.List;

public class AuctionExpireEvent extends AuctionEvent {

    private final List<Item> items;
    private final StorageType type;

    public AuctionExpireEvent(List<Item> items, StorageType type) {
        this.items = items;
        this.type = type;
    }

    public List<Item> getItems() {
        return items;
    }

    public StorageType getType() {
        return type;
    }
}
