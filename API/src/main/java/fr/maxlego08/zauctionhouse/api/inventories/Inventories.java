package fr.maxlego08.zauctionhouse.api.inventories;

/**
 * Defines all inventory types used by the auction house.
 * <p>
 * Each inventory corresponds to a YAML configuration file in the inventories folder.
 */
public enum Inventories {

    /**
     * Main auction house inventory showing all listings.
     */
    AUCTION("auction"),

    /**
     * Confirmation dialog for removing a listed item.
     */
    REMOVE_CONFIRM("remove-confirm"),

    /**
     * Confirmation dialog for purchasing an item.
     */
    PURCHASE_CONFIRM("purchase-confirm"),

    /**
     * Confirmation dialog for purchasing from inventory view.
     */
    PURCHASE_INVENTORY_CONFIRM("purchase-inventory-confirm"),

    /**
     * Confirmation dialog for removing from inventory view.
     */
    REMOVE_INVENTORY_CONFIRM("remove-inventory-confirm"),

    /**
     * Inventory for selecting items to sell.
     */
    SELL_INVENTORY("sell-inventory"),

    /**
     * Player's expired items inventory.
     */
    EXPIRED_ITEMS("expired-items"),

    /**
     * Player's purchased items waiting to be claimed.
     */
    PURCHASED_ITEMS("purchased-items"),

    /**
     * Player's currently selling items.
     */
    SELLING_ITEMS("selling-items"),

    /**
     * Admin view of a player's expired items.
     */
    ADMIN_EXPIRED_ITEMS("admin-expired-items"),

    /**
     * Admin view of a player's purchased items.
     */
    ADMIN_PURCHASED_ITEMS("admin-purchased-items"),

    /**
     * Admin view of a player's selling items.
     */
    ADMIN_SELLING_ITEMS("admin-selling-items"),

    /**
     * Admin history main menu.
     */
    ADMIN_MAIN("admin-main"),
    ADMIN_REMOVE_CONFIRM("admin-remove-confirm"),

    ADMIN_HISTORY_MAIN("admin-history-main"),

    /**
     * Admin logs viewer.
     */
    ADMIN_LOGS("admin-logs"),

    /**
     * Admin transactions viewer.
     */
    ADMIN_TRANSACTIONS("admin-transactions"),

    /**
     * Player's sales history.
     */
    HISTORY("history"),

    /**
     * Shulker box content viewer.
     */
    SHULKER_CONTENT("shulker-content"),

    /**
     * Combined view of selling, expired, and purchased items.
     */
    COMBINED_ITEMS("combined-items"),

    /**
     * Player options inventory.
     */
    OPTIONS("options"),

    ;

    private final String fileName;

    Inventories(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the file name for this inventory configuration.
     *
     * @return the file name without extension
     */
    public String getFileName() {
        return fileName;
    }
}
