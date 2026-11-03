package fr.maxlego08.zauctionhouse.utils.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExpiringCacheTest {

    @Test
    @DisplayName("get charge la valeur une seule fois tant que l'entree n'est pas expiree")
    void get_withinExpiry_loadsOnce() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(60_000L);
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get("k", () -> "v" + loads.incrementAndGet());
        String second = cache.get("k", () -> "v" + loads.incrementAndGet());

        assertEquals("v1", first);
        assertEquals("v1", second);
        assertEquals(1, loads.get());
    }

    @Test
    @DisplayName("get recharge la valeur une fois le delai d'expiration depasse")
    void get_afterExpiry_reloads() throws InterruptedException {
        ExpiringCache<String, String> cache = new ExpiringCache<>(1L);
        AtomicInteger loads = new AtomicInteger();

        assertEquals("v1", cache.get("k", () -> "v" + loads.incrementAndGet()));
        Thread.sleep(5);
        assertEquals("v2", cache.get("k", () -> "v" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("des cles differentes sont chargees independamment")
    void get_differentKeys_loadEach() {
        ExpiringCache<Integer, String> cache = new ExpiringCache<>(60_000L);
        AtomicInteger loads = new AtomicInteger();

        assertEquals("v1", cache.get(1, () -> "v" + loads.incrementAndGet()));
        assertEquals("v2", cache.get(2, () -> "v" + loads.incrementAndGet()));
        assertEquals("v1", cache.get(1, () -> "v" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    @DisplayName("clear force le rechargement de la cle, sans toucher aux autres")
    void clear_forcesReload() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(60_000L);
        AtomicInteger loads = new AtomicInteger();

        cache.get("a", () -> "a" + loads.incrementAndGet());
        cache.get("b", () -> "b" + loads.incrementAndGet());
        cache.clear("a");

        assertEquals("a3", cache.get("a", () -> "a" + loads.incrementAndGet()));
        assertEquals("b2", cache.get("b", () -> "b" + loads.incrementAndGet()));
        assertEquals(3, loads.get());
    }

    @Test
    @DisplayName("clear sur une cle inconnue ne leve rien")
    void clear_unknownKey_isNoop() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(1_000L);
        cache.clear("missing");
        assertEquals("v", cache.get("missing", () -> "v"));
    }

    @Test
    @DisplayName("une valeur nulle chargee est memorisee et rendue telle quelle")
    void get_nullValue_isCached() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(60_000L);
        AtomicInteger loads = new AtomicInteger();

        assertNull(cache.get("k", () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(cache.get("k", () -> {
            loads.incrementAndGet();
            return "other";
        }));
        assertEquals(1, loads.get());
    }
}
