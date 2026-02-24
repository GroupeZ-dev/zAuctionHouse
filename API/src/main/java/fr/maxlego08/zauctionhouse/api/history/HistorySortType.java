package fr.maxlego08.zauctionhouse.api.history;

import java.util.Comparator;

/**
 * Sorting options for sales history.
 */
public enum HistorySortType {

    DATE_DESC("Newest First", Comparator.comparing((ItemLog log) -> log.log().created_at()).reversed()),
    DATE_ASC("Oldest First", Comparator.comparing(log -> log.log().created_at())),
    PRICE_DESC("Highest Price", Comparator.comparing((ItemLog log) -> log.log().price()).reversed()),
    PRICE_ASC("Lowest Price", Comparator.comparing(log -> log.log().price())),
    BUYER_ASC("Buyer A-Z", Comparator.comparing(log -> log.item().getBuyerName() != null ? log.item().getBuyerName() : "")),
    BUYER_DESC("Buyer Z-A", Comparator.comparing((ItemLog log) -> log.item().getBuyerName() != null ? log.item().getBuyerName() : "").reversed());

    private final String defaultDisplayName;
    private final Comparator<ItemLog> comparator;

    HistorySortType(String defaultDisplayName, Comparator<ItemLog> comparator) {
        this.defaultDisplayName = defaultDisplayName;
        this.comparator = comparator;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public Comparator<ItemLog> getComparator() {
        return comparator;
    }
}
