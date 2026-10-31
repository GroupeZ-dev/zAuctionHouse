package fr.maxlego08.zauctionhouse.api.cache;

import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import fr.maxlego08.zauctionhouse.api.history.HistorySortType;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCacheKeyTest {

    @ParameterizedTest
    @EnumSource(PlayerCacheKey.class)
    @DisplayName("chaque cle expose un type, un type brut et un fallback compatible avec ce type")
    void everyKey_fallbackMatchesRawType(PlayerCacheKey key) {
        assertNotNull(key.getType());
        assertSame(key.getType().getRawType(), key.getRawType());
        Object fallback = key.getFallback();
        if (fallback != null) {
            assertTrue(key.getRawType().isInstance(fallback), key + " fallback " + fallback.getClass());
        }
    }

    @Test
    @DisplayName("les fallbacks mutables sont des instances neuves a chaque appel")
    void mutableFallbacks_areFreshInstances() {
        IntList a = PlayerCacheKey.ITEMS_LISTED.getFallback();
        IntList b = PlayerCacheKey.ITEMS_LISTED.getFallback();
        assertNotSame(a, b);
        assertTrue(a.isEmpty());

        Map<Integer, Object> sellA = PlayerCacheKey.SELL_ITEMS.getFallback();
        Map<Integer, Object> sellB = PlayerCacheKey.SELL_ITEMS.getFallback();
        assertNotSame(sellA, sellB);

        List<Object> logsA = PlayerCacheKey.ADMIN_LOGS_DATA.getFallback();
        List<Object> logsB = PlayerCacheKey.ADMIN_LOGS_DATA.getFallback();
        assertNotSame(logsA, logsB);
    }

    @Test
    @DisplayName("les valeurs par defaut metier sont celles documentees")
    void defaultValues() {
        int page = PlayerCacheKey.CURRENT_PAGE.getFallback();
        assertEquals(1, page);
        assertEquals(SortItem.DECREASING_DATE, PlayerCacheKey.ITEM_SORT.getFallback());
        assertEquals(Boolean.FALSE, PlayerCacheKey.ITEM_SORT_LOADING.getFallback());
        assertEquals(BigDecimal.ZERO, PlayerCacheKey.SELL_PRICE.getFallback());
        long expiredAt = PlayerCacheKey.SELL_EXPIRED_AT.getFallback();
        assertEquals(0L, expiredAt);
        int amount = PlayerCacheKey.SELL_AMOUNT.getFallback();
        assertEquals(0, amount);
        assertEquals("", PlayerCacheKey.ADMIN_TARGET_NAME.getFallback());
        assertNull(PlayerCacheKey.ADMIN_TARGET_UUID.getFallback());
        assertNull(PlayerCacheKey.CURRENT_CATEGORY.getFallback());
        assertNull(PlayerCacheKey.SEARCH_QUERY.getFallback());
        assertEquals(DateFilter.ALL, PlayerCacheKey.ADMIN_LOGS_DATE_FILTER.getFallback());
        assertEquals(HistorySortType.DATE_DESC, PlayerCacheKey.HISTORY_SORT.getFallback());
        int shulkerIndex = PlayerCacheKey.SHULKER_INDEX.getFallback();
        assertEquals(0, shulkerIndex);
    }

    @Test
    @DisplayName("les types bruts correspondent aux types declares")
    void rawTypes() {
        assertSame(IntList.class, PlayerCacheKey.ITEMS_LISTED.getRawType());
        assertSame(Integer.class, PlayerCacheKey.CURRENT_PAGE.getRawType());
        assertSame(Map.class, PlayerCacheKey.SELL_ITEMS.getRawType());
        assertSame(List.class, PlayerCacheKey.HISTORY_DATA.getRawType());
        assertSame(String.class, PlayerCacheKey.SEARCH_QUERY.getRawType());
    }
}
