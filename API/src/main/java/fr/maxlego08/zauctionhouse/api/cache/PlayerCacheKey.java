package fr.maxlego08.zauctionhouse.api.cache;

import com.google.common.reflect.TypeToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.SortItem;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public enum PlayerCacheKey {

    ITEMS_LISTED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ITEMS_EXPIRED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ITEMS_PURCHASED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ITEMS_OWNED(new TypeToken<List<Item>>() {}, Collections::emptyList),
    ADMIN_TARGET(new TypeToken<java.util.UUID>() {}, () -> null),
    ADMIN_TARGET_NAME(new TypeToken<String>() {}, () -> ""),
    ITEM_SHOW(new TypeToken<Item>() {}, () -> null),
    CURRENT_PAGE(new TypeToken<Integer>() {}, () -> 1),
    ITEM_SORT(new TypeToken<SortItem>() {}, () -> SortItem.DECREASING_DATE),
    ITEM_SORT_LOADING(new TypeToken<Boolean>() {}, () -> false),
    PURCHASE_ITEM(new TypeToken<Boolean>() {}, () -> false);

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
