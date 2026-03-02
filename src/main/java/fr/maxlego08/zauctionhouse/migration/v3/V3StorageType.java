package fr.maxlego08.zauctionhouse.migration.v3;

import fr.maxlego08.zauctionhouse.api.item.StorageType;

/**
 * Represents V3 storage types with their quirky table naming.
 * Note: V3 has swapped naming conventions that we need to handle.
 */
public enum V3StorageType {

    /**
     * Items currently on sale (V3 table: "auction")
     */
    STORAGE("auction"),

    /**
     * Purchased items waiting to be claimed (V3 table: "auction_expire" - yes, it's swapped)
     */
    BUY("auction_expire"),

    /**
     * Expired items (V3 table: "auction_buy" - yes, it's swapped)
     */
    EXPIRE("auction_buy");

    private final String tableSuffix;

    V3StorageType(String tableSuffix) {
        this.tableSuffix = tableSuffix;
    }

    public String getTableSuffix() {
        return tableSuffix;
    }

    /**
     * Converts V3 storage type to V4 storage type.
     */
    public StorageType toV4StorageType() {
        return switch (this) {
            case STORAGE -> StorageType.LISTED;
            case BUY -> StorageType.PURCHASED;
            case EXPIRE -> StorageType.EXPIRED;
        };
    }

    /**
     * Parse from V3 database string value.
     */
    public static V3StorageType fromString(String value) {
        if (value == null) return STORAGE;
        return switch (value.toUpperCase()) {
            case "BUY" -> BUY;
            case "EXPIRE" -> EXPIRE;
            default -> STORAGE;
        };
    }
}
