package fr.maxlego08.zauctionhouse;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemovePurchasedItemEvent;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogContentType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import fr.maxlego08.zauctionhouse.services.ExpireService;
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
import java.util.UUID;
import java.util.function.Predicate;

public class ZAuctionManager extends ZUtils implements AuctionManager {

    private final AuctionPlugin plugin;
    private final AuctionPurchaseService auctionPurchaseService;
    private final AuctionSellService auctionSellService;
    private final AuctionRemoveService auctionRemoveService;
    private final AuctionExpireService auctionExpireService;

    private final Map<Player, PlayerCache> caches = new HashMap<>();
    private final Map<StorageType, List<Item>> storageItems = new HashMap<>();

    public ZAuctionManager(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.auctionPurchaseService = new PurchaseService(plugin);
        this.auctionSellService = new SellService(plugin, this);
        this.auctionRemoveService = new RemoveService(plugin);
        this.auctionExpireService = new ExpireService(plugin, this);

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
        return this.auctionRemoveService;
    }

    @Override
    public AuctionExpireService getExpireService() {
        return this.auctionExpireService;
    }

    @Override
    public List<Item> getItems(StorageType storageType) {
        return this.storageItems.get(storageType);
    }

    @Override
    public List<Item> getItems(StorageType storageType, Predicate<Item> predicate) {
        List<Item> items = new ArrayList<>();

        var iterator = getItems(storageType).iterator();
        while (iterator.hasNext()) {

            var item = iterator.next();
            if (item.isExpired()) {
                this.auctionExpireService.processExpiredItem(item, storageType);
                iterator.remove();
                continue;
            }

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
        var cache = getCache(player);
        var sort = cache.get(PlayerCacheKey.ITEM_SORT, this.plugin.getConfiguration().getSort().defaultSort());
        return cache.getOrCompute(PlayerCacheKey.ITEMS_LISTED, () -> getItems(StorageType.LISTED, item -> true, // ToDo
                sort.getComparator()));
    }

    @Override
    public List<Item> getExpiredItems(Player player) {
        return getCache(player).getOrCompute(PlayerCacheKey.ITEMS_EXPIRED, () -> getItems(StorageType.EXPIRED, item -> item.getSellerUniqueId().equals(player.getUniqueId()), Comparator.comparing(Item::getExpiredAt)));
    }

    @Override
    public List<Item> getExpiredItems(UUID uniqueId) {
        return getItems(StorageType.EXPIRED, item -> item.getSellerUniqueId().equals(uniqueId), Comparator.comparing(Item::getExpiredAt));
    }

    @Override
    public List<Item> getPlayerOwnedItems(Player player) {
        return getCache(player).getOrCompute(PlayerCacheKey.ITEMS_OWNED, () -> getItems(StorageType.LISTED, item -> item.getSellerUniqueId().equals(player.getUniqueId()), Comparator.comparing(Item::getExpiredAt)));
    }

    @Override
    public List<Item> getPlayerOwnedItems(UUID uniqueId) {
        return getItems(StorageType.LISTED, item -> item.getSellerUniqueId().equals(uniqueId), Comparator.comparing(Item::getExpiredAt));
    }

    @Override
    public List<Item> getPurchasedItems(Player player) {
        return getCache(player).getOrCompute(PlayerCacheKey.ITEMS_PURCHASED, () -> getItems(StorageType.PURCHASED, item -> item.getBuyerUniqueId().equals(player.getUniqueId()), Comparator.comparing(Item::getExpiredAt)));
    }

    @Override
    public List<Item> getPurchasedItems(UUID uniqueId) {
        return getItems(StorageType.PURCHASED, item -> item.getBuyerUniqueId() != null && item.getBuyerUniqueId().equals(uniqueId), Comparator.comparing(Item::getExpiredAt));
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

        this.updateListedItems(item, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_OWNED, PlayerCacheKey.ITEMS_EXPIRED); // Suppression du cache du joueur

        if (configuration.getActions().listed().giveItem() && item.canReceiveItem(player)) {

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

        if (configuration.getActions().listed().openInventory()) {
            openMainAuction(player, getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1));
        } else {
            player.closeInventory();
        }

        var event = new AuctionRemoveListedItemEvent(item, player);
        event.callEvent();

        logItemAction(LogType.REMOVE_LISTED, item, player, null, "removed_from_listed");
    }

    @Override
    public void removeOwnedItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        item.setStatus(ItemStatus.DELETED);
        removeItem(StorageType.LISTED, item);

        this.updateListedItems(item, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_OWNED, PlayerCacheKey.ITEMS_EXPIRED); // Suppression du cache du joueur

        storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

        message(this.plugin, player, Message.ITEM_REMOVE_LISTED, "%amount%", item.getAmount(), "%item-translation-key%", item.getTranslationKey());

        if (configuration.getActions().listed().openInventory()) {
            this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
        } else {
            player.closeInventory();
        }

        var event = new AuctionRemoveListedItemEvent(item, player);
        event.callEvent();

        logItemAction(LogType.REMOVE_OWNED, item, player, null, "removed_owned_item");
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

        if (configuration.getActions().expired().openInventory()) {
            this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
        } else {
            player.closeInventory();
        }

        var event = new AuctionRemoveExpiredItemEvent(item, player);
        event.callEvent();

        logItemAction(LogType.REMOVE_EXPIRED, item, player, null, "removed_expired_item");
    }

