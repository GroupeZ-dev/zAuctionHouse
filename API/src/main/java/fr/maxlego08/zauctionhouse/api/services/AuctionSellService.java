package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public interface AuctionSellService {

    void sellAuctionItem(Player player, BigDecimal price, int amount, long expiredAt, ItemStack itemStack, AuctionEconomy auctionEconomy);

    void sellAuctionItems(Player player, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy);

    void openSellInventory(Player player, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy);

    /**
     * Opens the sell command inventory where the player can select items,
     * choose economy, and set the price before confirming the sale.
     *
     * @param player the player opening the inventory
     */
    void openSellCommandInventory(Player player);
}
