package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SellShowItemButton extends Button {

    private final AuctionPlugin plugin;
    private final int emptySlot;

    public SellShowItemButton(AuctionPlugin plugin, int emptySlot) {
        this.plugin = plugin;
        this.emptySlot = emptySlot;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();
        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();
        var cache = manager.getCache(player);
        var config = this.plugin.getConfiguration().getItemLore().sellInventoryRemoveItem();

        List<ItemStack> sellItems = cache.get(PlayerCacheKey.SELL_ITEMS);

        this.slots.forEach(slot -> {
            inventoryEngine.getSpigotInventory().setItem(slot, null);
            inventoryEngine.removeItem(slot);
        });

        if (sellItems.isEmpty()) {
            inventoryEngine.addItem(this.emptySlot, this.getItemStack().build(player, false, new Placeholders()));
        } else {
            var maxIndex = Math.min(slots.size(), sellItems.size());
            for (int i = 0; i != maxIndex; i++) {

                var finalItemStack = sellItems.get(i);

                var itemStack = finalItemStack.clone();
                var itemMeta = itemStack.getItemMeta();
                meta.updateLore(itemMeta, config, LoreType.APPEND);
                itemStack.setItemMeta(itemMeta);

                var button = inventoryEngine.addItem(this.slots.get(i), itemStack);
                if (button == null) continue;

                button.setClick(e -> {
                    if (removeFromSellList(player, finalItemStack)) {
                        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
                    }
                });
            }
        }
    }

    @Override
    public boolean hasSpecialRender() {
        return true;
    }

    @Override
    public void onInventoryClick(@NotNull InventoryClickEvent event, @NotNull Player player, @NotNull InventoryEngine inventoryDefault) {
        super.onInventoryClick(event, player, inventoryDefault);

        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        event.setCancelled(true);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        var ruleManager = this.plugin.getItemRuleManager();

        if (ruleManager.isBlacklistEnabled() && ruleManager.isBlacklisted(clickedItem)) {
            manager.message(player, Message.ITEM_BLACKLISTED);
            return;
        }

        if (ruleManager.isWhitelistEnabled() && !ruleManager.isWhitelisted(clickedItem)) {
            manager.message(player, Message.ITEM_WHITELISTED);
            return;
        }

        List<ItemStack> sellItems = cache.get(PlayerCacheKey.SELL_ITEMS);
        if (sellItems == null) {
            sellItems = new ArrayList<>();
        }

        boolean found = false;
        for (int i = 0; i < sellItems.size(); i++) {
            if (sellItems.get(i).isSimilar(clickedItem)) {
                sellItems.remove(i);
                found = true;
                manager.message(player, Message.SELL_ITEM_REMOVED);
                break;
            }
        }

        if (!found) {
            sellItems.add(clickedItem.clone());
            manager.message(player, Message.SELL_ITEM_ADDED);
        }

        cache.set(PlayerCacheKey.SELL_ITEMS, sellItems);

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    public boolean removeFromSellList(Player player, ItemStack itemStack) {
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        List<ItemStack> sellItems = cache.get(PlayerCacheKey.SELL_ITEMS);
        if (sellItems == null || sellItems.isEmpty()) {
            return false;
        }

        boolean removed = sellItems.removeIf(item -> item.isSimilar(itemStack));
        cache.set(PlayerCacheKey.SELL_ITEMS, sellItems);
        return removed;
    }
}
