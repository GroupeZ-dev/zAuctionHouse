package fr.maxlego08.zauctionhouse.buttons.confirm;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public class ConfirmPurchaseButton extends ConfirmHelper {

    public ConfirmPurchaseButton(Plugin plugin) {
        super((AuctionPlugin) plugin, ItemStatus.IS_PURCHASE_CONFIRM, ItemStatus.AVAILABLE);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        Item item = manager.getCache(player).get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) {
            manager.openMainAuction(player);
            return;
        }
        manager.getPurchaseService().purchaseItem(player, item);
    }
}
