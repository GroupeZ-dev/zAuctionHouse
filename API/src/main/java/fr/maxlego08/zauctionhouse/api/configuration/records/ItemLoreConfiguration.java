package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record ItemLoreConfiguration(
        List<String> listedAuctionLore,
        List<String> purchasedLore,
        List<String> expiredLore,
        List<String> ownedLore,
        String sellerStatus,
        String buyerStatus
) {
    public static ItemLoreConfiguration of(AuctionPlugin plugin, FileConfiguration config) {
        return new ItemLoreConfiguration(
                config.getStringList("item-lore.listed-auction-item"),
                config.getStringList("item-lore.purchased-item"),
                config.getStringList("item-lore.expired-item"),
                config.getStringList("item-lore.owned-item"),
                config.getString("item-lore.status.seller", "<#8c8c8c>• <#92bed8>ᴄʟɪᴄᴋ <#e6fff3>ᴛᴏ ʀᴇᴛʀɪᴇᴠᴇ ᴛʜɪs ɪᴛᴇᴍ"),
                config.getString("item-lore.status.buyer", "<#8c8c8c>• <#92bed8>ᴄʟɪᴄᴋ <#e6fff3>ᴛᴏ ʙᴜʏ ᴛʜɪs ɪᴛᴇᴍ")
        );
    }
}
