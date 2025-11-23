package fr.maxlego08.zauctionhouse.api.inventories;

public enum Inventories {

    AUCTION("auction"),
    REMOVE_CONFIRM("remove-confirm"),

    ;

    private final String fileName;

    Inventories(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
