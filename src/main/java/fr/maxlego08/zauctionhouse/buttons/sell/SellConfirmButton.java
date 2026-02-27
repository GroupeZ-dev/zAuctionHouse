package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Button to confirm the sale of selected items.
 * Shows a summary of the price and economy, and processes the sale when clicked.
 */
public class SellConfirmButton extends Button {

    private final AuctionPlugin plugin;

    public SellConfirmButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onInventoryOpen(@NotNull Player player, @NotNull InventoryEngine inventory, @NotNull Placeholders placeholders) {
        createPlaceholders(placeholders, player);
        super.onInventoryOpen(player, inventory, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);

        List<ItemStack> sellItems = cache.get(PlayerCacheKey.SELL_ITEMS);
        if (sellItems == null || sellItems.isEmpty()) {
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            if (mainHandItem.getType().isAir()) {
                this.plugin.getAuctionManager().message(player, Message.SELL_INVENTORY_EMPTY);
                return;
            }
            sellItems = new ArrayList<>();
            sellItems.add(mainHandItem.clone());
        }

        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy economy = cache.get(PlayerCacheKey.SELL_ECONOMY, this.plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION));
        long expiredAt = cache.get(PlayerCacheKey.SELL_EXPIRED_AT, 0L);

        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);

        List<ItemStack> itemsFromCache = cache.get(PlayerCacheKey.SELL_ITEMS);

        if (itemsFromCache == null || itemsFromCache.isEmpty()) {
            player.getInventory().setItemInMainHand(null);
        } else {
            for (ItemStack item : sellItems) {
                player.getInventory().removeItem(item);
            }
        }

        cache.remove(PlayerCacheKey.SELL_ITEMS);

        manager.getSellService().sellAuctionItems(player, price, expiredAt, sellItems, economy);

        player.closeInventory();
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        createPlaceholders(placeholders, player);
        return getItemStack().build(player, false, placeholders);
    }

    private void createPlaceholders(Placeholders placeholders, Player player) {
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        var economyManager = this.plugin.getEconomyManager();

        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy economy = cache.get(PlayerCacheKey.SELL_ECONOMY, economyManager.getDefaultEconomy(ItemType.AUCTION));

        List<ItemStack> sellItems = cache.get(PlayerCacheKey.SELL_ITEMS);
        int itemCount = 0;
        int totalAmount = 0;

        if (sellItems != null && !sellItems.isEmpty()) {
            itemCount = sellItems.size();
            totalAmount = sellItems.stream().filter(item -> item != null && !item.getType().isAir()).mapToInt(ItemStack::getAmount).sum();
        }

        placeholders.register("price", economyManager.format(economy, price));
        placeholders.register("economy", economy.getDisplayName());
        placeholders.register("economy_name", economy.getName());
        placeholders.register("item_count", String.valueOf(itemCount));
        placeholders.register("total_amount", String.valueOf(totalAmount));
    }

}
