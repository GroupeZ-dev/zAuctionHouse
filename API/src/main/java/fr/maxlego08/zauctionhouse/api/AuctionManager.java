package fr.maxlego08.zauctionhouse.api;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public interface AuctionManager {

    void openMainAuction(Player player);

    void openMainAuction(Player player, int page);

    void updateInventory(Player player);

    AuctionPurchaseService getPurchaseService();

    AuctionSellService getSellService();

    AuctionRemoveService getRemoveService();

    AuctionExpireService getExpireService();

    List<Item> getItems(StorageType storageType);

    List<Item> getItems(StorageType storageType, Predicate<Item> predicate);

    List<Item> getItems(StorageType storageType, Predicate<Item> predicate, Comparator<Item> comparator);

    void addItem(StorageType storageType, Item item);

    void removeItem(StorageType storageType, Item item);

    void removeItem(StorageType storageType, int itemId);

    List<Item> getItemsListedForSale(Player player);

    List<Item> getExpiredItems(Player player);

    List<Item> getPlayerOwnedItems(Player player);

    List<Item> getPurchasedItems(Player player);

    List<Item> getExpiredItems(java.util.UUID uniqueId);

    List<Item> getPlayerOwnedItems(java.util.UUID uniqueId);

    List<Item> getPurchasedItems(java.util.UUID uniqueId);

    PlayerCache getCache(Player player);

    void clearPlayersCache(PlayerCacheKey... keys);

    void clearPlayerCache(Player player, PlayerCacheKey... keys);

    void removeCache(Player player);

    CompletableFuture<Void> removeListedItem(Player player, Item item);

    CompletableFuture<Void> removeOwnedItem(Player player, Item item);

    CompletableFuture<Void> removeExpiredItem(Player player, Item item);

    CompletableFuture<Void> removePurchasedItem(Player player, Item item);

    void adminRemoveItem(Player admin, java.util.UUID targetUniqueId, Item item, StorageType storageType);

    CompletableFuture<Void> purchaseItem(Player player, Item item);

    void message(Player player, Message message, Object... args);

    void updateListedItems(Item item, boolean added, Player ignoredPlayer);
}
