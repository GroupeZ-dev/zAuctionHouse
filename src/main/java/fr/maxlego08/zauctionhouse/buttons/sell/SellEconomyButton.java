package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
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
 * <p>
 * With an {@code economy} option the button selects that economy instead of cycling.
 */
public class SellEconomyButton extends Button {

    private final AuctionPlugin plugin;
    // null = le bouton fait defiler les economies
    private final String economyName;

    public SellEconomyButton(Plugin plugin) {
        this(plugin, null);
    }

    public SellEconomyButton(Plugin plugin, String economyName) {
        this.plugin = (AuctionPlugin) plugin;
        this.economyName = economyName;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int slot, Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        var economyManager = this.plugin.getEconomyManager();

        List<AuctionEconomy> economies = new ArrayList<>(economyManager.getEconomies());
        if (economies.isEmpty()) return;

        AuctionEconomy defaultEconomy = economyManager.getDefaultEconomy(ItemType.AUCTION);
        if (defaultEconomy == null) {
            this.plugin.getAuctionManager().message(player, Message.SELL_ERROR_DEFAULT_ECONOMY);
            return;
        }
        AuctionEconomy currentEconomy = cache.get(PlayerCacheKey.SELL_ECONOMY, defaultEconomy);
        int currentIndex = economies.indexOf(currentEconomy);
        if (currentIndex < 0) currentIndex = 0;

        AuctionEconomy newEconomy;
        if (this.economyName != null) {
            var optional = economyManager.getEconomy(this.economyName);
            if (optional.isEmpty()) {
                this.plugin.getAuctionManager().message(player, Message.SELL_ERROR_ECONOMY, "%name%", this.economyName);
                return;
            }
            newEconomy = optional.get();
        } else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            // Previous economy
            newEconomy = economies.get((currentIndex - 1 + economies.size()) % economies.size());
        } else {
            // Next economy
            newEconomy = economies.get((currentIndex + 1) % economies.size());
        }

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

        AuctionEconomy defaultEconomy = economyManager.getDefaultEconomy(ItemType.AUCTION);
        if (defaultEconomy == null) return menuItemStack.build(player, false, placeholders);
        AuctionEconomy selectedEconomy = cache.get(PlayerCacheKey.SELL_ECONOMY, defaultEconomy);
        // Un bouton a economie fixe affiche SON economie, et indique si elle est selectionnee
        AuctionEconomy economy = this.economyName == null ? selectedEconomy : economyManager.getEconomy(this.economyName).orElse(selectedEconomy);
        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);

        placeholders.register("selected", String.valueOf(economy.equals(selectedEconomy)));
        placeholders.register("economy", economy.getDisplayName());
        placeholders.register("economy_name", economy.getName());
        placeholders.register("price", economyManager.format(economy, price));

        return menuItemStack.build(player, false, placeholders);
    }
}
