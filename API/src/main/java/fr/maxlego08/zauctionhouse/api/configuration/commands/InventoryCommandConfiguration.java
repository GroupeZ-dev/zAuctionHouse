package fr.maxlego08.zauctionhouse.api.configuration.commands;

import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record InventoryCommandConfiguration(String permission, String description, List<String> aliases,
                                            Inventories inventories, boolean enablePage, String pageName) {


    public static List<InventoryCommandConfiguration> of(AuctionPlugin plugin, FileConfiguration config) {

        var mapList = config.getMapList("commands.inventories");

        List<InventoryCommandConfiguration> configurations = new ArrayList<>();

        for (Map<?, ?> map : mapList) {
            var accessor = new TypedMapAccessor((Map<String, Object>) map);
            if (!accessor.getBoolean("enable", false)) continue;

            var permission = accessor.getString("permission", "");
            var description = accessor.getString("description", "");
            var aliases = accessor.getStringList("aliases");

            if (aliases.isEmpty()) {
                plugin.getLogger().warning("Aliases is empty for inventories command");
                continue;
            }

            Inventories inventories = null;
            var inventoryName = accessor.getString("inventory", "").toUpperCase();
            try {
                inventories = Inventories.valueOf(inventoryName);
            } catch (Exception exception) {
                plugin.getLogger().warning("Impossible to find the inventory '" + inventoryName + "' for inventories command");
                continue;
            }
            var enablePage = accessor.getBoolean("enablePage", true);
            var pageName = accessor.getString("pageName", "page");

            var configuration = new InventoryCommandConfiguration(permission, description, aliases, inventories, enablePage, pageName);
            configurations.add(configuration);
        }

        return configurations;
    }
}
