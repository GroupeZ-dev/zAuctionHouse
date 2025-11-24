package fr.maxlego08.zauctionhouse.api.inventories;

public enum Inventories {

    AUCTION("auction"),
    REMOVE_CONFIRM("remove-confirm"),
    EXPIRED_ITEMS("expired-items"),
    PURCHASED_ITEMS("purchased-items"),
    OWNED_ITEMS("owned-items"),

    ;

    private final String fileName;

    Inventories(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
