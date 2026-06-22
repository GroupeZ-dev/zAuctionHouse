package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for the sales history system.
 *
 * @param maxEntries    maximum number of history entries displayed per player (0 = unlimited)
 * @param expireAfterDays number of days after which history entries are no longer displayed (0 = never expire)
 */
public record HistoryConfiguration(
        int maxEntries,
        int expireAfterDays
) {

    public static HistoryConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        int maxEntries = configuration.getInt("history.max-entries", 500);
        int expireAfterDays = configuration.getInt("history.expire-after-days", 0);

        return new HistoryConfiguration(maxEntries, expireAfterDays);
    }
}
