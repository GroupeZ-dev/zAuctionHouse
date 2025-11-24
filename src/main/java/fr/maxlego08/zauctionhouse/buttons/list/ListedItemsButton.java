package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

public class ListedItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public ListedItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();
        var items = manager.getItemsListedForSale(player);

        paginate(items, inventoryEngine, (slot, item) -> {
            inventoryEngine.addItem(slot, item.buildItemStack(player)).setClick(event -> {

                if ((event.getClick() == ClickType.DROP || event.getClick() == ClickType.MIDDLE) && player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission())) {

                    // ToDo

                    return;
                }

                if (item.getSellerUniqueId().equals(player.getUniqueId())) {

                    // Remove item
                    var cache = manager.getCache(player);
                    cache.set(PlayerCacheKey.ITEM_SHOW, item);
                    cache.set(PlayerCacheKey.CURRENT_PAGE, this.plugin.getInventoriesLoader().getInventoryManager().getPage(player));

                    this.plugin.getInventoriesLoader().openInventory(player, Inventories.REMOVE_CONFIRM);
                    // manager.getRemoveService().removeListedItem(player, item);
                } else {

                    // Purchase items
                    manager.getPurchaseService().purchaseItem(player, item);
                }
            });
        });
    }

    @Override
    public int getPaginationSize(Player player) {
        return this.plugin.getAuctionManager().getItemsListedForSale(player).size();
    }
}
