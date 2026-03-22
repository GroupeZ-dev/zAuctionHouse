package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public class SellingItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;
    private final int emptySlot;

    public SellingItemsButton(Plugin plugin, int emptySlot) {
        this.plugin = (AuctionPlugin) plugin;
        this.emptySlot = emptySlot;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();
        var items = manager.getPlayerSellingItems(player);

        if (items.isEmpty()) {
            if (this.emptySlot == -1) return;
            inventoryEngine.addItem(this.emptySlot, getCustomItemStack(player, false, new Placeholders()));
            return;
        }

        var line = this.plugin.getConfiguration().getItemLore().sellingLore();
        var linePurchased = this.plugin.getConfiguration().getItemLore().beingPurchasedLore();

        paginate(items, inventoryEngine, (slot, item) -> {
            inventoryEngine.addItem(slot, item.buildItemStack(player, item.getStatus() == ItemStatus.AVAILABLE ? line : linePurchased)).setClick(event -> {
                this.plugin.getAuctionManager().getRemoveService().removeSellingItem(player, item);
            });
        });
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return this.plugin.getAuctionManager().getPlayerSellingItems(player).size();
    }
}
