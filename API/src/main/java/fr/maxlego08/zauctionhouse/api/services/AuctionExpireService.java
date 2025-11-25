package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

public interface AuctionExpireService {

    void processExpiredItem(Item item, StorageType storageType);

}
