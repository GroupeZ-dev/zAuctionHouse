package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public class AdminPurchasedItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public AdminPurchasedItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        var target = this.getTarget(player);
        if (target.isEmpty()) {
            this.plugin.getAuctionManager().message(player, Message.ADMIN_TARGET_REQUIRED);
            return;
        }

        var manager = this.plugin.getAuctionManager();
        var items = manager.getPurchasedItems(target.get());

        if (items.isEmpty()) {
            inventoryEngine.buildButton(this.getElseButton(), new Placeholders());
            return;
        }

        paginate(items, inventoryEngine, (slot, item) -> inventoryEngine.addItem(slot, item.buildItemStack(player)).setClick(event -> {
            // adminRemoveItem handles inventory update internally

            if (!player.hasPermission(Permission.ADMIN_PURCHASED_ITEMS_REMOVE.asPermission())) return;

            manager.adminRemoveItem(player, target.get(), item, StorageType.PURCHASED);
        }));
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return this.getTarget(player).map(uuid -> this.plugin.getAuctionManager().getPurchasedItems(uuid).size()).orElse(0);
    }

    private Optional<UUID> getTarget(Player player) {
        return Optional.ofNullable(this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ADMIN_TARGET_UUID));
    }
}
