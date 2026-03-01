package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public record ActionConfiguration(
                boolean updateInventoryOnAction,
        // Listed
        ListedConfiguration listed,
        // Purchased
        PurchasedConfiguration purchased,
        // Selling
        SellingConfiguration selling,
        // Expired
        ExpiredConfiguration expired) {

    public static ActionConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        return new ActionConfiguration(
                configuration.getBoolean("action.update-inventory-on-action"),
                // Listed
                ListedConfiguration.of(plugin, configuration),
                // Purchased
                PurchasedConfiguration.of(plugin, configuration),
                // Selling
                SellingConfiguration.of(plugin, configuration),
                // Expired
                ExpiredConfiguration.of(plugin, configuration));
    }

    public record ListedConfiguration(boolean giveItem,
                                      boolean openInventory,
                                      boolean openConfirmInventory) {
        public static ListedConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
            return new ListedConfiguration(configuration.getBoolean("action.remove-listed-item.give-item"),
                    configuration.getBoolean("action.remove-listed-item.open-inventory"),
                    configuration.getBoolean("action.remove-listed-item.open-confirm-inventory"));
        }
    }

    public record PurchasedConfiguration(boolean giveItem,
                                         boolean openInventory,
                                         PurchaseNoMoneyConfiguration noMoney,
                                         boolean sendNoMoneyMessage,
                                         SoundConfiguration noMoneySound,
                                         boolean freeSpace
    ) {
        public static PurchasedConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
            return new PurchasedConfiguration(
                    configuration.getBoolean("action.purchased-item.give-item"),
                    configuration.getBoolean("action.purchased-item.open-inventory"),
                    PurchaseNoMoneyConfiguration.of(plugin, configuration),
                    configuration.getBoolean("action.purchased-item.money-message"),
                    SoundConfiguration.of(plugin, configuration, "action.purchased-item.money-sound."),
                    configuration.getBoolean("action.purchased-item.player-inventory-must-have-free-space")
            );
        }
    }

    public record PurchaseNoMoneyConfiguration(boolean enable, int duration, MenuItemStack menuItemStack) {
        public static PurchaseNoMoneyConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {

            var enable = configuration.getBoolean("action.purchased-item.money-item.enable");
            MenuItemStack menuItemStack = null;
            int duration = 0;

            if (enable) {
                menuItemStack = plugin.getInventoriesLoader().getInventoryManager().loadItemStack((YamlConfiguration) configuration, "action.purchased-item.money-item.item.", new File(plugin.getDataFolder(), "config.yml"));
                duration = configuration.getInt("action.purchased-item.money-item.duration", 1);
                if (duration <= 0) {
                    plugin.getLogger().warning("The duration of the purchase no money is less than or equal to 0 !");
                    duration = 1;
                }
            }
            return new PurchaseNoMoneyConfiguration(enable, duration, menuItemStack);
        }
    }

    public record SellingConfiguration(boolean openInventory) {
        public static SellingConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
            return new SellingConfiguration(configuration.getBoolean("action.selling-item.open-inventory"));
        }
    }

    public record ExpiredConfiguration(boolean openInventory) {
        public static ExpiredConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
            return new ExpiredConfiguration(configuration.getBoolean("action.remove-expired-item.open-inventory"));
        }
    }
}


