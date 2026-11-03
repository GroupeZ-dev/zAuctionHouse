package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public class AdminRemoveConfirmButton extends Button {
    private final AuctionPlugin plugin;

    public AdminRemoveConfirmButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    public static void open(AuctionPlugin plugin, Player player, Item item) {
        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission()) || !item.isActivelyListed())
            return;
        ConfirmHelper.releaseConfirmation(plugin, player);
        var cache = plugin.getAuctionManager().getCache(player);
        cache.set(PlayerCacheKey.ITEM_SHOW, item);
        cache.set(PlayerCacheKey.ADMIN_REMOVE_NOTIFY, true);
        cache.set(PlayerCacheKey.ADMIN_REMOVE_EXPIRE, true);
        cache.set(PlayerCacheKey.ADMIN_REMOVE_COPY, false);
        plugin.getInventoriesLoader().openInventory(player, Inventories.ADMIN_REMOVE_CONFIRM);
    }

    @Override
    public void onClick(Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine engine, int slot, @NonNull Placeholders placeholders) {

        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission())) return;

        var manager = plugin.getAuctionManager();
        var cache = manager.getCache(player);

        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null || !item.isActivelyListed() || manager.getItem(StorageType.LISTED, item.getId()) != item) {
            this.plugin.getInventoriesLoader().openInventory(player, Inventories.AUCTION, 1, false);
            return;
        }

        manager.adminRemoveItem(player, item.getSellerUniqueId(), item, StorageType.LISTED, cache.get(PlayerCacheKey.ADMIN_REMOVE_NOTIFY), cache.get(PlayerCacheKey.ADMIN_REMOVE_EXPIRE), cache.get(PlayerCacheKey.ADMIN_REMOVE_COPY), false);

        cache.remove(PlayerCacheKey.ITEM_SHOW);
        cache.remove(PlayerCacheKey.ADMIN_REMOVE_NOTIFY, PlayerCacheKey.ADMIN_REMOVE_EXPIRE, PlayerCacheKey.ADMIN_REMOVE_COPY);

        manager.openMainAuction(player);
    }
}
