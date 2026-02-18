package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class TransactionStatusFilterButton extends Button {

    private final AuctionPlugin plugin;

    public TransactionStatusFilterButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        TransactionStatus currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_STATUS_FILTER);

        String displayName = currentFilter == null ? "All" : currentFilter.name();
        placeholders.register("current-status", displayName);

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);
        TransactionStatus currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_STATUS_FILTER);

        TransactionStatus nextFilter = getNextFilter(currentFilter, event.isRightClick());
        cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_STATUS_FILTER, nextFilter);
        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    private TransactionStatus getNextFilter(TransactionStatus current, boolean reverse) {
        TransactionStatus[] values = TransactionStatus.values();

        if (current == null) {
            return reverse ? values[values.length - 1] : values[0];
        }

        int index = current.ordinal();
        if (reverse) {
            return index == 0 ? null : values[index - 1];
        } else {
            return index == values.length - 1 ? null : values[index + 1];
        }
    }
}
