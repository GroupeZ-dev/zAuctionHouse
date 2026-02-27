package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Button to cycle through available economies for selling.
 * Left click goes to next economy, right click goes to previous.
 */
public class SellEconomyButton extends Button {

    private final AuctionPlugin plugin;

    public SellEconomyButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int slot, Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        var economyManager = this.plugin.getEconomyManager();

        List<AuctionEconomy> economies = new ArrayList<>(economyManager.getEconomies());
        if (economies.isEmpty()) return;

        AuctionEconomy currentEconomy = cache.get(PlayerCacheKey.SELL_ECONOMY, economyManager.getDefaultEconomy(ItemType.AUCTION));
        int currentIndex = economies.indexOf(currentEconomy);
        if (currentIndex < 0) currentIndex = 0;

        int newIndex;
        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            // Previous economy
            newIndex = (currentIndex - 1 + economies.size()) % economies.size();
        } else {
            // Next economy
            newIndex = (currentIndex + 1) % economies.size();
        }

        AuctionEconomy newEconomy = economies.get(newIndex);
        cache.set(PlayerCacheKey.SELL_ECONOMY, newEconomy);

        // Validate price against new economy limits
        BigDecimal currentPrice = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        BigDecimal minPrice = newEconomy.getMinPrice(ItemType.AUCTION);
        BigDecimal maxPrice = newEconomy.getMaxPrice(ItemType.AUCTION);

        if (currentPrice.compareTo(minPrice) < 0) {
            cache.set(PlayerCacheKey.SELL_PRICE, minPrice);
        } else if (currentPrice.compareTo(maxPrice) > 0) {
            cache.set(PlayerCacheKey.SELL_PRICE, maxPrice);
        }

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        MenuItemStack menuItemStack = this.getItemStack();

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        var economyManager = this.plugin.getEconomyManager();

        AuctionEconomy economy = cache.get(PlayerCacheKey.SELL_ECONOMY, economyManager.getDefaultEconomy(ItemType.AUCTION));
        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);

        placeholders.register("economy", economy.getDisplayName());
        placeholders.register("economy_name", economy.getName());
        placeholders.register("price", economyManager.format(economy, price));

        return menuItemStack.build(player, false, placeholders);
    }
}
