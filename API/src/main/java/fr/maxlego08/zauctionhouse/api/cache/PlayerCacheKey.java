package fr.maxlego08.zauctionhouse.api.cache;

import com.google.common.reflect.TypeToken;
import fr.maxlego08.zauctionhouse.api.items.Item;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public enum PlayerCacheKey {

    ITEMS_LISTED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ITEMS_EXPIRED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ITEMS_PURCHASED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ITEMS_OWNED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ITEM_SHOW(new TypeToken<Item>() {}, () -> null),
    CURRENT_PAGE(new TypeToken<Integer>() {}, () -> 1);

    private final TypeToken<?> type;
    private final Supplier<?> fallback;

    PlayerCacheKey(TypeToken<?> type, Supplier<?> fallback) {
        this.type = type;
        this.fallback = fallback;
    }

    public TypeToken<?> getType() {
        return type;
    }

    @SuppressWarnings("unchecked")
    public <T> T getFallback() {
        return (T) fallback.get();
    }
}
