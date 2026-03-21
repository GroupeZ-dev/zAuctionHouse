package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ListedItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;
    private final int emptySlot;

    public ListedItemsButton(Plugin plugin, int emptySlot) {
        this.plugin = (AuctionPlugin) plugin;
        this.emptySlot = emptySlot;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {

        var manager = this.plugin.getAuctionManager();

        // 1. Get IDs from cache (O(1) access)
        IntList itemIds = manager.getItemIdsListedForSale(player);

        if (itemIds.isEmpty()) {
            if (this.emptySlot == -1) return;
            inventoryEngine.addItem(this.emptySlot, getCustomItemStack(player, false, new Placeholders()));
            return;
        }

        // 2. Resolve ONLY items for the current page
        int page = inventoryEngine.getPage() - 1; // zMenu pages start at 1
        var slots = new ArrayList<>(getSlots());
        List<Item> pageItems = manager.resolveItemsForPage(StorageType.LISTED, itemIds, page, slots.size());

        // 3. Display items directly
        for (int i = 0; i < pageItems.size(); i++) {
            Item item = pageItems.get(i);
            int slot = slots.get(i);
            var itemStack = item.buildItemStack(player);
            var button = inventoryEngine.addItem(slot, itemStack);
            if (button != null) {
                button.setClick(createClick(player, inventoryEngine, slot, item, itemStack));
            }
        }
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        return this.plugin.getAuctionManager().getItemIdsListedForSale(player).size();
    }

    /**
     * Create a consumer for the click event of an item in the listed items inventory.
     * This consumer will handle the click event and perform the necessary actions.
     * If the item is not available, it will clear the cache and update the inventory.
     * If the player has the permission to remove items from the inventory, it will open the remove confirm inventory.
     * If the player is the seller of the item, it will open the remove confirm inventory.
     * Otherwise, it will process the purchase of the item.
     *
     * @param player          the player who clicked the item
     * @param inventoryEngine the inventory engine to use
     * @param slot            the slot of the item in the inventory
     * @param item            the item that was clicked
     * @param itemStack       the item stack of the item
     * @return the consumer for the click event
     */
    public Consumer<InventoryClickEvent> createClick(Player player, InventoryEngine inventoryEngine, int slot, Item item, ItemStack itemStack) {

        var manager = this.plugin.getAuctionManager();

        return event -> {

            if (item.getStatus() != ItemStatus.AVAILABLE) {
                manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
                manager.updateInventory(player);
                return;
            }

            if ((event.getClick() == ClickType.DROP || event.getClick() == ClickType.MIDDLE) && player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission())) {

                // ToDo

                return;
            }

            if (item.getSellerUniqueId().equals(player.getUniqueId())) {

                // Remove item
                if (this.plugin.getConfiguration().getActions().listed().openConfirmInventory()) {

                    var isMultipleAuctionItem = item instanceof AuctionItem auctionItem && auctionItem.getItemStacks().size() > 1;

                    var cache = manager.getCache(player);
                    cache.set(PlayerCacheKey.ITEM_SHOW, item);
                    cache.set(PlayerCacheKey.CURRENT_PAGE, this.plugin.getInventoriesLoader().getInventoryManager().getPage(player));

                    // Notify cluster first, then update status to ensure atomicity
                    this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_REMOVE_CONFIRM)
                            .thenRun(() -> {
                                item.setStatus(ItemStatus.IS_REMOVE_CONFIRM);
                                manager.updateListedItems(item, false, null);
                                this.plugin.getInventoriesLoader().openInventory(player, isMultipleAuctionItem ? Inventories.REMOVE_INVENTORY_CONFIRM : Inventories.REMOVE_CONFIRM);
                            });
                } else {

                    manager.getRemoveService().removeListedItem(player, item);
                }
            } else {

                // Purchase items
                var isMultipleAuctionItem = item instanceof AuctionItem auctionItem && auctionItem.getItemStacks().size() > 1;
                var inventories = isMultipleAuctionItem ? Inventories.PURCHASE_INVENTORY_CONFIRM : Inventories.PURCHASE_CONFIRM;

                processPurchase(player, inventoryEngine, slot, item, itemStack, inventories);
            }
        };
    }

    /**
     * Process the purchase of an item.
     * This method will first check if the player has enough money to purchase the item.
     * If the player does not have enough money, it will send a message to the player and
     * temporarily replace the item in the inventory with a "no money" item.
     * If the player has enough money, it will update the status of the item to "is purchase confirm"
     * and open the purchase confirm inventory.
     * The method will also use the cache to prevent the player from purchasing the item multiple times.
     *
     * @param player          the player who is purchasing the item
     * @param inventoryEngine the inventory engine to use
     * @param slot            the slot of the item in the inventory
     * @param item            the item to purchase
     * @param itemStack       the item stack of the item
     */
    private void processPurchase(Player player, InventoryEngine inventoryEngine, int slot, Item item, ItemStack itemStack, Inventories inventories) {

        var configuration = this.plugin.getConfiguration();
        var actionConfiguration = configuration.getActions().purchased();
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);

        if (cache.get(PlayerCacheKey.PURCHASE_ITEM)) return;

        cache.set(PlayerCacheKey.PURCHASE_ITEM, true);

        var economy = item.getAuctionEconomy();
        if (economy == null) {
            this.plugin.getLogger().warning("Item " + item.getId() + " has no economy set, cannot process purchase");
            cache.set(PlayerCacheKey.PURCHASE_ITEM, false);
            return;
        }

        economy.has(player.getUniqueId(), item.getPrice()).whenComplete((hasMoney, throwable) -> {

            if (throwable != null) {
                this.plugin.getLogger().log(Level.WARNING, "Cannot verify the balance of " + player.getName(), throwable);
                cache.set(PlayerCacheKey.PURCHASE_ITEM, false);
                return;
            }

            if (!hasMoney) {

                if (actionConfiguration.sendNoMoneyMessage()) {
                    manager.message(player, Message.NOT_ENOUGH_MONEY);
                }

                actionConfiguration.noMoneySound().play(player);

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

            // Notify cluster first, then update status to ensure atomicity
            this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_PURCHASE_CONFIRM)
                    .thenRun(() -> {
                        item.setStatus(ItemStatus.IS_PURCHASE_CONFIRM);
                        manager.updateListedItems(item, false, player);
                        this.plugin.getInventoriesLoader().openInventory(player, inventories);
                    });
        });
    }

    /**
     * Update the inventory of a player with the given item.
     * If the item is being added, it will be inserted at the correct position in the inventory.
     * If the item is being removed, it will be removed from the inventory.
     *
     * @param player          the player whose inventory to update
     * @param inventoryEngine the inventory engine to use
     * @param item            the item to add or remove
     * @param isAdded         whether the item is being added or removed
     * @param manager         the auction manager to use
     */
    public void updateInventory(Player player, InventoryEngine inventoryEngine, Item item, boolean isAdded, AuctionManager manager) {

        // Get the current page of the inventory
        int page = inventoryEngine.getPage();

        // Get the IDs from cache (O(1) access)
        IntList itemIds = manager.getItemIdsListedForSale(player);

        if (itemIds.size() <= 1) {
            this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
            return;
        }

        // Get the slots in the inventory
        var slots = new ArrayList<>(getSlots());
        if (slots.isEmpty() || slots.size() == 1) return;

        // Find the index of the item ID in the list
        int itemIndex = findIndexOf(itemIds, item.getId());
        if (itemIndex == -1) return;

        // Calculate the start and end index of the items on the current page
        int itemsPerPage = slots.size();
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = startIndex + itemsPerPage;

        // If the item is not on the current page, do nothing
        if (itemIndex < startIndex || itemIndex >= endIndex) return;

        // If the item is being removed, shift all items after it down by one slot
        if (!isAdded) {
            // Resolve item at endIndex if needed (for filling the gap)
            Item itemToAddAtEnd = null;
            if (endIndex < itemIds.size()) {
                int endItemId = itemIds.getInt(endIndex);
                List<Item> resolved = manager.resolveItems(StorageType.LISTED, createSingleItemList(endItemId));
                if (!resolved.isEmpty()) {
                    itemToAddAtEnd = resolved.getFirst();
                }
            }
            processRemove(itemToAddAtEnd, itemIndex, startIndex, slots, inventoryEngine, player);
        }
        // If the item is being added, shift all items after it up by one slot
        else {
            processAdd(item, itemIndex, startIndex, slots, inventoryEngine, player);
        }
    }

    /**
     * Find the index of an item ID in an IntList.
     *
     * @param ids    the list of IDs to search
     * @param itemId the item ID to find
     * @return the index of the item ID, or -1 if not found
     */
    private int findIndexOf(IntList ids, int itemId) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.getInt(i) == itemId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Create an IntList with a single item ID.
     *
     * @param itemId the item ID
     * @return an IntList containing the single ID
     */
    private IntList createSingleItemList(int itemId) {
        IntList list = new fr.maxlego08.zauctionhouse.api.utils.IntArrayList(1);
        list.add(itemId);
        return list;
    }

    /**
     * Add an item to the player's inventory at the specified index.
     * This method will shift all items after the added item up by one slot.
     *
     * @param item            the item to add
     * @param itemIndex       the index of the item in the list of items
     * @param startIndex      the index of the first item on the page
     * @param slots           the list of slots in the inventory
     * @param inventoryEngine the inventory engine to use
     * @param player          the player whose inventory to update
     */
    private void processAdd(Item item, int itemIndex, int startIndex, List<Integer> slots, InventoryEngine inventoryEngine, Player player) {
        // Get the inventory and the items stored in it
        var spigotInventory = inventoryEngine.getSpigotInventory();
        var inventoryItems = inventoryEngine.getItems();

        // Calculate the GUI index of the item to be inserted
        int insertGuiIndex = itemIndex - startIndex;
        int newItemSlot = slots.get(insertGuiIndex);

        // Shift all items after the inserted item up by one slot
        for (int guiIndex = slots.size() - 1; guiIndex > insertGuiIndex; guiIndex--) {

            int fromSlot = slots.get(guiIndex - 1);
            int toSlot = slots.get(guiIndex);

            // Get the item at the "from" slot and move it to the "to" slot
            var button = inventoryItems.get(fromSlot);
            if (button != null) {
                inventoryItems.put(toSlot, button);
                // Update the GUI with the moved item
                spigotInventory.setItem(toSlot, button.getDisplayItem());
            }
        }

        // Create a new item stack for the inserted item
        var itemStack = item.buildItemStack(player);
        // Add the inserted item to the inventory
        var itemButton = inventoryEngine.addItem(newItemSlot, itemStack);
        if (itemButton != null) itemButton.setClick(createClick(player, inventoryEngine, newItemSlot, item, itemStack));
    }

    /**
     * Remove an item from a player's inventory.
     * This method will also shift all items after the removed item down by one slot.
     * If there is an item to add at the end (from the next page), it will be added.
     *
     * @param itemToAddAtEnd  the item to add at the end of the page (or null if none)
     * @param itemIndex       the index of the item to remove
     * @param startIndex      the index of the first item on the page
     * @param slots           the list of slots in the inventory
     * @param inventoryEngine the inventory engine to use
     * @param player          the player whose inventory to update
     */
    private void processRemove(Item itemToAddAtEnd, int itemIndex, int startIndex, List<Integer> slots, InventoryEngine inventoryEngine, Player player) {

        // Get the current inventory and the items stored in it
        var spigotInventory = inventoryEngine.getSpigotInventory();
        var inventoryItems = inventoryEngine.getItems();

        // Get the GUI index of the removed item
        int removedGuiIndex = itemIndex - startIndex;
        int removedSlot = slots.get(removedGuiIndex);

        // Remove the item from the inventory
        inventoryItems.remove(removedSlot);
        spigotInventory.setItem(removedSlot, null);

        // Shift all items after the removed item down by one slot
        for (int guiIndex = removedGuiIndex + 1; guiIndex < slots.size(); guiIndex++) {

            int fromSlot = slots.get(guiIndex);
            int toSlot = slots.get(guiIndex - 1);

            // Get the item at the current GUI index
            var button = inventoryItems.remove(fromSlot);
            if (button != null) {
                // Move the item to the previous slot
                inventoryItems.put(toSlot, button);
                spigotInventory.setItem(toSlot, button.getDisplayItem());
                // Clear the previous slot
                spigotInventory.setItem(fromSlot, null);
            }
        }

        // If there was an item at the end of the page that needed to be moved, add it now
        if (itemToAddAtEnd != null) {
            int lastSlot = slots.getLast();
            var lastItemStack = itemToAddAtEnd.buildItemStack(player);
            var itemButton = inventoryEngine.addItem(lastSlot, lastItemStack);
            if (itemButton != null) {
                itemButton.setClick(createClick(player, inventoryEngine, lastSlot, itemToAddAtEnd, lastItemStack));
            }
        }
    }
}
