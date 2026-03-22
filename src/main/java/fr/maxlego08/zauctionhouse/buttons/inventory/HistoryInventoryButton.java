package fr.maxlego08.zauctionhouse.buttons.inventory;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public class HistoryInventoryButton extends Button {

    private final AuctionPlugin plugin;

    public HistoryInventoryButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.HISTORY);
    }

    @Override
    public boolean isPermanent() {
        return true;
    }
}
