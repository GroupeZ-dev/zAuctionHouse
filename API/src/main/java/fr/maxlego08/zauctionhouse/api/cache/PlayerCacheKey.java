package fr.maxlego08.zauctionhouse.api.cache;

import com.google.common.reflect.TypeToken;
import fr.maxlego08.zauctionhouse.api.items.Item;

import java.util.List;

public enum PlayerCacheKey {

    ITEMS_LISTED(new TypeToken<List<Item>>() {
    }),
    ITEMS_EXPIRED(new TypeToken<List<Item>>() {
    }),
    ITEMS_PURCHASED(new TypeToken<List<Item>>() {
    }),
    ITEMS_OWNED(new TypeToken<List<Item>>() {
    }),
    ITEM_SHOW(new TypeToken<Item>() {
    }),
    CURRENT_PAGE(new TypeToken<Integer>() {
    })
    ;

    private final TypeToken<?> type;

    PlayerCacheKey(TypeToken<?> type) {
        this.type = type;
    }

    public TypeToken<?> getType() {
        return type;
    }
}
