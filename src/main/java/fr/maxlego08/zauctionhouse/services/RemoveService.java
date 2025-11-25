package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class RemoveService implements AuctionRemoveService {

    private final AuctionPlugin plugin;

    public RemoveService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    @Override
    public void removeListedItem(Player player, Item item) {

        var event = new AuctionPreRemoveListedItemEvent(item, player);
        if (!event.callEvent()) return;

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            logger.info("Item expired");
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            inventoryManager.updateInventory(player);
            return;
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            logger.info("Item not available");
            inventoryManager.updateInventory(player);
            return;
        }

        item.setStatus(ItemStatus.IS_BEING_REMOVED);

        // 2. Vérifier si l'item est lock
        clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                logger.info("Item is not available");
                inventoryManager.updateInventory(player);
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, player.getUniqueId());

        }).thenCompose(token -> {

            // 3. On va supprimer l'item coté REDIS

            logger.info("Token: " + token);
            return clusterBridge.removeItem(item);

        }).thenCompose(v -> {

            // 4. On supprime l'item en local
            auctionManager.removeListedItem(player, item);

            return clusterBridge.unlockItem(item, LockToken.of(item));
        }).exceptionally(e -> {
            e.printStackTrace();
            return null;
        });
    }

    @Override
    public void removeExpiredItem(Player player, Item item) {

        var event = new AuctionPreRemoveExpiredItemEvent(item, player);
        if (!event.callEvent()) return;

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            logger.info("Item expired");
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_EXPIRED);
            inventoryManager.updateInventory(player);
            return;
        }

        if (item.getStatus() != ItemStatus.REMOVED) {
            logger.info("Item not available");
            inventoryManager.updateInventory(player);
            return;
        }

        item.setStatus(ItemStatus.DELETED);

        // 2. Vérifier si l'item est lock
        clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                logger.info("Item is not available");
                inventoryManager.updateInventory(player);
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, player.getUniqueId());

        }).thenCompose(token -> {

            // 3. On va supprimer l'item coté REDIS

            logger.info("Token: " + token);
            return clusterBridge.removeItem(item);

        }).thenCompose(v -> {

            // 4. On supprime l'item en local
            this.plugin.getAuctionManager().removeExpiredItem(player, item);

            return clusterBridge.unlockItem(item, LockToken.of(item));
        }).exceptionally(e -> {
            e.printStackTrace();
            return null;
        });

    }
}
