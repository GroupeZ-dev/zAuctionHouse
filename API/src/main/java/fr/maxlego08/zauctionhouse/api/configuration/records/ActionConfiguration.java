package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;

import java.io.File;

public record ActionConfiguration(
                boolean updateInventoryOnAction,
        boolean resetCategoryOnOpen,
        boolean resetSearchOnOpen,
        // Listed
        ListedConfiguration listed,
        // Purchased
        PurchasedConfiguration purchased,
        // Selling
        SellingConfiguration selling,
        // Expired
        ExpiredConfiguration expired,
        // Container preview
        ContainerPreviewConfiguration containerPreview) {

    public static ActionConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        return new ActionConfiguration(
                configuration.getBoolean("action.update-inventory-on-action"),
                configuration.getBoolean("action.reset-category-on-open", true),
                configuration.getBoolean("action.reset-search-on-open", true),
                // Listed
                ListedConfiguration.of(plugin, configuration),
                // Purchased
                PurchasedConfiguration.of(plugin, configuration),
                // Selling
                SellingConfiguration.of(plugin, configuration),
                // Expired
                ExpiredConfiguration.of(plugin, configuration),
                // Container preview
                ContainerPreviewConfiguration.of(plugin, configuration));
    }

    /**
     * Apercu du contenu d'un conteneur (shulker, etc.) directement depuis l'hotel des ventes,
     * sans passer par la confirmation d'achat ni par la verification d'argent (comportement V3).
     *
     * @param enable active l'apercu
     * @param click  type de clic qui declenche l'apercu
     */
    public record ContainerPreviewConfiguration(boolean enable, ClickType click) {
        public static ContainerPreviewConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
            var enable = configuration.getBoolean("action.container-preview.enable", true);
            var clickName = configuration.getString("action.container-preview.click", "RIGHT");
            ClickType click;
            try {
                click = ClickType.valueOf(clickName.toUpperCase());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid click type '" + clickName + "' for action.container-preview.click, using RIGHT");
                click = ClickType.RIGHT;
            }
            return new ContainerPreviewConfiguration(enable, click);
        }
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

    public record SellingConfiguration(boolean openInventory, boolean freeSpace) {
        public static SellingConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
            return new SellingConfiguration(
                    configuration.getBoolean("action.selling-item.open-inventory"),
                    configuration.getBoolean("action.selling-item.player-inventory-must-have-free-space", true));
        }
    }

    public record ExpiredConfiguration(boolean openInventory, boolean freeSpace) {
        public static ExpiredConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
            return new ExpiredConfiguration(
                    configuration.getBoolean("action.remove-expired-item.open-inventory"),
                    configuration.getBoolean("action.remove-expired-item.player-inventory-must-have-free-space", true));
        }
    }
}


