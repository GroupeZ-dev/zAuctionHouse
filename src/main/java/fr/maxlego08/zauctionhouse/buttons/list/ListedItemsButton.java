package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ListedItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public ListedItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();
        var items = manager.getItemsListedForSale(player);

        paginate(items, inventoryEngine, (slot, item) -> {
            var itemStack = item.buildItemStack(player);
            inventoryEngine.addItem(slot, itemStack).setClick(createClick(player, inventoryEngine, slot, item, itemStack));
        });
    }

    @Override
    public int getPaginationSize(Player player) {
        return this.plugin.getAuctionManager().getItemsListedForSale(player).size();
    }

    public Consumer<InventoryClickEvent> createClick(Player player, InventoryEngine inventoryEngine, int slot, Item item, ItemStack itemStack) {

        var manager = this.plugin.getAuctionManager();

        return event -> {

            if ((event.getClick() == ClickType.DROP || event.getClick() == ClickType.MIDDLE) && player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission())) {

                // ToDo

                return;
            }

            if (item.getSellerUniqueId().equals(player.getUniqueId())) {

                // Remove item
                if (this.plugin.getConfiguration().getActions().listed().openConfirmInventory()) {
                    var cache = manager.getCache(player);
                    cache.set(PlayerCacheKey.ITEM_SHOW, item);
                    cache.set(PlayerCacheKey.CURRENT_PAGE, this.plugin.getInventoriesLoader().getInventoryManager().getPage(player));

                    this.plugin.getInventoriesLoader().openInventory(player, Inventories.REMOVE_CONFIRM);
                } else {
                    manager.getRemoveService().removeListedItem(player, item);
                }
            } else {

                // Purchase items
                processPurchase(player, inventoryEngine, slot, item, itemStack);
            }
        };
    }

    private void processPurchase(Player player, InventoryEngine inventoryEngine, int slot, Item item, ItemStack itemStack) {

        var configuration = this.plugin.getConfiguration();
        var actionConfiguration = configuration.getActions().purchased();
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);

        if (cache.get(PlayerCacheKey.PURCHASE_ITEM)) return;

        cache.set(PlayerCacheKey.PURCHASE_ITEM, true);

        var economy = item.getAuctionEconomy();
        economy.has(player, item.getPrice()).whenComplete((hasMoney, throwable) -> {

            if (throwable != null) {
                this.plugin.getLogger().log(Level.WARNING, "Cannot verify the balance of " + player.getName(), throwable);
                cache.set(PlayerCacheKey.PURCHASE_ITEM, false);
                return;
            }

            if (!hasMoney) {

                if (actionConfiguration.sendNoMoneyMessage()) {
                    manager.message(player, Message.NOT_ENOUGH_MONEY);
                }

                if (actionConfiguration.noMoney().enable()) {
                    var spigotInventory = inventoryEngine.getSpigotInventory();
                    spigotInventory.setItem(slot, actionConfiguration.noMoney().menuItemStack().build(player));
                    this.plugin.getScheduler().runLater(() -> {

                        if (inventoryEngine.isClose()) return;
                        spigotInventory.setItem(slot, itemStack);

                    }, actionConfiguration.noMoney().duration());
                }

                cache.set(PlayerCacheKey.PURCHASE_ITEM, false);
                return;
            }

            cache.set(PlayerCacheKey.ITEM_SHOW, item);
            cache.set(PlayerCacheKey.CURRENT_PAGE, this.plugin.getInventoriesLoader().getInventoryManager().getPage(player));
            cache.set(PlayerCacheKey.PURCHASE_ITEM, false);

            this.plugin.getInventoriesLoader().openInventory(player, Inventories.PURCHASE_CONFIRM);
        });
    }

    public void updateInventory(Player player, InventoryEngine inventoryEngine, Item item, boolean added, AuctionManager manager) {

        int page = inventoryEngine.getPage();
        var items = manager.getItemsListedForSale(player);

        var slots = new ArrayList<>(getSlots());
        if (slots.isEmpty() || slots.size() == 1) return;

        int itemIndex = items.indexOf(item);
        if (itemIndex == -1) {
            return;
        }

        int itemsPerPage = slots.size();
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = startIndex + itemsPerPage;

        if (itemIndex < startIndex || itemIndex >= endIndex) {
            return;
        }

        var spigotInventory = inventoryEngine.getSpigotInventory();
        var inventoryItems = inventoryEngine.getItems();

        if (!added) {

            Item itemToAddAtEnd = null;
            if (endIndex < items.size()) {
                itemToAddAtEnd = items.get(endIndex);
            }

            items.remove(itemIndex);

            int removedGuiIndex = itemIndex - startIndex;
            int removedSlot = slots.get(removedGuiIndex);

            inventoryItems.remove(removedSlot);
            spigotInventory.setItem(removedSlot, null);

            for (int guiIndex = removedGuiIndex + 1; guiIndex < slots.size(); guiIndex++) {

                int fromSlot = slots.get(guiIndex);
                int toSlot = slots.get(guiIndex - 1);

                var button = inventoryItems.remove(fromSlot);
                if (button != null) {
                    inventoryItems.put(toSlot, button);
                    spigotInventory.setItem(toSlot, button.getDisplayItem());
                    spigotInventory.setItem(fromSlot, null);
                }
            }

            if (itemToAddAtEnd != null) {
                int lastSlot = slots.getLast();
                var lastItemStack = itemToAddAtEnd.buildItemStack(player);
                inventoryEngine.addItem(lastSlot, lastItemStack)
                        .setClick(createClick(player, inventoryEngine, lastSlot, itemToAddAtEnd, lastItemStack));
            }

            return;
        }

        int insertGuiIndex = itemIndex - startIndex;
        int newItemSlot = slots.get(insertGuiIndex);

        for (int guiIndex = slots.size() - 1; guiIndex > insertGuiIndex; guiIndex--) {

            int fromSlot = slots.get(guiIndex - 1);
            int toSlot = slots.get(guiIndex);

            var button = inventoryItems.get(fromSlot);
            if (button != null) {
                inventoryItems.put(toSlot, button);
                spigotInventory.setItem(toSlot, button.getDisplayItem());
            }
        }

        var itemStack = item.buildItemStack(player);
        inventoryEngine.addItem(newItemSlot, itemStack).setClick(createClick(player, inventoryEngine, newItemSlot, item, itemStack));
    }

}
