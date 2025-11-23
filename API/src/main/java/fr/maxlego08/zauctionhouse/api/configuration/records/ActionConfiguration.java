package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public record ActionConfiguration(
        boolean giveItemAfterPurchase,
        boolean giveItemAfterRemovingListedItem,
        boolean openInventoryAfterRemovingListedItem,
        boolean openInventoryAfterRemovingPurchasedItem,
        boolean openInventoryAfterRemovingOwnedItem,
        boolean openInventoryAfterRemovingExpiredItem
) {

    public static ActionConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        return new ActionConfiguration(
                configuration.getBoolean("action.purchase.give-item"),
                configuration.getBoolean("action.remove-listed-item.give-item"),
                configuration.getBoolean("action.remove-listed-item.open-inventory"),
                configuration.getBoolean("action.remove-purchased-item.open-inventory"),
                configuration.getBoolean("action.remove-owned-item.open-inventory"),
                configuration.getBoolean("action.remove-expired-item.open-inventory")
        );
    }
}

