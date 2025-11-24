package fr.maxlego08.zauctionhouse.api.cache;

import java.util.function.Supplier;

public interface PlayerCache {

    <T> void set(PlayerCacheKey key, T value);

    <T> T get(PlayerCacheKey key);

    <T> T get(PlayerCacheKey key, T fallback);

    boolean has(PlayerCacheKey key);

    void remove(PlayerCacheKey key);

    void remove(PlayerCacheKey... keys);

    <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier);

}
