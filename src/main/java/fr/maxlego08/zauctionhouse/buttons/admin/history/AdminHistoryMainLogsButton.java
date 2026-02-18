package fr.maxlego08.zauctionhouse.buttons.admin.history;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.LogRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdminHistoryMainLogsButton extends TargetHelper {

    public AdminHistoryMainLogsButton(Plugin plugin) {
        super((AuctionPlugin) plugin);
    }

    @Override
    public @Nullable ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        getTargetName(player).ifPresent(target -> placeholders.register("target", target));

        getTargetUniqueId(player).ifPresent(uuid -> {
            var list = this.plugin.getStorageManager().with(LogRepository.class).selectByPlayerOrTarget(uuid);

            placeholders.register("logs", String.valueOf(list.size()));
            placeholders.register("s", list.size() > 1 ? "s" : "");
        });

        return super.getCustomItemStack(player, useCache, placeholders);
    }

    @Override
    public void onClick(@NotNull Player player, @NotNull InventoryClickEvent event, @NotNull InventoryEngine inventory, int slot, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        cache.remove(PlayerCacheKey.ADMIN_LOGS_DATA, PlayerCacheKey.ADMIN_LOGS_LOADING,
                PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER, PlayerCacheKey.ADMIN_LOGS_DATE_FILTER);
        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.ADMIN_LOGS);
    }
}
