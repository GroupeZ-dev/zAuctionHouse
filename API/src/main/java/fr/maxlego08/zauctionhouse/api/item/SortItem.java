package fr.maxlego08.zauctionhouse.api.item;

import java.util.Comparator;

public enum SortItem {

    DECREASING_DATE,
    DECREASING_PRICE,
    ASCENDING_DATE,
    ASCENDING_PRICE,

    ;

    public SortItem next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public Comparator<Item> getComparator() {
        return switch (this) {
            case ASCENDING_DATE -> Comparator.comparing(Item::getExpiredAt);
            case DECREASING_DATE -> Comparator.comparing(Item::getExpiredAt).reversed();
            case ASCENDING_PRICE -> Comparator.comparing(Item::getPrice);
            case DECREASING_PRICE -> Comparator.comparing(Item::getPrice).reversed();
        };
    }

}
