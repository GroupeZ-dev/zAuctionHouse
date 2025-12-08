package fr.maxlego08.zauctionhouse.api.cache;

import java.util.function.Supplier;

/**
 * Represents a lightweight cache tied to a player, used to store frequently accessed values such
 * as configuration flags, limits, or computed statistics. Implementations are expected to be
 * thread-safe when accessed from asynchronous tasks interacting with the auction house.
 */
public interface PlayerCache {

    /**
     * Stores the provided value under the given key, overriding any previously cached entry.
     *
     * @param key   identifier describing the cached value
     * @param value value to associate with the key
     * @param <T>   type of the value being cached
     */
    <T> void set(PlayerCacheKey key, T value);

    /**
     * Retrieves the value associated with the key or {@code null} when no value is cached.
     *
     * @param key identifier describing the cached value
     * @param <T> expected type of the cached value
     * @return cached value or {@code null}
     */
    <T> T get(PlayerCacheKey key);

    /**
     * Retrieves the value associated with the key or returns the provided fallback when the cache is
     * missing an entry for the key.
     *
     * @param key      identifier describing the cached value
     * @param fallback value returned if nothing is cached
     * @param <T>      expected type of the cached value
     * @return cached value or the fallback
     */
    <T> T get(PlayerCacheKey key, T fallback);

    /**
     * Checks whether a value is already cached for the given key.
     *
     * @param key identifier describing the cached value
     * @return {@code true} if the cache contains the key, {@code false} otherwise
     */
    boolean has(PlayerCacheKey key);

    /**
     * Removes the cached value for the given key.
     *
     * @param key identifier describing the cached value
     */
    void remove(PlayerCacheKey key);

    /**
     * Removes multiple cached values at once.
     *
     * @param keys identifiers describing the cached values
     */
    void remove(PlayerCacheKey... keys);

    /**
     * Retrieves a cached value if it exists or computes and stores a new value using the supplier
     * when missing.
     *
     * @param key      identifier describing the cached value
     * @param supplier computation executed when the value is absent
     * @param <T>      expected type of the cached value
     * @return existing or newly computed value
     */
    <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier);

}
