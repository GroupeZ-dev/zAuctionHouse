package fr.maxlego08.zauctionhouse.api.items;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

public interface Item {

    int getId();

    UUID getSellerUniqueId();

    String getSellerName();

    BigDecimal getPrice();

    AuctionEconomy getAuctionEconomy();

    OfflinePlayer getSeller();

    Date getExpiredAt();

    Date getCreatedAt();

    ItemStack buildItemStack(Player player);
}
