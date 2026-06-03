package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.filter.SearchQueryFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class SearchButton extends Button {

    private final AuctionPlugin plugin;

    public SearchButton(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        String query = cache.get(PlayerCacheKey.SEARCH_QUERY);
        boolean hasActiveQuery = query != null && !query.isBlank();

        placeholders.register("search_query", hasActiveQuery ? SearchQueryFormatter.format(query, this.plugin.getConfiguration().getSearchFilter()) : "None");
        placeholders.register("search_active", hasActiveQuery ? "true" : "false");

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);
        String query = cache.get(PlayerCacheKey.SEARCH_QUERY);

        if (event.isRightClick()) {
            if (query != null) {
                this.plugin.getAuctionManager().clearSearch(player);
                this.plugin.getAuctionManager().openMainAuction(player);
            }
            return;
        }

        if (this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
            zAuctionPlugin.getSearchDialogService().openSearchDialog(player);
        }
    }
}
