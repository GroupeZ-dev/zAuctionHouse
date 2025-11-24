package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public record ActionConfiguration(
        // Purchase
        boolean giveItemAfterPurchase,
        // Remove listed
        boolean giveItemAfterRemovingListedItem,
        boolean openInventoryAfterRemovingListedItem,
        boolean openConfirmRemoveListedItem,
        // Remove purchased
        boolean openInventoryAfterRemovingPurchasedItem,
        // Remove owned
        boolean openInventoryAfterRemovingOwnedItem,
        // Remove expired
        boolean openInventoryAfterRemovingExpiredItem
) {

    public static ActionConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        return new ActionConfiguration(
                // Purchase
                configuration.getBoolean("action.purchase.give-item"),
                // Remove listed
                configuration.getBoolean("action.remove-listed-item.give-item"),
                configuration.getBoolean("action.remove-listed-item.open-inventory"),
                configuration.getBoolean("action.remove-listed-item.open-confirm-inventory"),
                // Remove purchased
                configuration.getBoolean("action.remove-purchased-item.open-inventory"),
                // Remove owned
                configuration.getBoolean("action.remove-owned-item.open-inventory"),
                // Remove expired
                configuration.getBoolean("action.remove-expired-item.open-inventory")
        );
    }
}

