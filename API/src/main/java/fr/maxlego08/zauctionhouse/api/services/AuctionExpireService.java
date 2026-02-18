package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.util.List;

public interface AuctionExpireService {

    void processExpiredItem(Item item, StorageType storageType);

    /**
     * Processes multiple expired items in batch, executing a single SQL query per StorageType.
     *
     * @param items       list of expired items to process
     * @param storageType the original storage type of the items
     */
    void processExpiredItems(List<Item> items, StorageType storageType);

}
