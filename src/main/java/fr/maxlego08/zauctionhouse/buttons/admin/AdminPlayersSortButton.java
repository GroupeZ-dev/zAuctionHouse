package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class AdminPlayersSortButton extends Button {

    private final AuctionPlugin plugin;

    public AdminPlayersSortButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(Player player, boolean useCache, Placeholders placeholders) {
        int sort = this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ADMIN_PLAYERS_SORT);
        String[] names = {"name", "listed", "sold", "purchased"};
        for (int index = 0; index < names.length; index++) {
            placeholders.register(names[index] + "-color", sort == index ? "#2CCED2" : "#8c8c8c");
        }
        return super.getCustomItemStack(player, false, placeholders);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine engine, int slot, Placeholders placeholders) {
        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS.asPermission())) return;
        var cache = this.plugin.getAuctionManager().getCache(player);
        int sort = cache.get(PlayerCacheKey.ADMIN_PLAYERS_SORT);
        cache.set(PlayerCacheKey.ADMIN_PLAYERS_SORT, Math.floorMod(sort + (event.isRightClick() ? -1 : 1), 4));
        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }
}
