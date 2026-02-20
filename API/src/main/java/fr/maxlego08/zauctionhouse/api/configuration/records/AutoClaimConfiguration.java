package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for the auto-claim feature.
 *
 * @param enabled         whether auto-claim is enabled on player join
 * @param delayTicks      delay in ticks before auto-claiming on join (0 = immediate)
 * @param notifyPlayer    whether to send a message to the player when money is claimed
 * @param notifyPending   whether to notify the player about pending money on join (if not auto-claiming)
 * @param notifyDelayTicks delay in ticks before sending the notification about pending money
 * @param depositReason   the reason shown in the economy transaction when depositing money
 */
public record AutoClaimConfiguration(
        boolean enabled,
        long delayTicks,
        boolean notifyPlayer,
        boolean notifyPending,
        long notifyDelayTicks,
        String depositReason
) {

    public static AutoClaimConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        boolean enabled = configuration.getBoolean("auto-claim.enable", false);
        long delayTicks = configuration.getLong("auto-claim.delay-ticks", 20);
        boolean notifyPlayer = configuration.getBoolean("auto-claim.notify-player", true);
        boolean notifyPending = configuration.getBoolean("auto-claim.notify-pending", true);
        long notifyDelayTicks = configuration.getLong("auto-claim.notify-delay-ticks", 40);
        String depositReason = configuration.getString("auto-claim.deposit-reason", "Claimed pending auction money");

        return new AutoClaimConfiguration(enabled, delayTicks, notifyPlayer, notifyPending, notifyDelayTicks, depositReason);
    }
}
