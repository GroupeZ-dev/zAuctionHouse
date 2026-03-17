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
import java.util.concurrent.CopyOnWriteArrayList;
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
            var sellerPlayer = offlineSeller.getPlayer();
            if (sellerPlayer != null) {
                this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
            }
        }

        if (storageType == StorageType.LISTED) {

            item.setStatus(ItemStatus.REMOVED);
            this.auctionManager.removeItem(StorageType.LISTED, item);

            Consumer<Long> applyExpiration = expiration -> this.plugin.getScheduler().runNextTick(w -> {
                long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                item.setExpiredAt(new Date(expiredAt));

                this.auctionManager.addItem(StorageType.EXPIRED, item);
                storageManager.updateItem(item, StorageType.EXPIRED);
            });

            var onlinePlayer = offlineSeller.isOnline() ? offlineSeller.getPlayer() : null;
            if (onlinePlayer != null) {
                var expiration = configuration.getExpireExpiration().getExpiration(onlinePlayer);
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

        // Log expiration for debugging purposes
        if (this.plugin.getConfiguration().isEnableDebug()) {
            this.plugin.getLogger().info("Item " + item.getId() + " expired from " + storageType + " (seller: " + item.getSellerName() + ")");
        }
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
                var sellerPlayer = offlineSeller.getPlayer();
                if (sellerPlayer != null) {
                    this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
                }
            }
        }

        if (storageType == StorageType.LISTED) {
            // Items from LISTED storage go to EXPIRED
            List<Item> onlineSellerItems = new ArrayList<>();
            List<Item> offlineSellerItems = new ArrayList<>();

            // Remove all items from LISTED storage and separate by seller online status
            for (Item item : items) {
                item.setStatus(ItemStatus.REMOVED);
                this.auctionManager.removeItem(StorageType.LISTED, item);
                if (item.getSeller().isOnline()) {
                    onlineSellerItems.add(item);
                } else {
                    offlineSellerItems.add(item);
                }
            }

            // Process online sellers synchronously and batch update
            if (!onlineSellerItems.isEmpty()) {
                for (Item item : onlineSellerItems) {
                    var sellerPlayer = item.getSeller().getPlayer();
                    // Player may have disconnected between isOnline() check and now
                    long expiration = sellerPlayer != null
                        ? configuration.getExpireExpiration().getExpiration(sellerPlayer)
                        : configuration.getExpireExpiration().defaultExpiration();
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
                List<Item> processedItems = new CopyOnWriteArrayList<>();

                for (Item item : offlineSellerItems) {
                    configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), item.getSeller())
                            .whenComplete((expiration, throwable) -> {
                                long safeExpiration = expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration();
                                if (throwable != null) {
                                    this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + item.getSeller().getName(), throwable);
                                }

                                long expiredAt = safeExpiration > 0 ? System.currentTimeMillis() + (safeExpiration * 1000) : 0;
                                item.setExpiredAt(new Date(expiredAt));

                                processedItems.add(item);
                                this.auctionManager.addItem(StorageType.EXPIRED, item);

                                // When all items are processed, batch update
                                if (remaining.decrementAndGet() == 0) {
                                    this.plugin.getScheduler().runNextTick(w -> {
                                        Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                                        batchUpdate.put(StorageType.EXPIRED, new ArrayList<>(processedItems));
                                        storageManager.updateItems(batchUpdate);
                                    });
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
