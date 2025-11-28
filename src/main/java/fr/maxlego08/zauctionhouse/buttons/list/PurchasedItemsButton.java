package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PurchasedItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public PurchasedItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();
        var items = manager.getPurchasedItems(player);
        var line = this.plugin.getConfiguration().getItemLore().purchasedLore();

        paginate(items, inventoryEngine, (slot, item) -> {
            inventoryEngine.addItem(slot, item.buildItemStack(player, line)).setClick(event -> {
                manager.getRemoveService().removePurchasedItem(player, item);
            });
        });
    }

    @Override
    public int getPaginationSize(Player player) {
        return this.plugin.getAuctionManager().getPurchasedItems(player).size();
    }
}
