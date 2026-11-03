package fr.maxlego08.zauctionhouse.buttons.admin.history;

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

public abstract class AdminRemoveOptionButton extends Button {

    private final AuctionPlugin plugin;
    private final PlayerCacheKey key;
    private final String enableMaterial;
    private final String disableMaterial;

    protected AdminRemoveOptionButton(Plugin plugin, PlayerCacheKey key, String enableMaterial, String disableMaterial) {
        this.plugin = (AuctionPlugin) plugin;
        this.key = key;
        this.enableMaterial = enableMaterial;
        this.disableMaterial = disableMaterial;
    }

    @Override
    public ItemStack getCustomItemStack(Player player, boolean useCache, Placeholders placeholders) {
        boolean enabled = this.plugin.getAuctionManager().getCache(player).get(this.key);
        placeholders.register("enabled", String.valueOf(enabled));
        placeholders.register("state-color", enabled ? "#55ff55" : "#ff5555");
        placeholders.register("material", enabled ? this.enableMaterial : this.disableMaterial);
        return super.getCustomItemStack(player, false, placeholders);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine engine, int slot, Placeholders placeholders) {
        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission())) return;
        var cache = this.plugin.getAuctionManager().getCache(player);
        boolean enabled = cache.get(this.key);
        cache.set(this.key, !enabled);
        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }
}
