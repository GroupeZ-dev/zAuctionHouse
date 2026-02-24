package fr.maxlego08.zauctionhouse.buttons.confirm;

import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;

public abstract class ConfirmHelper extends Button {

    protected final AuctionPlugin plugin;
    private final ItemStatus previous;
    private final ItemStatus next;

    public ConfirmHelper(AuctionPlugin plugin, ItemStatus previous, ItemStatus next) {
        this.plugin = plugin;
        this.previous = previous;
        this.next = next;
    }

    @Override
    public void onInventoryClose(@NonNull Player player, @NonNull InventoryEngine inventory) {
        super.onInventoryClose(player, inventory);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        // Si lors de la fermeture, le status est similaire à celui de l'ouverture, alors l'état n'a pas changé, et donc on doit mettre le prochain état de l'item.
        if (item.getStatus() == this.previous) {
            item.setStatus(this.next);
            this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next);

        }
        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED);
        manager.updateListedItems(item, true, player);
    }

    @Override
    public void onBackClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, @NonNull List<Inventory> oldInventories, @NonNull Inventory toInventory, int slot) {
        super.onBackClick(player, event, inventory, oldInventories, toInventory, slot);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        item.setStatus(this.next);
        this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next);

        manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_LISTED);
    }
}
