package fr.maxlego08.zauctionhouse.listeners;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.inventory.SellInventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SellInventoryListener implements Listener {

    private final AuctionPlugin plugin;

    public SellInventoryListener(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot == SellInventoryHolder.CONFIRM_SLOT) {
            event.setCancelled(true);
            this.handleConfirm(player, holder);
            return;
        }

        if (rawSlot == SellInventoryHolder.CANCEL_SLOT) {
            event.setCancelled(true);
            this.handleCancel(player, holder);
            return;
        }

        if (rawSlot < event.getInventory().getSize() && holder.isLockedSlot(rawSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellInventoryHolder holder)) return;
        if (holder.isCompleted()) return;
        if (event.getPlayer() instanceof Player player) {
            this.returnItems(player, holder);
            this.plugin.getAuctionManager().message(player, Message.SELL_INVENTORY_CANCELLED);
        }
    }

    private void handleConfirm(Player player, SellInventoryHolder holder) {

        if (holder.isCompleted()) return;

        var items = this.getSellableItems(holder);
        if (items.isEmpty()) {
            this.plugin.getAuctionManager().message(player, Message.SELL_INVENTORY_EMPTY);
            return;
        }

        holder.setCompleted(true);
        player.closeInventory();

        this.plugin.getAuctionManager().getCache(player).set(PlayerCacheKey.CURRENT_PAGE, 1);
        this.plugin.getAuctionManager().getSellService().sellAuctionItems(player, holder.getPrice(), holder.getExpiredAt(), items, holder.getAuctionEconomy());
    }

    private void handleCancel(Player player, SellInventoryHolder holder) {
        if (holder.isCompleted()) return;
        holder.setCompleted(true);
        this.returnItems(player, holder);
        player.closeInventory();
        this.plugin.getAuctionManager().message(player, Message.SELL_INVENTORY_CANCELLED);
    }

    private List<ItemStack> getSellableItems(SellInventoryHolder holder) {
        List<ItemStack> sellableItems = new ArrayList<>();
        var inventory = holder.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (holder.isLockedSlot(slot)) continue;
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null) continue;
            sellableItems.add(itemStack);
            inventory.setItem(slot, null);
        }
        return sellableItems;
    }

    private void returnItems(Player player, SellInventoryHolder holder) {
        var inventory = holder.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (holder.isLockedSlot(slot)) continue;
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null) continue;
            player.getInventory().addItem(itemStack).forEach((index, remaining) -> player.getWorld().dropItem(player.getLocation(), remaining));
            inventory.setItem(slot, null);
        }
    }
}
