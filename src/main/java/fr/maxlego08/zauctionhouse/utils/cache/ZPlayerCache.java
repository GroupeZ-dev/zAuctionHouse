package fr.maxlego08.zauctionhouse.utils.cache;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class ZPlayerCache implements PlayerCache {

    private final Map<PlayerCacheKey, Object> cache = new EnumMap<>(PlayerCacheKey.class);

    @Override
    public <T> void set(PlayerCacheKey key, T value) {
        if (!isValidType(key.getType().getType(), value)) {
            throw new IllegalArgumentException("Invalid type for key " + key + ": expected " + key.getType().getType());
        }
        this.cache.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(PlayerCacheKey key) {
        return (T) this.cache.get(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(PlayerCacheKey key, T fallback) {
        return (T) this.cache.getOrDefault(key, fallback);
    }

    @Override
    public boolean has(PlayerCacheKey key) {
        return this.cache.containsKey(key);
    }

    @Override
    public void remove(PlayerCacheKey key) {
        this.cache.remove(key);
    }

    private boolean isValidType(Type expected, Object value) {
        if (value == null) return true;

        if (expected instanceof Class<?>) {
            return ((Class<?>) expected).isInstance(value);
        }

        if (expected instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            return raw.isInstance(value);
        }

        return false;
    }

    @Override
    public <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier) {
        if (has(key)) {
            return get(key);
        }

        T value = supplier.get();
        set(key, value);
        return value;
    }

}
