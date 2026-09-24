package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class SearchButton extends Button {

    private final AuctionPlugin plugin;
    private final String noneValue;
    private final String activeValue;
    private final String inactiveValue;
    private final boolean dialogEnabled;

    public SearchButton(AuctionPlugin plugin, String noneValue, String activeValue, String inactiveValue) {
        this(plugin, noneValue, activeValue, inactiveValue, false);
    }

    public SearchButton(AuctionPlugin plugin, String noneValue, String activeValue, String inactiveValue, boolean dialogEnabled) {
        this.plugin = plugin;
        this.noneValue = noneValue;
        this.activeValue = activeValue;
        this.inactiveValue = inactiveValue;
        this.dialogEnabled = dialogEnabled;
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

        placeholders.register("search_query", hasActiveQuery ? query : this.noneValue);
        placeholders.register("search_active", hasActiveQuery ? this.activeValue : this.inactiveValue);

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        // An open zMenu inventory can still reference this button after its plugin is unloaded.
        if (!this.plugin.isEnabled() || this.plugin instanceof ZAuctionPlugin plugin && plugin.isShuttingDown()) return;

        super.onClick(player, event, inventory, slot, placeholders);

        if (this.dialogEnabled && this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
            this.plugin.getScheduler().runAtEntity(player, task -> {
                if (this.plugin.isEnabled() && player.isOnline() && !zAuctionPlugin.isShuttingDown()) {
                    zAuctionPlugin.getChatSearchListener().startDialogSearch(player);
                }
            });
            return;
        }

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
            zAuctionPlugin.getChatSearchListener().startSearch(player);
        }
    }
}
