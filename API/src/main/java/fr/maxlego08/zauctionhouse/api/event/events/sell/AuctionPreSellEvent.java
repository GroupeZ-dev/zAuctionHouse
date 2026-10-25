package fr.maxlego08.zauctionhouse.api.event.events.sell;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.event.CancelledAuctionEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

/**
 * Event fired before a player lists an item for sale.
 * <p>
 * This event is cancellable and allows modification of the listing parameters
 * such as price, amount, expiration time, and economy before the item is listed.
 * <p>
 * Cancelling this event prevents the item from being listed.
 * <p>
 * This event is fired once for every listing, whatever the sell path (command,
 * sell inventory or GUI). When several stacks are listed together, the item stack
 * is the first stack and the amount is the total amount: changes to the item stack
 * and the amount are only honoured for single-stack listings, while price,
 * expiration and economy changes are always honoured.
 *
 * <pre>{@code
 * @EventHandler
 * public void onPreSell(AuctionPreSellEvent event) {
 *     // Add a 10% markup to all prices
 *     BigDecimal newPrice = event.getPrice().multiply(new BigDecimal("1.10"));
 *     event.setPrice(newPrice);
 * }
 * }</pre>
 */
public class AuctionPreSellEvent extends CancelledAuctionEvent {

    private final Player player;
    private int amount;
    private long expiredAt;
    private ItemStack itemStack;
    private AuctionEconomy auctionEconomy;
    private BigDecimal price;

    /**
     * Creates a new pre-sell event.
     *
     * @param player         the player listing the item
     * @param amount         the quantity of items being listed
     * @param expiredAt      the expiration timestamp in milliseconds
     * @param itemStack      the item being listed
     * @param auctionEconomy the economy to use for the transaction
     * @param price          the listing price
     */
    public AuctionPreSellEvent(Player player, int amount, long expiredAt, ItemStack itemStack, AuctionEconomy auctionEconomy, BigDecimal price) {
        this.player = player;
        this.amount = amount;
        this.expiredAt = expiredAt;
        this.itemStack = itemStack;
        this.auctionEconomy = auctionEconomy;
        this.price = price;
    }

    /**
     * Gets the player listing the item.
     *
     * @return the seller
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the quantity of items being listed.
     *
     * @return the item amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Sets the quantity of items to list.
     *
     * @param amount the new amount
     */
    public void setAmount(int amount) {
        this.amount = amount;
    }

    /**
     * Gets the expiration timestamp.
     *
     * @return expiration time in milliseconds since epoch
     */
    public long getExpiredAt() {
        return expiredAt;
    }

    /**
     * Sets the expiration timestamp.
     *
     * @param expiredAt expiration time in milliseconds since epoch
     */
    public void setExpiredAt(long expiredAt) {
        this.expiredAt = expiredAt;
    }

    /**
     * Gets the item being listed.
     *
     * @return the item stack
     */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * Sets the item to be listed.
     *
     * @param itemStack the new item stack
     */
    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    /**
     * Gets the economy to use for this listing.
     *
     * @return the auction economy
     */
    public AuctionEconomy getAuctionEconomy() {
        return auctionEconomy;
    }

    /**
     * Sets the economy to use for this listing.
     *
     * @param auctionEconomy the new economy
     */
    public void setAuctionEconomy(AuctionEconomy auctionEconomy) {
        this.auctionEconomy = auctionEconomy;
    }

    /**
     * Gets the listing price.
     *
     * @return the price
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Sets the listing price.
     *
     * @param price the new price
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
