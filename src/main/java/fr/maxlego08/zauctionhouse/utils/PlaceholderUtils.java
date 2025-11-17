package fr.maxlego08.zauctionhouse.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class PlaceholderUtils {

    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    protected String papi(String placeholder, CommandSender sender) {
        return sender instanceof Player player ? PapiHelper.papi(placeholder, player) : placeholder;
    }

    protected List<String> papi(List<String> placeHolders, Player player) {
        return PapiHelper.papi(placeHolders, player);
    }

    private static class CacheEntry {
        String value;
        long timeStamp; // Time when the cache entry was created

        public CacheEntry(String value, long timeStamp) {
            this.value = value;
            this.timeStamp = timeStamp;
        }

        public boolean isValid() {
            return System.currentTimeMillis() - timeStamp < 100L;
        }
    }

    public static class PapiHelper {
        public static String papi(String placeHolder, Player player) {

            if (placeHolder == null) return null;
            if (player == null) return placeHolder;
            if (!placeHolder.contains("%")) return placeHolder;

            String cacheKey = placeHolder + ";" + player.getUniqueId();
            CacheEntry cachedResult = CACHE.get(cacheKey);

            if (cachedResult != null && cachedResult.isValid()) {
                return cachedResult.value;
            }

            String result = PlaceholderAPI.setPlaceholders(player, placeHolder).replace("%player%", player.getName());

            CACHE.put(cacheKey, new CacheEntry(result, System.currentTimeMillis()));
            return result;
        }

        public static List<String> papi(List<String> placeHolders, Player player) {
            if (player == null) return placeHolders;
            return placeHolders.stream().map(placeHolder -> papi(placeHolder, player)).collect(Collectors.toList());
        }
    }

}
