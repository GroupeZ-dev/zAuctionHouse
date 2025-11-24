package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.items.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

public class SellService extends ZUtils implements AuctionSellService {

    private final AuctionPlugin plugin;
    private final AuctionManager auctionManager;

    public SellService(AuctionPlugin plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @Override
    public void sellAuctionItem(Player player, BigDecimal price, int amount, long expiredAt, ItemStack itemStack, AuctionEconomy auctionEconomy) {

        var clonedItemStack = itemStack.clone();
        clonedItemStack.setAmount(amount);

        removeItemInHand(player, amount);

        var storageManager = this.plugin.getStorageManager();
        storageManager.createAuctionItem(player, price, expiredAt, clonedItemStack, auctionEconomy).thenAccept(auctionItem -> this.postSell(player, auctionItem, amount, price, clonedItemStack, auctionEconomy)).exceptionally(throwable -> {
            throwable.printStackTrace();
            player.getInventory().addItem(clonedItemStack);
            return null;
        });
    }

    private void postSell(Player player, AuctionItem auctionItem, int amount, BigDecimal price, ItemStack clonedItemStack, AuctionEconomy auctionEconomy) {

        this.auctionManager.addItem(StorageType.LISTED, auctionItem);

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global
        this.auctionManager.clearPlayerCache(player, PlayerCacheKey.ITEMS_OWNED); // Suppression du cache du joueur

        this.plugin.getAuctionClusterBridge().notifyItemSold(auctionItem).thenAccept(v -> {
            this.plugin.getLogger().info("Cluster notify item sold");
        });
    }
}
