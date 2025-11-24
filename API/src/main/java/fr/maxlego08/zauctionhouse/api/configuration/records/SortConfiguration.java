package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public record SortConfiguration(SortItem defaultSort, Map<SortItem, String> sortItems) {

    public static SortConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {

        Map<SortItem, String> map = new HashMap<>();

        var defaultSort = get(configuration.getString("sort-items.default-sort"));
        if (defaultSort == null) {
            plugin.getLogger().severe("The default sort is null !, you need to fix that !");
            defaultSort = SortItem.DECREASING_DATE;
        }

        for (SortItem sortItem : SortItem.values()) {

            var value = configuration.getString("sort-items.translations." + sortItem.name());
            if (value == null) {
                plugin.getLogger().severe("The sort item translation for '" + sortItem.name() + "' is null !, you need to fix that !");
                value = sortItem.name();
            }

            map.put(sortItem, value);
        }

        return new SortConfiguration(defaultSort, map);
    }

    private static SortItem get(String value) {
        try {
            return SortItem.valueOf(value.toUpperCase());
        } catch (Exception exception) {
            return null;
        }
    }

}
