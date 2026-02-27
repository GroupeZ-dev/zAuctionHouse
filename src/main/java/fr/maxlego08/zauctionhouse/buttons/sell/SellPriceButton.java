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

/**
 * Button to increase or decrease the sell price.
 * Configurable amounts for left click, right click, and shift clicks.
 */
public class SellPriceButton extends Button {

    private final AuctionPlugin plugin;
    private final BigDecimal leftClickAmount;
    private final BigDecimal rightClickAmount;
    private final BigDecimal shiftLeftClickAmount;
    private final BigDecimal shiftRightClickAmount;

    public SellPriceButton(Plugin plugin, BigDecimal leftClickAmount, BigDecimal rightClickAmount, BigDecimal shiftLeftClickAmount, BigDecimal shiftRightClickAmount) {
        this.plugin = (AuctionPlugin) plugin;
        this.leftClickAmount = leftClickAmount;
        this.rightClickAmount = rightClickAmount;
        this.shiftLeftClickAmount = shiftLeftClickAmount;
        this.shiftRightClickAmount = shiftRightClickAmount;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int slot, Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        BigDecimal currentPrice = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy economy = cache.get(PlayerCacheKey.SELL_ECONOMY, this.plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION));

        BigDecimal amount = getAmountForClick(event.getClick());
        BigDecimal newPrice = currentPrice.add(amount);

        // Ensure price doesn't go below minimum
        BigDecimal minPrice = economy.getMinPrice(ItemType.AUCTION);
        if (newPrice.compareTo(minPrice) < 0) {
            newPrice = minPrice;
        }

        // Ensure price doesn't exceed maximum
        BigDecimal maxPrice = economy.getMaxPrice(ItemType.AUCTION);
        if (newPrice.compareTo(maxPrice) > 0) {
            newPrice = maxPrice;
        }

        cache.set(PlayerCacheKey.SELL_PRICE, newPrice);
        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    private BigDecimal getAmountForClick(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> leftClickAmount;
            case RIGHT -> rightClickAmount;
            case SHIFT_LEFT -> shiftLeftClickAmount;
            case SHIFT_RIGHT -> shiftRightClickAmount;
            default -> leftClickAmount;
        };
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        MenuItemStack menuItemStack = this.getItemStack();

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy economy = cache.get(PlayerCacheKey.SELL_ECONOMY, this.plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION));

        placeholders.register("price", this.plugin.getEconomyManager().format(economy, price));
        placeholders.register("economy", economy.getDisplayName());
        placeholders.register("left_click_amount", formatAmount(leftClickAmount));
        placeholders.register("right_click_amount", formatAmount(rightClickAmount));
        placeholders.register("shift_left_click_amount", formatAmount(shiftLeftClickAmount));
        placeholders.register("shift_right_click_amount", formatAmount(shiftRightClickAmount));

        return menuItemStack.build(player, false, placeholders);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) >= 0) {
            return "+" + amount.stripTrailingZeros().toPlainString();
        }
        return amount.stripTrailingZeros().toPlainString();
    }
}
