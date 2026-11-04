package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemPlaceholder;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ItemLoreConfiguration(
        boolean forceAmountOne,
        List<String> listedAuctionLore,
        Set<ItemPlaceholder> listedAuctionPlaceholders,
        List<String> multipleListedAuctionLore,
        Set<ItemPlaceholder> multipleListedAuctionPlaceholders,
        List<String> purchasedLore,
        Set<ItemPlaceholder> purchasedPlaceholders,
        List<String> expiredLore,
        Set<ItemPlaceholder> expiredPlaceholders,
        List<String> sellingLore,
        Set<ItemPlaceholder> sellingPlaceholders,
        List<String> beingPurchasedLore,
        Set<ItemPlaceholder> beingPurchasedPlaceholders,
        List<String> historyLore,
        List<String> adminLogLore,
        List<String> adminLogMultipleLore,
        List<String> sellInventoryRemoveItem,
        Map<LogType, String> logTypeNames,
        String sellerStatus,
        String buyerStatus,
        String rightSellerStatus,
        String rightBuyerStatus,
        String containerPreview
) {

    public static final String DEFAULT_CONTAINER_PREVIEW = "#8c8c8c• #2CCED2ʀɪɢʜᴛ ᴄʟɪᴄᴋ #92ffffᴛᴏ ᴠɪᴇᴡ ᴛʜᴇ ᴄᴏɴᴛᴇɴᴛs";

    public static ItemLoreConfiguration of(AuctionPlugin plugin, FileConfiguration config) {
        var listedAuctionLore = config.getStringList("item-lore.listed-auction-item");
        var multipleListedAuctionLore = config.getStringList("item-lore.multiple-listed-auction-item");
        var purchasedLore = config.getStringList("item-lore.purchased-item");
        var expiredLore = config.getStringList("item-lore.expired-item");
        var sellingLore = config.getStringList("item-lore.selling-item");
        var beingPurchasedLore = config.getStringList("item-lore.being-purchased-item");

        return new ItemLoreConfiguration(
                config.getBoolean("item-lore.force-amount-one", false),
                listedAuctionLore,
                ItemPlaceholder.detect(listedAuctionLore),
                multipleListedAuctionLore,
                ItemPlaceholder.detect(multipleListedAuctionLore),
                purchasedLore,
                ItemPlaceholder.detect(purchasedLore),
                expiredLore,
                ItemPlaceholder.detect(expiredLore),
                sellingLore,
                ItemPlaceholder.detect(sellingLore),
                beingPurchasedLore,
                ItemPlaceholder.detect(beingPurchasedLore),
                config.getStringList("item-lore.history-item"),
                config.getStringList("item-lore.admin-log-item"),
                config.getStringList("item-lore.admin-log-multiple-item"),
                config.getStringList("item-lore.sell-inventory-remove-item"),
                loadLogTypeNames(config),
                config.getString("item-lore.status.seller", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʀᴇᴛʀɪᴇᴠᴇ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.buyer", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʙᴜʏ ᴛʜɪs ɪᴛᴇᴍ"),
                // Ventes en lot : plus de mention du clic droit, qui ouvre l'apercu quand le lot contient un conteneur
                config.getString("item-lore.status.right-seller", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʀᴇᴛʀɪᴇᴠᴇ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.right-buyer", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʙᴜʏ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.container-preview", DEFAULT_CONTAINER_PREVIEW)
        );
    }

    private static Map<LogType, String> loadLogTypeNames(FileConfiguration config) {
        Map<LogType, String> names = new EnumMap<>(LogType.class);
        ConfigurationSection section = config.getConfigurationSection("item-lore.log-type-names");

        // Default values
        names.put(LogType.SALE, "Item Listed");
        names.put(LogType.PURCHASE, "Item Purchased");
        names.put(LogType.REMOVE_LISTED, "Removed from Listing");
        names.put(LogType.REMOVE_SELLING, "Retrieved Selling Item");
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
