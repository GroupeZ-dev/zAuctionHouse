package fr.maxlego08.zauctionhouse.buttons.admin.history;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdminHistoryMainOwnedButton extends TargetHelper {

    public AdminHistoryMainOwnedButton(Plugin plugin) {
        super((AuctionPlugin) plugin);
    }

    @Override
    public @Nullable ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        getTargetName(player).ifPresent(target -> placeholders.register("target", target));

        getTargetUniqueId(player).ifPresent(uuid -> {
            var list = this.plugin.getAuctionManager().getPlayerOwnedItems(uuid);

            placeholders.register("owned-items", String.valueOf(list.size()));
            placeholders.register("s", list.size() > 1 ? "s" : "");
        });

        return super.getCustomItemStack(player, useCache, placeholders);
    }

    @Override
    public void onClick(@NotNull Player player, @NotNull InventoryClickEvent event, @NotNull InventoryEngine inventory, int slot, @NotNull Placeholders placeholders) {
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.ADMIN_OWNED_ITEMS);
    }
}
