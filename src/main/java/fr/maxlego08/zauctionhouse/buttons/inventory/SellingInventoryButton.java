package fr.maxlego08.zauctionhouse.buttons.inventory;

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
import org.jspecify.annotations.NonNull;

public class SellingInventoryButton extends Button {

    private final AuctionPlugin plugin;
    private final boolean openWhenEmpty;

    public SellingInventoryButton(Plugin plugin, boolean openWhenEmpty) {
        this.plugin = (AuctionPlugin) plugin;
        this.openWhenEmpty = openWhenEmpty;
    }

    @Override
    public boolean hasPermission() {
        return true;
    }

    @Override
    public boolean checkPermission(Player player, InventoryEngine inventory, Placeholders placeholders) {
        // open-when-empty : le bouton reste cliquable meme sans item, l'inventaire s'ouvre vide
        if (this.openWhenEmpty) return super.checkPermission(player, inventory, placeholders);
        var list = this.plugin.getAuctionManager().getPlayerSellingItems(player);
        return super.checkPermission(player, inventory, placeholders) && !list.isEmpty();
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.SELLING_ITEMS);
    }

    @Override
    public ItemStack getCustomItemStack(@NonNull Player player, boolean useCache, @NotNull Placeholders placeholders) {

        var list = this.plugin.getAuctionManager().getPlayerSellingItems(player);

        placeholders.register("selling-items", String.valueOf(list.size()));
        placeholders.register("s", list.size() > 1 ? "s" : "");
        return getItemStack().build(player, false, placeholders);
    }

    @Override
    public boolean isPermanent() {
        return true;
    }
}
