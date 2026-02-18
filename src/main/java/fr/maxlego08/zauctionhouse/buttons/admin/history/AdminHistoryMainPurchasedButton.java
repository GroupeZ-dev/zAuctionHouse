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

public class AdminHistoryMainPurchasedButton extends TargetHelper {

    public AdminHistoryMainPurchasedButton(Plugin plugin) {
        super((AuctionPlugin) plugin);
    }

    @Override
    public @Nullable ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        getTargetName(player).ifPresent(target -> placeholders.register("target", target));

        getTargetUniqueId(player).ifPresentOrElse(uuid -> {
            var list = this.plugin.getAuctionManager().getPurchasedItems(uuid);

            placeholders.register("purchased-items", String.valueOf(list.size()));
            placeholders.register("s", list.size() > 1 ? "s" : "");
        }, () -> System.out.println("NON"));

        return super.getCustomItemStack(player, useCache, placeholders);
    }

    @Override
    public void onClick(@NotNull Player player, @NotNull InventoryClickEvent event, @NotNull InventoryEngine inventory, int slot, @NotNull Placeholders placeholders) {
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.ADMIN_PURCHASED_ITEMS);
    }

}
