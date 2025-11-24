package fr.maxlego08.zauctionhouse.api;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.items.Item;
import fr.maxlego08.zauctionhouse.api.items.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public interface AuctionManager {

    void openMainAuction(Player player);

    void openMainAuction(Player player, int page);

    AuctionPurchaseService getPurchaseService();

    AuctionSellService getSellService();

    AuctionRemoveService getRemoveService();

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

    PlayerCache getCache(Player player);

    void clearPlayersCache(PlayerCacheKey... keys);

    void clearPlayerCache(Player player, PlayerCacheKey... keys);

    void removeCache(Player player);

    void removeListedItem(Player player, Item item);

    void removeExpiredItem(Player player, Item item);
}
