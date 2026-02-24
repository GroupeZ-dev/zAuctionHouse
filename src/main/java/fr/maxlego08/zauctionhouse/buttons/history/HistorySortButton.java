package fr.maxlego08.zauctionhouse.buttons.history;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.history.HistorySortType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Button that allows sorting the sales history.
 */
public class HistorySortButton extends Button {

    private final AuctionPlugin plugin;
    private final String enableText;
    private final String disableText;
    private final List<HistorySortType> sortTypes;
    private final Map<HistorySortType, String> sortNames;

    public HistorySortButton(AuctionPlugin plugin, String enableText, String disableText,
                             List<HistorySortType> sortTypes, Map<HistorySortType, String> sortNames) {
        this.plugin = plugin;
        this.enableText = enableText;
        this.disableText = disableText;
        this.sortTypes = sortTypes;
        this.sortNames = sortNames;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        HistorySortType currentSort = cache.get(PlayerCacheKey.HISTORY_SORT, HistorySortType.DATE_DESC);

        for (HistorySortType sortType : sortTypes) {
            String displayName = sortNames.getOrDefault(sortType, sortType.getDefaultDisplayName());
            String status = (sortType == currentSort ? enableText : disableText).replace("%sorting%", displayName);
            placeholders.register(sortType.name(), status);
        }

        return getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        if (sortTypes.isEmpty()) return;

        var cache = this.plugin.getAuctionManager().getCache(player);
        HistorySortType currentSort = cache.get(PlayerCacheKey.HISTORY_SORT, HistorySortType.DATE_DESC);

        int index = sortTypes.indexOf(currentSort);
        if (index == -1) index = 0;

        int direction = event.isRightClick() ? -1 : 1;
        int size = sortTypes.size();
        int nextIndex = (index + direction + size) % size;

        HistorySortType nextSort = sortTypes.get(nextIndex);
        cache.set(PlayerCacheKey.HISTORY_SORT, nextSort);

        // Refresh the inventory
        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }
}
