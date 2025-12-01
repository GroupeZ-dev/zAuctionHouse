package fr.maxlego08.zauctionhouse.api.inventories;

public enum Inventories {

    AUCTION("auction"),
    REMOVE_CONFIRM("remove-confirm"),
    PURCHASE_CONFIRM("purchase-confirm"),
    EXPIRED_ITEMS("expired-items"),
    PURCHASED_ITEMS("purchased-items"),
    OWNED_ITEMS("owned-items"),
    ADMIN_EXPIRED_ITEMS("admin-expired-items"),
    ADMIN_PURCHASED_ITEMS("admin-purchased-items"),
    ADMIN_OWNED_ITEMS("admin-owned-items"),

    ;

    private final String fileName;

    Inventories(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
