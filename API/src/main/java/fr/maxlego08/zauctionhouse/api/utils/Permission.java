package fr.maxlego08.zauctionhouse.api.utils;

public enum Permission {

    ZAUCTIONHOUSE_USE,
    ZAUCTIONHOUSE_RELOAD,
    ZAUCTIONHOUSE_SELL,
    ZAUCTIONHOUSE_BID,
    ZAUCTIONHOUSE_RENT,
    ;

    private final String permission;

    Permission() {
        this.permission = this.name().toLowerCase().replace("_", ".");
    }

    public String asPermission() {
        return permission;
    }

}