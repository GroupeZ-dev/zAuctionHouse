package fr.maxlego08.zauctionhouse.api.inventories;

public enum Inventories {

    AUCTION("auction"),
    REMOVE_CONFIRM("remove-confirm"),
    PURCHASE_CONFIRM("purchase-confirm"),
    AUCTION_ITEM("auction-item"),
    SELL_INVENTORY("sell-inventory"),
    EXPIRED_ITEMS("expired-items"),
    PURCHASED_ITEMS("purchased-items"),
    OWNED_ITEMS("owned-items"),
    ADMIN_EXPIRED_ITEMS("admin-expired-items"),
    ADMIN_PURCHASED_ITEMS("admin-purchased-items"),
    ADMIN_OWNED_ITEMS("admin-owned-items"),
    ADMIN_HISTORY_MAIN("admin-history-main"),
    ADMIN_LOGS("admin-logs"),
    ADMIN_TRANSACTIONS("admin-transactions"),

    ;

    private final String fileName;

    Inventories(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
