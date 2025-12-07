package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.purchase.AuctionPrePurchaseItemEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import org.bukkit.entity.Player;

import java.util.AbstractMap;
import java.util.concurrent.CompletableFuture;

public class PurchaseService extends AuctionService implements AuctionPurchaseService {

    private final AuctionPlugin plugin;

    public PurchaseService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> purchaseItem(Player player, Item item) {

        var event = new AuctionPrePurchaseItemEvent(item, player);
        if (!event.callEvent()) return CompletableFuture.completedFuture(null);

        var auctionManager = this.plugin.getAuctionManager();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var logger = this.plugin.getLogger();
        var auctionEconomy = item.getAuctionEconomy();

        var configuration = this.plugin.getConfiguration().getActions().purchased();
        if (configuration.giveItem() && configuration.freeSpace() && !item.canReceiveItem(player)){
            message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
            return CompletableFuture.completedFuture(null);
        }

        // 1. Vérifier si l'item est expiré
        if (item.isExpired()) {
            logger.info("Item expired");
            auctionManager.getCache(player).remove(PlayerCacheKey.ITEMS_LISTED);
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(null);
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            logger.info("Item not available");
            auctionManager.openMainAuction(player);
            return CompletableFuture.completedFuture(null);
        }

        item.setStatus(ItemStatus.IS_BEING_REMOVED);

        // 2. Vérifier si l'item est lock
        return clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                logger.info("Item is not available");
                inventoryManager.updateInventory(player);
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, player.getUniqueId());

        }).thenCompose(token -> auctionEconomy.has(player, item.getPrice())
                .thenApply(hasMoney -> new AbstractMap.SimpleEntry<>(token, hasMoney)))
                .thenCompose(entry -> {

                    var token = entry.getKey();

                    if (entry.getValue()) {
                        return auctionManager.purchaseItem(player, item)
                                .thenCompose(v -> clusterBridge.notifyItemBought(player, item))
                                .thenCompose(v -> clusterBridge.unlockItem(item, token));
                    }

                    return clusterBridge.unlockItem(item, token);
                }).exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });
    }
}
