package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ItemLoreConfiguration(
        List<String> listedAuctionLore,
        List<String> multipleListedAuctionLore,
        List<String> purchasedLore,
        List<String> expiredLore,
        List<String> ownedLore,
        List<String> beingPurchasedLore,
        List<String> historyLore,
        List<String> adminLogLore,
        List<String> adminLogMultipleLore,
        List<String> sellInventoryRemoveItem,
        Map<LogType, String> logTypeNames,
        String sellerStatus,
        String buyerStatus,
        String rightSellerStatus,
        String rightBuyerStatus
) {

    public static ItemLoreConfiguration of(AuctionPlugin plugin, FileConfiguration config) {
        return new ItemLoreConfiguration(
                config.getStringList("item-lore.listed-auction-item"),
                config.getStringList("item-lore.multiple-listed-auction-item"),
                config.getStringList("item-lore.purchased-item"),
                config.getStringList("item-lore.expired-item"),
                config.getStringList("item-lore.owned-item"),
                config.getStringList("item-lore.being-purchased-item"),
                config.getStringList("item-lore.history-item"),
                config.getStringList("item-lore.admin-log-item"),
                config.getStringList("item-lore.admin-log-multiple-item"),
                config.getStringList("item-lore.sell-inventory-remove-item"),
                loadLogTypeNames(config),
                config.getString("item-lore.status.seller", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʀᴇᴛʀɪᴇᴠᴇ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.buyer", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʙᴜʏ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.right-seller", "#8c8c8c• #2CCED2ʀɪɢʜᴛ ᴄʟɪᴄᴋ #92ffffᴛᴏ ʀᴇᴛʀɪᴇᴠᴇ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.right-buyer", "#8c8c8c• #2CCED2ʀɪɢʜᴛ ᴄʟɪᴄᴋ #92ffffᴛᴏ ʙᴜʏ ᴛʜɪs ɪᴛᴇᴍ")
        );
    }

    private static Map<LogType, String> loadLogTypeNames(FileConfiguration config) {
        Map<LogType, String> names = new EnumMap<>(LogType.class);
        ConfigurationSection section = config.getConfigurationSection("item-lore.log-type-names");

        // Default values
        names.put(LogType.SALE, "Item Listed");
        names.put(LogType.PURCHASE, "Item Purchased");
        names.put(LogType.REMOVE_LISTED, "Removed from Listing");
        names.put(LogType.REMOVE_OWNED, "Retrieved Own Item");
        names.put(LogType.REMOVE_EXPIRED, "Retrieved Expired");
        names.put(LogType.REMOVE_PURCHASED, "Retrieved Purchase");

        if (section != null) {
            for (LogType logType : LogType.values()) {
                String name = section.getString(logType.name());
                if (name != null) {
                    names.put(logType, name);
                }
            }
        }

        return names;
    }

    public String getLogTypeName(LogType logType) {
        return logTypeNames.getOrDefault(logType, logType.name());
    }
}
