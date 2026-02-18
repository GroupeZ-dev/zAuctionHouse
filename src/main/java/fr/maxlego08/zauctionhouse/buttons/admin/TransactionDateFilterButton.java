package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class TransactionDateFilterButton extends Button {

    private final AuctionPlugin plugin;

    public TransactionDateFilterButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        DateFilter currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_DATE_FILTER, DateFilter.ALL);

        placeholders.register("current-date", currentFilter.getDisplayName());

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);
        DateFilter currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_DATE_FILTER, DateFilter.ALL);

        DateFilter nextFilter = getNextFilter(currentFilter, event.isRightClick());
        cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_DATE_FILTER, nextFilter);
        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    private DateFilter getNextFilter(DateFilter current, boolean reverse) {
        DateFilter[] values = DateFilter.values();
        int index = current.ordinal();

        if (reverse) {
            return values[(index - 1 + values.length) % values.length];
        } else {
            return values[(index + 1) % values.length];
        }
    }
}
