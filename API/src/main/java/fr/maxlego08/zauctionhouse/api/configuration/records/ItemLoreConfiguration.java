package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record ItemLoreConfiguration(
        List<String> listedAuctionLore,
        List<String> multipleListedAuctionLore,
        List<String> purchasedLore,
        List<String> expiredLore,
        List<String> ownedLore,
        List<String> beingPurchasedLore,
        List<String> historyLore,
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
                config.getString("item-lore.status.seller", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʀᴇᴛʀɪᴇᴠᴇ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.buyer", "#8c8c8c• #2CCED2ᴄʟɪᴄᴋ #92ffffᴛᴏ ʙᴜʏ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.right-seller", "#8c8c8c• #2CCED2ʀɪɢʜᴛ ᴄʟɪᴄᴋ #92ffffᴛᴏ ʀᴇᴛʀɪᴇᴠᴇ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.right-buyer", "#8c8c8c• #2CCED2ʀɪɢʜᴛ ᴄʟɪᴄᴋ #92ffffᴛᴏ ʙᴜʏ ᴛʜɪs ɪᴛᴇᴍ")
        );
    }
}
