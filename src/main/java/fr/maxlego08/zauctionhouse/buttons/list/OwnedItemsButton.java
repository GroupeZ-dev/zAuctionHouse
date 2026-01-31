package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public class OwnedItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public OwnedItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();
        var items = manager.getPlayerOwnedItems(player);
        var line = this.plugin.getConfiguration().getItemLore().ownedLore();
        var linePurchased = this.plugin.getConfiguration().getItemLore().beingPurchasedLore();

        paginate(items, inventoryEngine, (slot, item) -> {
            inventoryEngine.addItem(slot, item.buildItemStack(player, item.getStatus() == ItemStatus.AVAILABLE ? line : linePurchased)).setClick(event -> {
                this.plugin.getAuctionManager().getRemoveService().removeOwnedItem(player, item);
            });
        });
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return this.plugin.getAuctionManager().getPlayerOwnedItems(player).size();
    }
}
