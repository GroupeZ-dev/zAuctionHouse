package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemovePurchasedItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.services.AuctionRemoveService;
import org.bukkit.entity.Player;

public class RemoveService extends AuctionService implements AuctionRemoveService {

    private final AuctionPlugin plugin;

    public RemoveService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void removeListedItem(Player player, Item item) {

        var event = new AuctionPreRemoveListedItemEvent(item, player);
        if (!event.callEvent()) return;

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var logger = this.plugin.getLogger();

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            logger.info("Item expired");
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            auctionManager.openMainAuction(player);
            return;
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            logger.info("Item not available");
            auctionManager.openMainAuction(player);
            return;
        }

        item.setStatus(ItemStatus.IS_BEING_REMOVED);

        // 2. Vérifier si l'item est lock
        executeRemoval(player, item, () -> inventoryManager.updateInventory(player), () -> auctionManager.removeListedItem(player, item));
    }

    @Override
    public void removeOwnedItem(Player player, Item item) {


        var event = new AuctionPreRemoveListedItemEvent(item, player);
        if (!event.callEvent()) return;

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var logger = this.plugin.getLogger();

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            logger.info("Item expired");
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            auctionManager.openMainAuction(player);
            return;
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            logger.info("Item not available");
            auctionManager.openMainAuction(player);
            return;
        }

        item.setStatus(ItemStatus.IS_BEING_REMOVED);

        // 2. Vérifier si l'item est lock
        executeRemoval(player, item, () -> inventoryManager.updateInventory(player), () -> auctionManager.removeOwnedItem(player, item));

    }

    @Override
    public void removeExpiredItem(Player player, Item item) {

        var event = new AuctionPreRemoveExpiredItemEvent(item, player);
        if (!event.callEvent()) return;

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
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
        executeRemoval(player, item, () -> inventoryManager.updateInventory(player), () -> this.plugin.getAuctionManager().removeExpiredItem(player, item));
    }

    @Override
    public void removePurchasedItem(Player player, Item item) {

        var event = new AuctionPreRemovePurchasedItemEvent(item, player);
        if (!event.callEvent()) return;

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var logger = this.plugin.getLogger();

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            logger.info("Item expired");
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_EXPIRED);
            inventoryManager.updateInventory(player);
            return;
        }

        if (item.getStatus() != ItemStatus.PURCHASED) {
            logger.info("Item not available");
            inventoryManager.updateInventory(player);
            return;
        }

        item.setStatus(ItemStatus.DELETED);

        // 2. Vérifier si l'item est lock
        executeRemoval(player, item, () -> inventoryManager.updateInventory(player), () -> this.plugin.getAuctionManager().removePurchasedItem(player, item));

    }

    private void executeRemoval(Player player, Item item, Runnable onUnavailable, Runnable onLocalRemoval) {

        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();

        clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                logger.info("Item is not available");
                onUnavailable.run();
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, player.getUniqueId());

        }).thenCompose(token -> {

            // 3. On va supprimer l'item coté REDIS

            logger.info("Token: " + token);
            return clusterBridge.removeItem(item);

        }).thenCompose(v -> {

            // 4. On supprime l'item en local
            onLocalRemoval.run();

            return clusterBridge.unlockItem(item, LockToken.of(item));
        }).exceptionally(e -> {
            e.printStackTrace();
            return null;
        });
    }
}
