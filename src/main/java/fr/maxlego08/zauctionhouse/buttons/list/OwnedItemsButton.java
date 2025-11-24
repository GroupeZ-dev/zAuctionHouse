package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class OwnedItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public OwnedItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();
        var items = manager.getPlayerOwnedItems(player);


        paginate(items, inventoryEngine, (slot, item) -> {
            inventoryEngine.addItem(slot, item.buildItemStack(player)).setClick(event -> {
                // ToDo
            });
        });
    }

    @Override
    public int getPaginationSize(Player player) {
        return this.plugin.getAuctionManager().getPlayerOwnedItems(player).size();
    }
}
