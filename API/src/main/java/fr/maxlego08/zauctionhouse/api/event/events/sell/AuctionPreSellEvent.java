package fr.maxlego08.zauctionhouse.api.event.events.sell;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.event.CancelledAuctionEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

public class AuctionPreSellEvent extends CancelledAuctionEvent {

    private final Player player;
    private int amount;
    private long expiredAt;
    private ItemStack itemStack;
    private AuctionEconomy auctionEconomy;
    private BigDecimal price;

    public AuctionPreSellEvent(Player player, int amount, long expiredAt, ItemStack itemStack, AuctionEconomy auctionEconomy, BigDecimal price) {
        this.player = player;
        this.amount = amount;
        this.expiredAt = expiredAt;
        this.itemStack = itemStack;
        this.auctionEconomy = auctionEconomy;
        this.price = price;
    }

    public Player getPlayer() {
        return player;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public long getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(long expiredAt) {
        this.expiredAt = expiredAt;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public AuctionEconomy getAuctionEconomy() {
        return auctionEconomy;
    }

    public void setAuctionEconomy(AuctionEconomy auctionEconomy) {
        this.auctionEconomy = auctionEconomy;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
