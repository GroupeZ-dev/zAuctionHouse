package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.items.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

public class ZAuctionItem extends ZItem implements AuctionItem {

    private final ItemStack itemStack;

    public ZAuctionItem(AuctionPlugin plugin, int id, UUID sellerUniqueId, String sellerName, BigDecimal price, AuctionEconomy auctionEconomy, Date createdAt, Date expiredAt, ItemStack itemStack) {
        super(plugin, id, sellerUniqueId, sellerName, price, auctionEconomy, createdAt, expiredAt);
        this.itemStack = itemStack;
    }

    @Override
    public ItemStack getItemStack() {
        return itemStack;
    }

    @Override
    public ItemStack buildItemStack(Player player) {
        return this.itemStack.clone();
    }
}
