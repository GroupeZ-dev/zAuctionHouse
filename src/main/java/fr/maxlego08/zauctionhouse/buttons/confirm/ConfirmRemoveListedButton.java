package fr.maxlego08.zauctionhouse.buttons.confirm;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.services.RemoveService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public class ConfirmRemoveListedButton extends ConfirmHelper {

    public ConfirmRemoveListedButton(Plugin plugin) {
        super((AuctionPlugin) plugin, ItemStatus.IS_REMOVE_CONFIRM, ItemStatus.AVAILABLE);
    }

    @Override
    protected void onPostClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders, AuctionManager manager, Item item) {
        // Seul ce chemin peut traiter une annonce en IS_REMOVE_CONFIRM (cf. RemoveService).
        if (manager.getRemoveService() instanceof RemoveService removeService) {
            removeService.removeListedItem(player, item, true);
        } else {
            manager.getRemoveService().removeListedItem(player, item);
        }
    }
}
