package fr.maxlego08.zauctionhouse.api.utils;

public enum Permission {

    ZAUCTIONHOUSE_USE,
    ZAUCTIONHOUSE_RELOAD,
    ;

    private final String permission;

    Permission() {
        this.permission = this.name().toLowerCase().replace("_", ".");
    }

    public String asPermission() {
        return permission;
    }

}