package fr.maxlego08.zauctionhouse.api.log;

/**
 * Defines the types of actions that can be logged in the auction house.
 * <p>
 * Log types are used to categorize and filter log entries in the admin
 * interface and sales history.
 */
public enum LogType {

    /**
     * A sale was completed - an item was sold by a seller.
     */
    SALE("Sale"),

    ADMIN_REMOVE("Admin Remove"),

    /**
     * A purchase was completed - an item was bought by a buyer.
     */
    PURCHASE("Purchase"),

    /**
     * A listed item was removed by its owner before being sold.
     */
    REMOVE_LISTED("Remove Listed"),

    /**
     * An item being sold was removed from the selling inventory.
     */
    REMOVE_SELLING("Remove Selling"),

    /**
     * An expired item was claimed by its original owner.
     */
    REMOVE_EXPIRED("Remove Expired"),

    /**
     * A purchased item was claimed by the buyer.
     */
    REMOVE_PURCHASED("Remove Purchased");

    private final String defaultDisplayName;

    LogType(String defaultDisplayName) {
        this.defaultDisplayName = defaultDisplayName;
    }

    /**
     * Gets the default display name for this log type.
     *
     * @return the human-readable display name
     */
    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }
}
