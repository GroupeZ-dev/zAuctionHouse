package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.services.result.SellResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for listing items for sale in the auction house.
 * <p>
 * This service handles the complete selling flow including:
 * <ul>
 *     <li>Item validation against blacklist/whitelist rules</li>
 *     <li>Tax calculation and deduction</li>
 *     <li>Item removal from player inventory</li>
 *     <li>Listing creation and persistence</li>
 *     <li>Cluster notification for multi-server setups</li>
 * </ul>
 */
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
     * @return a future containing the result of the sell operation
     */
    CompletableFuture<SellResult> sellAuctionItems(Player player, BigDecimal price, long expiredAt, Map<Integer, ItemStack> slotItems, AuctionEconomy auctionEconomy);

    /**
     * Opens the sell command inventory for a player with pre-configured price and economy.
     * <p>
     * This inventory allows players to select items to sell and confirm the transaction.
     * The price and economy are pre-filled based on command arguments.
     *
     * @param player         the player to open the inventory for
     * @param price          the pre-configured price for the listing
     * @param auctionEconomy the economy to use for the transaction
     */
    void openSellCommandInventory(Player player, BigDecimal price, AuctionEconomy auctionEconomy);
}
