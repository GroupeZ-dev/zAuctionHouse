package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionExpireEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

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
            var event = new AuctionExpireEvent(List.of(item), storageType);
            event.callEvent();
        });

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global

        var offlineSeller = item.getSeller();
        if (offlineSeller.isOnline()) {
            this.auctionManager.clearPlayerCache(offlineSeller.getPlayer(), PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED); // Suppression du cache du joueur
        }

        if (storageType == StorageType.LISTED) {

            item.setStatus(ItemStatus.REMOVED);

            Consumer<Long> applyExpiration = expiration -> this.plugin.getScheduler().runNextTick(w -> {
                long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                item.setExpiredAt(new Date(expiredAt));

                this.auctionManager.addItem(StorageType.EXPIRED, item);
                storageManager.updateItem(item, StorageType.EXPIRED);
            });

            if (offlineSeller.isOnline()) {
                var expiration = configuration.getExpireExpiration().getExpiration(offlineSeller.getPlayer());
                applyExpiration.accept(expiration);
            } else {
                configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), offlineSeller)
                        .whenComplete((expiration, throwable) -> {
                            long safeExpiration = expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration();
                            if (throwable != null) {
                                this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + offlineSeller.getName(), throwable);
                            }
                            applyExpiration.accept(safeExpiration);
                        });
            }

        } else {

            item.setStatus(ItemStatus.DELETED);
            storageManager.updateItem(item, StorageType.DELETED);
        }

        // ToDo Logs
    }

    @Override
    public void processExpiredItems(List<Item> items, StorageType storageType) {
        if (items.isEmpty()) return;

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(items, storageType);
            event.callEvent();
        });

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED);

        // Clear player caches for online sellers
        Set<OfflinePlayer> offlinePlayers = new HashSet<>();
        for (Item item : items) {
            var offlineSeller = item.getSeller();
            if (offlineSeller.isOnline() && !offlinePlayers.contains(offlineSeller)) {
                offlinePlayers.add(offlineSeller);
                this.auctionManager.clearPlayerCache(offlineSeller.getPlayer(), PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
            }
        }

        if (storageType == StorageType.LISTED) {
            // Items from LISTED storage go to EXPIRED
            List<Item> onlineSellerItems = new ArrayList<>();
            List<Item> offlineSellerItems = new ArrayList<>();

            // Separate items by seller online status
            for (Item item : items) {
                item.setStatus(ItemStatus.REMOVED);
                if (item.getSeller().isOnline()) {
                    onlineSellerItems.add(item);
                } else {
                    offlineSellerItems.add(item);
                }
            }

            // Process online sellers synchronously and batch update
            if (!onlineSellerItems.isEmpty()) {
                for (Item item : onlineSellerItems) {
                    var expiration = configuration.getExpireExpiration().getExpiration(item.getSeller().getPlayer());
                    long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                    item.setExpiredAt(new Date(expiredAt));
                    this.auctionManager.addItem(StorageType.EXPIRED, item);
                }

                // Batch update all online seller items
                Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                batchUpdate.put(StorageType.EXPIRED, onlineSellerItems);
                storageManager.updateItems(batchUpdate);
            }

            // Process offline sellers asynchronously then batch update
            if (!offlineSellerItems.isEmpty()) {
                AtomicInteger remaining = new AtomicInteger(offlineSellerItems.size());
                List<Item> processedItems = new ArrayList<>();

                for (Item item : offlineSellerItems) {
                    configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), item.getSeller())
                            .whenComplete((expiration, throwable) -> {
                                long safeExpiration = expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration();
                                if (throwable != null) {
                                    this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + item.getSeller().getName(), throwable);
                                }

                                long expiredAt = safeExpiration > 0 ? System.currentTimeMillis() + (safeExpiration * 1000) : 0;
                                item.setExpiredAt(new Date(expiredAt));

                                synchronized (processedItems) {
                                    processedItems.add(item);
                                    this.auctionManager.addItem(StorageType.EXPIRED, item);

                                    // When all items are processed, batch update
                                    if (remaining.decrementAndGet() == 0) {
                                        this.plugin.getScheduler().runNextTick(w -> {
                                            Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                                            batchUpdate.put(StorageType.EXPIRED, processedItems);
                                            storageManager.updateItems(batchUpdate);
                                        });
                                    }
                                }
                            });
                }
            }

        } else {
            // Items from EXPIRED storage go to DELETED
            for (Item item : items) {
                item.setStatus(ItemStatus.DELETED);
            }

            // Batch update all items to DELETED
            Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
            batchUpdate.put(StorageType.DELETED, items);
            storageManager.updateItems(batchUpdate);
        }
    }
}
