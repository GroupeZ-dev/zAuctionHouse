package fr.maxlego08.zauctionhouse.utils.cache;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class ZPlayerCache implements PlayerCache {

    /**
     * EnumMap enveloppee dans un moniteur reentrant plutot qu'un ConcurrentHashMap :
     * <ul>
     *   <li>ConcurrentHashMap INTERDIT les valeurs nulles, or LogTypeFilterButton et
     *       TransactionStatusFilterButton ecrivent deliberement {@code null} pour l'etat
     *       « aucun filtre » ;</li>
     *   <li>{@code computeIfAbsent} y est re-entrance-hostile : getOrCompute(ITEMS_EXPIRED, ...)
     *       redescend dans clearPlayersCache(), donc dans un remove() sur CETTE map, ce qui
     *       peut faire respiner le thread principal a l'infini sur le ReservationNode.</li>
     * </ul>
     * Toutes les operations utilisees ici (get / put / remove / containsKey) sont atomiques
     * sous le moniteur ; aucune iteration n'est faite sur cette map.
     */
    private final Map<PlayerCacheKey, Object> cache = Collections.synchronizedMap(new EnumMap<>(PlayerCacheKey.class));

    @Override
    public <T> void set(PlayerCacheKey key, T value) {
        if (value != null && !key.getRawType().isInstance(value)) {
            throw new IllegalArgumentException("Invalid type for key " + key + ": expected " + key.getType().getType());
        }
        this.cache.put(key, value);
    }

    @Override
    public <T> T get(PlayerCacheKey key) {
        return get(key, key.getFallback());
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

    @Override
    public void remove(PlayerCacheKey... keys) {
        for (PlayerCacheKey key : keys) {
            this.cache.remove(key);
        }
    }

    @Override
    public <T> T getOrCompute(PlayerCacheKey key, Supplier<T> supplier) {
        if (has(key)) {
            return get(key);
        }

        // Le supplier est evalue HORS du moniteur : getItemIds() redescend dans
        // processExpiredItems() puis clearPlayersCache(), qui reprend ce meme moniteur, et il
        // peut declencher des ecritures base. Le tenir ici serrerait le verrou pendant un
        // aller-retour SQL, et le double calcul concurrent - deja possible aujourd'hui - reste
        // inoffensif (les deux threads calculent la meme valeur).
        T value = supplier.get();
        set(key, value);
        return value;
    }

}
