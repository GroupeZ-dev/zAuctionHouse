package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;

public record WorldConfiguration(EnumMap<ItemType, List<String>> worlds) {

    public static WorldConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        EnumMap<ItemType, List<String>> worlds = new EnumMap<>(ItemType.class);

        for (ItemType itemType : ItemType.values()) {
            var localWorlds = configuration.getStringList("banned-worlds." + itemType.name().toLowerCase());
            worlds.put(itemType, localWorlds);
        }

        return new WorldConfiguration(worlds);
    }

    public boolean isWorldBanned(ItemType itemType, String worldName) {
        return this.worlds.get(itemType).contains(worldName);
    }

}
