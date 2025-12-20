package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class SellSlotButton extends Button {

    @Override
    public void onInventoryOpen(Player player, InventoryEngine inventory, Placeholders placeholders) {
        super.onInventoryOpen(player, inventory, placeholders);
        inventory.setDisableClick(false);
        inventory.setDisablePlayerInventoryClick(false);
    }

    @Override
    public void onRender(Player player, InventoryEngine inventory) {
    }

    @Override
    public boolean hasSpecialRender() {
        return true;
    }

    @Override
    public void onInventoryClose(Player player, InventoryEngine inventory) {
        super.onInventoryClose(player, inventory);
        Inventory spigotInventory = inventory.getSpigotInventory();

        this.slots.forEach(slot -> {
            ItemStack itemStack = spigotInventory.getItem(slot);
            if (itemStack != null) {
                give(player, itemStack);
            }
        });
    }

    protected boolean hasInventoryFull(Player player) {
        int slot = 0;
        PlayerInventory inventory = player.getInventory();
        for (int a = 0; a != 36; a++) {
            ItemStack itemStack = inventory.getContents()[a];
            if (itemStack == null) slot++;
        }
        return slot == 0;
    }

    protected void give(Player player, ItemStack item) {
        if (hasInventoryFull(player)) player.getWorld().dropItem(player.getLocation(), item);
        else player.getInventory().addItem(item);
    }

}
