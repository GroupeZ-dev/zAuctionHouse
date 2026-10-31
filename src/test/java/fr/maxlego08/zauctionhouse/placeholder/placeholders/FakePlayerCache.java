package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cache joueur en memoire, sans type-safety ni fallback automatique : exactement ce que les
 * placeholders attendent (has / get / get avec fallback). Evite les stubs generiques Mockito.
 */
final class FakePlayerCache implements PlayerCache {

    private final Map<PlayerCacheKey, Object> values = new HashMap<>();

    @Override
    public <T> void set(PlayerCacheKey key, T value) {
        this.values.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(PlayerCacheKey key) {
        return (T) this.values.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(PlayerCacheKey key, T fallback) {
        return this.values.containsKey(key) ? (T) this.values.get(key) : fallback;
    }

    @Override
    public boolean has(PlayerCacheKey key) {
        return this.values.containsKey(key);
    }

    @Override
    public void remove(PlayerCacheKey key) {
        this.values.remove(key);
    }

    @Override
    public void remove(PlayerCacheKey... keys) {
        for (PlayerCacheKey key : keys) remove(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier) {
        return (T) this.values.computeIfAbsent(key, ignored -> supplier.get());
    }
}
