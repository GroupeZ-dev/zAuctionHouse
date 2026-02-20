package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for sales notification on player join.
 *
 * @param enabled         whether sales notifications are enabled on player join
 * @param delayTicks      delay in ticks before sending the notification (0 = immediate)
 */
public record SalesNotificationConfiguration(
        boolean enabled,
        long delayTicks
) {

    public static SalesNotificationConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        boolean enabled = configuration.getBoolean("sales-notification.enable", true);
        long delayTicks = configuration.getLong("sales-notification.delay-ticks", 40);

        return new SalesNotificationConfiguration(enabled, delayTicks);
    }
}
