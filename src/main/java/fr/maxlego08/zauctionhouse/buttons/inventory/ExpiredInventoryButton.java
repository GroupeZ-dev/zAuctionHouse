package fr.maxlego08.zauctionhouse.buttons.inventory;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class ExpiredInventoryButton extends Button {

    private final AuctionPlugin plugin;

    public ExpiredInventoryButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public boolean hasPermission() {
        return true;
    }

    @Override
    public boolean checkPermission(Player player, InventoryEngine inventory, Placeholders placeholders) {
        var list = this.plugin.getAuctionManager().getExpiredItems(player);
        return super.checkPermission(player, inventory, placeholders) && !list.isEmpty();
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int slot, Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.EXPIRED_ITEMS);
    }

    @Override
    public ItemStack getCustomItemStack(Player player) {

        var list = this.plugin.getAuctionManager().getExpiredItems(player);

        Placeholders placeholders = new Placeholders();
        placeholders.register("expired-items", String.valueOf(list.size()));
        placeholders.register("s", list.size() > 1 ? "s" : "");
        return getItemStack().build(player, false, placeholders);
    }

    @Override
    public boolean isPermanent() {
        return true;
    }
}
