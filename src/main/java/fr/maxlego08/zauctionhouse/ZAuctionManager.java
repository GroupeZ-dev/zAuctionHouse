package fr.maxlego08.zauctionhouse;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.items.Item;
import fr.maxlego08.zauctionhouse.api.items.ItemStatus;
import fr.maxlego08.zauctionhouse.api.items.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.services.PurchaseService;
import fr.maxlego08.zauctionhouse.services.RemoveService;
import fr.maxlego08.zauctionhouse.services.SellService;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import fr.maxlego08.zauctionhouse.utils.cache.ZPlayerCache;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ZAuctionManager extends ZUtils implements AuctionManager {

    private final AuctionPlugin plugin;
    private final AuctionPurchaseService auctionPurchaseService;
    private final AuctionSellService auctionSellService;
    private final AuctionRemoveService auctionRemoveService;

    private final Map<Player, PlayerCache> caches = new HashMap<>();
    private final Map<StorageType, List<Item>> storageItems = new HashMap<>();

    public ZAuctionManager(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.auctionPurchaseService = new PurchaseService(plugin);
        this.auctionSellService = new SellService(plugin, this);
        this.auctionRemoveService = new RemoveService(plugin);

        for (StorageType value : StorageType.values()) {
            this.storageItems.put(value, new ArrayList<>());
        }
    }

    @Override
    public void openMainAuction(Player player) {
        this.openMainAuction(player, 1);
    }

    @Override
    public void openMainAuction(Player player, int page) {
        var inventoriesLoader = this.plugin.getInventoriesLoader();
        inventoriesLoader.openInventory(player, Inventories.AUCTION, page);
    }

    public AuctionPlugin getPlugin() {
        return plugin;
    }

    @Override
    public AuctionPurchaseService getPurchaseService() {
        return auctionPurchaseService;
    }

    @Override
    public AuctionSellService getSellService() {
        return auctionSellService;
    }

    @Override
    public AuctionRemoveService getRemoveService() {
        return auctionRemoveService;
    }

    @Override
    public List<Item> getItems(StorageType storageType) {
        return this.storageItems.get(storageType);
    }

    @Override
    public List<Item> getItems(StorageType storageType, Predicate<Item> predicate) {
        List<Item> items = new ArrayList<>();
        for (Item item : getItems(storageType)) {
            if (predicate.test(item)) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<Item> getItems(StorageType storageType, Predicate<Item> predicate, Comparator<Item> comparator) {

        List<Item> result = getItems(storageType, predicate);

        if (comparator != null && result.size() > 1) {
            result.sort(comparator);
        }

        return result;
    }

    @Override
    public void addItem(StorageType storageType, Item item) {
        getItems(storageType).add(item);
    }

    @Override
    public void removeItem(StorageType storageType, Item item) {
        getItems(storageType).remove(item);
    }

    @Override
    public void removeItem(StorageType storageType, int itemId) {
        getItems(storageType).removeIf(item -> item.getId() == itemId);
    }

    @Override
    public List<Item> getItemsListedForSale(Player player) {
        return getCache(player).getOrCompute(PlayerCacheKey.ITEMS_LISTED, () -> getItems(StorageType.LISTED,
                item -> true, // ToDo
                Comparator.comparing(Item::getExpiredAt)
        ));
    }

    @Override
    public List<Item> getExpiredItems(Player player) {
        return getCache(player).getOrCompute(PlayerCacheKey.ITEMS_EXPIRED, () -> getItems(StorageType.EXPIRED,
                item -> item.getSellerUniqueId().equals(player.getUniqueId()),
                Comparator.comparing(Item::getExpiredAt)
        ));
    }

    @Override
    public List<Item> getPlayerOwnedItems(Player player) {
        return getCache(player).getOrCompute(PlayerCacheKey.ITEMS_OWNED, () -> getItems(StorageType.LISTED,
                item -> item.getSellerUniqueId().equals(player.getUniqueId()),
                Comparator.comparing(Item::getExpiredAt)
        ));
    }

    @Override
    public List<Item> getPurchasedItems(Player player) {
        return getCache(player).getOrCompute(PlayerCacheKey.ITEMS_PURCHASED, () -> getItems(StorageType.PURCHASED,
                item -> item.getBuyerUniqueId().equals(player.getUniqueId()),
                Comparator.comparing(Item::getExpiredAt)
        ));
    }

    @Override
    public PlayerCache getCache(Player player) {
        return this.caches.computeIfAbsent(player, p -> new ZPlayerCache());
    }

    @Override
    public void clearPlayersCache(PlayerCacheKey... keys) {
        this.caches.forEach((player, cache) -> cache.remove(keys));
    }

    @Override
    public void clearPlayerCache(Player player, PlayerCacheKey... keys) {
        getCache(player).remove(keys);
    }

    @Override
    public void removeCache(Player player) {
        this.caches.remove(player);
    }

    @Override
    public void removeListedItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        item.setStatus(ItemStatus.REMOVED);
        removeItem(StorageType.LISTED, item);

        clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global
        clearPlayerCache(player, PlayerCacheKey.ITEMS_OWNED); // Suppression du cache du joueur

        if (configuration.getActions().giveItemAfterRemovingListedItem() && item.canReceiveItem(player)) {

            storageManager.updateItem(item, StorageType.DELETED);
            giveItem(player, item);

        } else {

            var expiration = configuration.getExpireExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            item.setExpiredAt(new Date(expiredAt));

            addItem(StorageType.EXPIRED, item);
            storageManager.updateItem(item, StorageType.EXPIRED);
        }

        message(this.plugin, player, Message.ITEM_REMOVE_LISTED, "%amount%", item.getAmount(), "%item-translation-key%", item.getTranslationKey());

        if (configuration.getActions().openInventoryAfterRemovingListedItem()) {
            openMainAuction(player, getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1));
        } else {
            player.closeInventory();
        }

        var event = new AuctionRemoveListedItemEvent(item, player);
        event.callEvent();

        // ToDo Logs
    }

    @Override
    public void removeExpiredItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        removeItem(StorageType.EXPIRED, item);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);

        storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

        message(this.plugin, player, Message.ITEM_REMOVE_EXPIRED, "%amount%", item.getAmount(), "%item-translation-key%", item.getTranslationKey());

        if (configuration.getActions().openInventoryAfterRemovingExpiredItem()) {
            this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
        } else {
            player.closeInventory();
        }

        var event = new AuctionRemoveExpiredItemEvent(item, player);
        event.callEvent();

        // ToDo Logs
    }

    private void giveItem(Player player, Item item) {
        if (item instanceof AuctionItem auctionItem) {

            var itemStack = auctionItem.getItemStack();
            player.getInventory().addItem(itemStack);

        } else plugin.getLogger().severe("give item not implemented");
    }
}
