package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public record SpecialItemsConfiguration(MenuItemStack auctionItem) {

    public static SpecialItemsConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {

        var auctionItem = plugin.getInventoriesLoader().getInventoryManager().loadItemStack((YamlConfiguration) configuration, "special-items.auction-item.", new File(plugin.getDataFolder(), "config.yml"));

        return new SpecialItemsConfiguration(auctionItem);
    }

}
