package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionExpireEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;

import java.util.Date;

public class ExpireService implements AuctionExpireService {

    private final AuctionPlugin plugin;
    private final AuctionManager auctionManager;

    public ExpireService(AuctionPlugin plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @Override
    public void processExpiredItem(Item item, StorageType storageType) {

        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(item, storageType);
            event.callEvent();
        });

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global

        var offlineSeller = item.getSeller();
        if (offlineSeller.isOnline()) {
            this.auctionManager.clearPlayerCache(offlineSeller.getPlayer(), PlayerCacheKey.ITEMS_OWNED, PlayerCacheKey.ITEMS_EXPIRED); // Suppression du cache du joueur
        }

        if (storageType == StorageType.LISTED) {

            item.setStatus(ItemStatus.REMOVED);
            
            var expiration = offlineSeller.isOnline() ?
                    configuration.getExpireExpiration().getExpiration(offlineSeller.getPlayer())
                    : configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), offlineSeller);

            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            item.setExpiredAt(new Date(expiredAt));

            this.auctionManager.addItem(StorageType.EXPIRED, item);
            storageManager.updateItem(item, StorageType.EXPIRED);

        } else {

            item.setStatus(ItemStatus.DELETED);
            storageManager.updateItem(item, StorageType.DELETED);
        }

        // ToDo Logs
    }
}
