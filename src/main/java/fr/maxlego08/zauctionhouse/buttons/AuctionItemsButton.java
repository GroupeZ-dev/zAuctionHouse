package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class AuctionItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public AuctionItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var cache = this.plugin.getAuctionManager().getCache(player);
        var item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (!(item instanceof AuctionItem auctionItem)) return;

        List<ItemStack> items = auctionItem.getItemStacks().stream().map(ItemStack::clone).toList();
        paginate(items, inventoryEngine, (slot, itemStack) -> inventoryEngine.addItem(slot, itemStack));
    }

    @Override
    public int getPaginationSize(Player player) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        var item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (!(item instanceof AuctionItem auctionItem)) return 0;
        return auctionItem.getItemStacks().size();
    }
}
