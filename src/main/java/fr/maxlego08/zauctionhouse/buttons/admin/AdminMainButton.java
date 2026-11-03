package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;

public class AdminMainButton extends Button {

    private final AuctionPlugin plugin;

    public AdminMainButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine engine, int slot, Placeholders placeholders) {
        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS.asPermission())) return;
        this.plugin.getAuctionManager().getCache(player).remove(PlayerCacheKey.ADMIN_PLAYERS_DATA);
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.ADMIN_MAIN);
    }
}
