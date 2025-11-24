package fr.maxlego08.zauctionhouse.api.item;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;

public enum StorageType {

    LISTED(PlayerCacheKey.ITEMS_LISTED),
    PURCHASED(PlayerCacheKey.ITEMS_PURCHASED),
    EXPIRED(PlayerCacheKey.ITEMS_EXPIRED),
    DELETED(null),

    ;

    private final PlayerCacheKey playerCacheKey;

    StorageType(PlayerCacheKey playerCacheKey) {
        this.playerCacheKey = playerCacheKey;
    }

    public PlayerCacheKey getPlayerCacheKey() {
        return playerCacheKey;
    }
}
