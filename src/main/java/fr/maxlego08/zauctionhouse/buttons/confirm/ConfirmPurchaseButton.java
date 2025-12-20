package fr.maxlego08.zauctionhouse.buttons.confirm;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class ConfirmPurchaseButton extends ConfirmHelper {

    public ConfirmPurchaseButton(Plugin plugin) {
        super((AuctionPlugin) plugin, ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.AVAILABLE);
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int slot, Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        manager.getPurchaseService().purchaseItem(player, manager.getCache(player).get(PlayerCacheKey.ITEM_SHOW));
    }
}
