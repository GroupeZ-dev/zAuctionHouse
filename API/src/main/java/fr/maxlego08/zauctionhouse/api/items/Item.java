package fr.maxlego08.zauctionhouse.api.items;

import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface Item {

    int getId();

    UUID getSellerUniqueId();

    String getSellerName();

    BigDecimal getPrice();

    AuctionEconomy getAuctionEconomy();

    OfflinePlayer getSeller();

    Date getExpiredAt();

    void setExpiredAt(Date expiredAt);

    Date getCreatedAt();

    ItemStack buildItemStack(Player player);

    ItemStack buildItemStack(Player player, List<String> lore);

    Placeholders createPlaceholders(Player player);

    String createStatus(Player player);

    String getFormattedPrice();

    String getFormattedExpireDate();

    String getRemainingTime();

    boolean isExpired();

    ItemStatus getStatus();

    void setStatus(ItemStatus status);

    boolean canReceiveItem(Player player);

    int getAmount();

    String getTranslationKey();

    UUID getBuyerUniqueId();

    String getBuyerName();

    void setBuyer(Player player);
}