    @Override
    public void removePurchasedItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        removeItem(StorageType.PURCHASED, item);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);

        storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

        message(this.plugin, player, Message.ITEM_REMOVE_PURCHASED, "%amount%", item.getAmount(), "%item-translation-key%", item.getTranslationKey());

        if (configuration.getActions().purchased().openInventory()) {
            this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
        } else {
            player.closeInventory();
        }

        var event = new AuctionRemovePurchasedItemEvent(item, player);
        event.callEvent();

        logItemAction(LogType.REMOVE_PURCHASED, item, player, item.getSellerUniqueId(), "removed_purchased_item");

    }

    @Override
    public void adminRemoveItem(Player admin, UUID targetUniqueId, Item item, StorageType storageType) {

        removeItem(storageType, item);

        this.plugin.getStorageManager().updateItem(item, StorageType.DELETED);
        clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_OWNED);

        giveItem(admin, item);

        var targetName = item.getSellerUniqueId().equals(targetUniqueId) ? item.getSellerName() : item.getBuyerName();
        message(this.plugin, admin, Message.ADMIN_ITEM_REMOVED, "%item%", item.getTranslationKey(), "%target%", targetName == null ? "unknown" : targetName);
    }

    @Override
    public void purchaseItem(Player player, Item item) {
        if (item instanceof AuctionItem auctionItem) {
            purchaseAuctionItem(player, auctionItem);
        }
    }

    private void purchaseAuctionItem(Player player, AuctionItem auctionItem) {

        var auctionEconomy = auctionItem.getAuctionEconomy();
        var price = auctionItem.getPrice();
        var itemStack = auctionItem.getItemStack();
        var seller = auctionItem.getSeller();
        var storageManager = this.plugin.getStorageManager();
        var configuration = this.plugin.getConfiguration();

        // On retire l'argent
        auctionEconomy.withdraw(player, price, args(auctionEconomy.getWithdrawReason(), "%amount%", itemStack.getAmount(), "%seller%", auctionItem.getSellerName(), "%item%", itemStack.getType().name()));

        // On donne l'argent
        auctionEconomy.deposit(seller, price, args(auctionEconomy.getDepositReason(), "%amount%", itemStack.getAmount(), "%buyer%", player.getName(), "%item%", itemStack.getType().name()));

        if (seller.isOnline()) {
            message(this.plugin, seller.getPlayer(), Message.ITEM_BOUGHT_SELLER, "%amount%", itemStack.getAmount(), "%price%", auctionItem.getFormattedPrice(), "%item-translation-key%", auctionItem.getTranslationKey(), "%seller%", auctionItem.getSellerName(), "%buyer%", player.getName());
        }

        message(player, Message.ITEM_BOUGHT_BUYER, "%amount%", itemStack.getAmount(), "%price%", auctionItem.getFormattedPrice(), "%item-translation-key%", auctionItem.getTranslationKey(), "%seller%", auctionItem.getSellerName(), "%buyer%", player.getName());

        auctionItem.setBuyer(player);
        auctionItem.setStatus(ItemStatus.PURCHASED);

        this.updateListedItems(auctionItem, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);
        if (seller.isOnline()) {
            clearPlayerCache(seller.getPlayer(), PlayerCacheKey.ITEMS_OWNED);
        }

        removeItem(StorageType.LISTED, auctionItem);

        var purchasedConfiguration = configuration.getActions().purchased();

        if (purchasedConfiguration.giveItem()) {

            storageManager.updateItem(auctionItem, StorageType.DELETED);
            giveItem(player, auctionItem);

        } else {

            var expiration = configuration.getPurchaseExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            auctionItem.setExpiredAt(new Date(expiredAt));

            addItem(StorageType.PURCHASED, auctionItem);
            storageManager.updateItem(auctionItem, StorageType.PURCHASED);
        }

        if (purchasedConfiguration.openInventory()) {
            openMainAuction(player, getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1));
        } else {
            player.closeInventory();
        }

        logItemAction(LogType.PURCHASE, auctionItem, player, auctionItem.getSellerUniqueId(), "purchase_item");
    }

    @Override
    public void message(Player player, Message message, Object... args) {
        this.message(this.plugin, player, message, args);
    }

    public void giveItem(Player player, Item item) {
        if (item instanceof AuctionItem auctionItem) {

            var itemStack = auctionItem.getItemStack();
            player.getInventory().addItem(itemStack).forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));

        } else plugin.getLogger().severe("give item not implemented");
    }

    @Override
    public void updateListedItems(Item item, boolean added, Player ignoredPlayer) {

        if (!this.plugin.getConfiguration().getActions().updateInventoryOnAction()) {
            if (!added) {
                for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
                    removeFromCache(onlinePlayer, item);
                }
            }
            return;
        }

        if (!added) removeFromCache(ignoredPlayer, item);

        this.plugin.getScheduler().runAsync(w -> {
            for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {

                if (onlinePlayer == ignoredPlayer) continue;

                var openInventory = onlinePlayer.getOpenInventory().getTopInventory().getHolder();
                if (openInventory instanceof InventoryEngine inventoryEngine) {
                    var buttons = inventoryEngine.getMenuInventory().getButtons(ListedItemsButton.class);
                    if (buttons.isEmpty()) continue;

                    var listedItemsButton = buttons.getFirst();
                    listedItemsButton.updateInventory(onlinePlayer, inventoryEngine, item, added, this);
                }

                if (!added) removeFromCache(onlinePlayer, item);
            }
        });
    }

    private void removeFromCache(Player player, Item item) {
        if (this.caches.containsKey(player)) {
            List<Item> items = this.caches.get(player).get(PlayerCacheKey.ITEMS_LISTED);
            items.remove(item);
        }
    }

    private void logItemAction(LogType logType, Item item, Player player, UUID targetUniqueId, String additionalData) {
        var storageManager = this.plugin.getStorageManager();
        var economy = item.getAuctionEconomy();
        var itemStack = item instanceof AuctionItem auctionItem ? auctionItem.getItemStack() : null;
        var economyName = economy == null ? null : economy.getName();

        storageManager.log(logType, LogContentType.ITEM, item.getId(), player, targetUniqueId, itemStack, item.getPrice(), economyName, additionalData);
    }
}
