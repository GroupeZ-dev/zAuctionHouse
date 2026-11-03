package fr.maxlego08.zauctionhouse.utils.cache;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZPlayerCacheTest {

    private ZPlayerCache cache;

    @BeforeEach
    void setUp() {
        this.cache = new ZPlayerCache();
    }

    @Test
    @DisplayName("set puis get rend la valeur stockee")
    void setThenGet_returnsStoredValue() {
        cache.set(PlayerCacheKey.CURRENT_PAGE, 7);
        int page = cache.get(PlayerCacheKey.CURRENT_PAGE);
        assertEquals(7, page);
        assertTrue(cache.has(PlayerCacheKey.CURRENT_PAGE));
    }

    @Test
    @DisplayName("set refuse une valeur dont le type ne correspond pas a la cle")
    void set_wrongType_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cache.set(PlayerCacheKey.CURRENT_PAGE, "not-an-integer"));
        assertTrue(exception.getMessage().contains("CURRENT_PAGE"));
        assertFalse(cache.has(PlayerCacheKey.CURRENT_PAGE));
    }

    @Test
    @DisplayName("set accepte une sous-classe du type attendu (IntArrayList pour IntList)")
    void set_subtype_isAccepted() {
        IntList list = new IntArrayList();
        list.add(42);
        cache.set(PlayerCacheKey.ITEMS_LISTED, list);
        IntList stored = cache.get(PlayerCacheKey.ITEMS_LISTED);
        assertSame(list, stored);
    }

    @Test
    @DisplayName("set(null) est autorise : la cle existe (has) et get rend null malgre le fallback")
    void set_null_isStoredAsNullFilterState() {
        cache.set(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER, null);
        assertTrue(cache.has(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER));
        assertNull(cache.get(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER));
        // Meme avec un fallback explicite : la cle est presente, la valeur nulle l'emporte.
        cache.set(PlayerCacheKey.CURRENT_PAGE, null);
        Integer page = cache.get(PlayerCacheKey.CURRENT_PAGE, 99);
        assertNull(page);
    }

    @Test
    @DisplayName("get sans valeur rend le fallback de la cle, une nouvelle instance a chaque fois")
    void get_missing_returnsFreshFallback() {
        int page = cache.get(PlayerCacheKey.CURRENT_PAGE);
        assertEquals(1, page);
        SortItem sort = cache.get(PlayerCacheKey.ITEM_SORT);
        assertEquals(SortItem.DECREASING_DATE, sort);
        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE);
        assertEquals(BigDecimal.ZERO, price);

        IntList first = cache.get(PlayerCacheKey.ITEMS_EXPIRED);
        IntList second = cache.get(PlayerCacheKey.ITEMS_EXPIRED);
        assertNotSame(first, second);
        assertFalse(cache.has(PlayerCacheKey.ITEMS_EXPIRED), "le fallback ne doit pas etre memorise");
    }

    @Test
    @DisplayName("get(key, fallback) rend le fallback explicite quand la cle est absente")
    void get_withExplicitFallback() {
        String name = cache.get(PlayerCacheKey.ADMIN_TARGET_NAME, "Steve");
        assertEquals("Steve", name);
        cache.set(PlayerCacheKey.ADMIN_TARGET_NAME, "Alex");
        assertEquals("Alex", cache.get(PlayerCacheKey.ADMIN_TARGET_NAME, "Steve"));
    }

    @Test
    @DisplayName("remove(key) supprime la cle ; remove(keys...) en supprime plusieurs d'un coup")
    void remove_singleAndVarargs() {
        cache.set(PlayerCacheKey.CURRENT_PAGE, 3);
        cache.set(PlayerCacheKey.PURCHASE_ITEM, true);
        cache.set(PlayerCacheKey.SHULKER_INDEX, 5);

        cache.remove(PlayerCacheKey.CURRENT_PAGE);
        assertFalse(cache.has(PlayerCacheKey.CURRENT_PAGE));
        assertTrue(cache.has(PlayerCacheKey.PURCHASE_ITEM));

        cache.remove(PlayerCacheKey.PURCHASE_ITEM, PlayerCacheKey.SHULKER_INDEX, PlayerCacheKey.ITEM_SHOW);
        assertFalse(cache.has(PlayerCacheKey.PURCHASE_ITEM));
        assertFalse(cache.has(PlayerCacheKey.SHULKER_INDEX));
    }

    @Test
    @DisplayName("remove sur une cle absente est sans effet")
    void remove_missing_isNoop() {
        cache.remove(PlayerCacheKey.CURRENT_PAGE);
        cache.remove(new PlayerCacheKey[0]);
        assertFalse(cache.has(PlayerCacheKey.CURRENT_PAGE));
    }

    @Test
    @DisplayName("getOrCompute calcule et memorise une seule fois")
    void getOrCompute_computesOnce() {
        AtomicInteger calls = new AtomicInteger();
        int first = cache.getOrCompute(PlayerCacheKey.CURRENT_PAGE, () -> 10 + calls.incrementAndGet());
        int second = cache.getOrCompute(PlayerCacheKey.CURRENT_PAGE, () -> 10 + calls.incrementAndGet());

        assertEquals(11, first);
        assertEquals(11, second);
        assertEquals(1, calls.get());
        assertTrue(cache.has(PlayerCacheKey.CURRENT_PAGE));
    }

    @Test
    @DisplayName("getOrCompute n'appelle pas le supplier quand une valeur (meme nulle) est presente")
    void getOrCompute_existingNull_doesNotCompute() {
        cache.set(PlayerCacheKey.SEARCH_QUERY, null);
        AtomicInteger calls = new AtomicInteger();
        String value = cache.getOrCompute(PlayerCacheKey.SEARCH_QUERY, () -> "q" + calls.incrementAndGet());
        assertNull(value);
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("getOrCompute propage le controle de type de set")
    void getOrCompute_wrongType_throws() {
        assertThrows(IllegalArgumentException.class, () -> cache.getOrCompute(PlayerCacheKey.CURRENT_PAGE, () -> "oops"));
        assertFalse(cache.has(PlayerCacheKey.CURRENT_PAGE));
    }

    @Test
    @DisplayName("getOrCompute supporte la reentrance : le supplier peut modifier le cache")
    void getOrCompute_reentrant_supplierMayMutateCache() {
        int value = cache.getOrCompute(PlayerCacheKey.CURRENT_PAGE, () -> {
            cache.remove(PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_LISTED);
            cache.set(PlayerCacheKey.SHULKER_INDEX, 2);
            return 4;
        });
        assertEquals(4, value);
        int shulkerIndex = cache.get(PlayerCacheKey.SHULKER_INDEX);
        assertEquals(2, shulkerIndex);
    }
}
