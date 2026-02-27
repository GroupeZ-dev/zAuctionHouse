package fr.maxlego08.zauctionhouse.api.cache;

import com.google.common.reflect.TypeToken;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import fr.maxlego08.zauctionhouse.api.history.HistorySortType;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.log.AdminLogItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.api.utils.IntList;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public enum PlayerCacheKey {

    ITEMS_LISTED(new TypeToken<IntList>() {}, IntArrayList::new),
    ITEMS_EXPIRED(new TypeToken<IntList>() {}, IntArrayList::new),
    ITEMS_PURCHASED(new TypeToken<IntList>() {}, IntArrayList::new),
    ITEMS_OWNED(new TypeToken<IntList>() {}, IntArrayList::new),
    ADMIN_TARGET_UUID(new TypeToken<java.util.UUID>() {}, () -> null),
    ADMIN_TARGET_NAME(new TypeToken<String>() {}, () -> ""),
    ITEM_SHOW(new TypeToken<Item>() {}, () -> null),
    CURRENT_PAGE(new TypeToken<Integer>() {}, () -> 1),
    ITEM_SORT(new TypeToken<SortItem>() {}, () -> SortItem.DECREASING_DATE),
    ITEM_SORT_LOADING(new TypeToken<Boolean>() {}, () -> false),
    PURCHASE_ITEM(new TypeToken<Boolean>() {}, () -> false),
    SELL_PRICE(new TypeToken<BigDecimal>() {}, () -> BigDecimal.ZERO),
    SELL_ECONOMY(new TypeToken<AuctionEconomy>() {}, () -> null),
    SELL_EXPIRED_AT(new TypeToken<Long>() {}, () -> 0L),
    SELL_AMOUNT(new TypeToken<Integer>() {}, () -> 1),
    SELL_ITEMS(new TypeToken<List<org.bukkit.inventory.ItemStack>>() {}, ArrayList::new),
    CURRENT_CATEGORY(new TypeToken<Category>() {}, () -> null),

    // Admin Logs
    ADMIN_LOGS_DATA(new TypeToken<List<LogDTO>>() {}, ArrayList::new),
    ADMIN_LOGS_LOADING(new TypeToken<Boolean>() {}, () -> false),
    ADMIN_LOGS_TYPE_FILTER(new TypeToken<LogType>() {}, () -> null),
    ADMIN_LOGS_DATE_FILTER(new TypeToken<DateFilter>() {}, () -> DateFilter.ALL),
    ADMIN_LOG_SELECTED(new TypeToken<AdminLogItem>() {}, () -> null),

    // Admin Transactions
    ADMIN_TRANSACTIONS_DATA(new TypeToken<List<TransactionDTO>>() {}, ArrayList::new),
    ADMIN_TRANSACTIONS_LOADING(new TypeToken<Boolean>() {}, () -> false),
    ADMIN_TRANSACTIONS_STATUS_FILTER(new TypeToken<TransactionStatus>() {}, () -> null),
    ADMIN_TRANSACTIONS_DATE_FILTER(new TypeToken<DateFilter>() {}, () -> DateFilter.ALL),

    // Pending Money (Claim)
    PENDING_MONEY_DATA(new TypeToken<java.util.Map<String, java.math.BigDecimal>>() {}, java.util.HashMap::new),
    PENDING_MONEY_LOADING(new TypeToken<Boolean>() {}, () -> false),

    // Sales History
    HISTORY_DATA(new TypeToken<List<LogDTO>>() {}, ArrayList::new),
    HISTORY_LOADING(new TypeToken<Boolean>() {}, () -> false),
    HISTORY_SORT(new TypeToken<HistorySortType>() {}, () -> HistorySortType.DATE_DESC)
    ;

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
