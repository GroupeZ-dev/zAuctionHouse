package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;

public class ConfirmRemoveListedButton extends Button {

    private final AuctionPlugin plugin;

    public ConfirmRemoveListedButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int slot, Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        manager.getRemoveService().removeListedItem(player, manager.getCache(player).get(PlayerCacheKey.ITEM_SHOW));
    }
}
