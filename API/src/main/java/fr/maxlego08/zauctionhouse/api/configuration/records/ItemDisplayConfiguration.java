package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public record ItemDisplayConfiguration(
        String langDisplay,
        String itemNameDisplay,
        String and,
        String between
) {


    public static ItemDisplayConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {

        var langDisplay = configuration.getString("item-display.lang", "");
        if (langDisplay.isBlank()) {
            plugin.getLogger().severe("The lang display is blank !, you need to fix that !");
            langDisplay = "#8ee6e3x%amount% &7<lang:%item-translation-key%>";
        }

        var itemNameDisplay = configuration.getString("item-display.item-name", "");
        if (itemNameDisplay.isBlank()) {
            plugin.getLogger().severe("The item name display is blank !, you need to fix that !");
            itemNameDisplay = "#8ee6e3x%amount% &7%item-name%";
        }

        var and = configuration.getString("item-display.and", "and");
        var between = configuration.getString("item-display.between", ",");

        return new ItemDisplayConfiguration(langDisplay, itemNameDisplay, and, between);
    }

}
