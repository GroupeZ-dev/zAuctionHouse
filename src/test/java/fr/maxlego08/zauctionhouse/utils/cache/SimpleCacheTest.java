package fr.maxlego08.zauctionhouse.utils.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleCacheTest {

    @Test
    @DisplayName("get charge une seule fois et rend toujours la meme instance")
    void get_loadsOnce_returnsSameInstance() {
        SimpleCache<String, Object> cache = new SimpleCache<>();
        AtomicInteger loads = new AtomicInteger();

        Object first = cache.get("k", () -> {
            loads.incrementAndGet();
            return new Object();
        });
        Object second = cache.get("k", () -> {
            loads.incrementAndGet();
            return new Object();
        });

        assertSame(first, second);
        assertEquals(1, loads.get());
    }

    @Test
    @DisplayName("des cles differentes declenchent des chargements distincts")
    void get_differentKeys_loadSeparately() {
        SimpleCache<Integer, String> cache = new SimpleCache<>();
        assertEquals("one", cache.get(1, () -> "one"));
        assertEquals("two", cache.get(2, () -> "two"));
        assertEquals("one", cache.get(1, () -> "changed"));
    }

    @Test
    @DisplayName("un loader qui rend null leve IllegalStateException mentionnant la cle")
    void get_nullLoader_throws() {
        SimpleCache<String, String> cache = new SimpleCache<>();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> cache.get("bad-key", () -> null));
        assertTrue(exception.getMessage().contains("bad-key"));
    }

    @Test
    @DisplayName("apres un echec de chargement, la cle reste absente et peut etre rechargee")
    void get_afterFailure_retriesLoader() {
        SimpleCache<String, String> cache = new SimpleCache<>();
        assertThrows(IllegalStateException.class, () -> cache.get("k", () -> null));
        assertEquals("ok", cache.get("k", () -> "ok"));
    }
}
