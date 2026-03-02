package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Map;

public interface AuctionSellService {

    /**
     * Slot constant representing the player's main hand.
     * Use this value as a key in the slotItems map when selling from the main hand.
     */
    int MAIN_HAND_SLOT = -1;

    /**
     * Sells auction items from the player's inventory.
     * Items are verified to still be in their original slots after async tax verification
     * before being removed from the inventory.
     *
     * @param player         the player selling the items
     * @param price          the total price for all items
     * @param expiredAt      the expiration timestamp (0 for no expiration)
     * @param slotItems      map of inventory slot to ItemStack (use MAIN_HAND_SLOT for main hand)
     * @param auctionEconomy the economy to use for the transaction
     */
    void sellAuctionItems(Player player, BigDecimal price, long expiredAt, Map<Integer, ItemStack> slotItems, AuctionEconomy auctionEconomy);

    void openSellCommandInventory(Player player, BigDecimal price, AuctionEconomy auctionEconomy);
}
