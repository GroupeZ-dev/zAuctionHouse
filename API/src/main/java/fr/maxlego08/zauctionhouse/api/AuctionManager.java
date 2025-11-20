package fr.maxlego08.zauctionhouse.api;

import fr.maxlego08.zauctionhouse.api.items.Item;
import fr.maxlego08.zauctionhouse.api.items.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import org.bukkit.entity.Player;

import java.util.List;

public interface AuctionManager {

    void openMainAuction(Player player);

    void openMainAuction(Player player, int page);

    AuctionPurchaseService getPurchaseService();

    AuctionSellService getSellService();

    AuctionRemoveService getRemoveService();

    List<Item> getItems(StorageType storageType);

    void addItem(StorageType storageType, Item item);

    void removeItem(StorageType storageType, Item item);

    void removeItem(StorageType storageType, int itemId);

    List<Item> getSortItems(Player player);

}
