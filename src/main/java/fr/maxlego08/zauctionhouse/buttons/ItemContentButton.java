package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.AdminLogItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ItemContentButton extends Button {

    private final AuctionPlugin plugin;

    public ItemContentButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        List<ItemStack> items = getItemStacks(player);
        if (items.isEmpty()) return;

        paginate(items, inventoryEngine, inventoryEngine::addItem);
    }

    @Override
    public boolean hasSpecialRender() {
        return true;
    }

    private List<ItemStack> getItemStacks(Player player) {
        var cache = this.plugin.getAuctionManager().getCache(player);

        // Check for AuctionItem first
        var item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item instanceof AuctionItem auctionItem && auctionItem.getItemStacks() != null) {
            // C-038 : un contenu illisible laisse un null dans la liste ; sans ce filtre le
            // rendu part en NPE sur ItemStack::clone.
            return auctionItem.getItemStacks().stream().filter(Objects::nonNull).map(ItemStack::clone).toList();
        }

        // Check for AdminLogItem (used by admin logs)
        AdminLogItem adminLogItem = cache.get(PlayerCacheKey.ADMIN_LOG_SELECTED);
        if (adminLogItem != null && adminLogItem.itemStacks() != null) {
            return adminLogItem.itemStacks().stream().map(ItemStack::clone).toList();
        }

        return Collections.emptyList();
    }
}
