package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogContentType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.utils.AuctionItemType;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;

public class SellService extends ZUtils implements AuctionSellService {

    private final AuctionPlugin plugin;
    private final AuctionManager manager;

    public SellService(AuctionPlugin plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void sellAuctionItem(Player player, BigDecimal price, int amount, long expiredAt, ItemStack itemStack, AuctionEconomy auctionEconomy) {

        var economyManager = this.plugin.getEconomyManager();
        var configuration = this.plugin.getConfiguration();
        var ruleManager = this.plugin.getItemRuleManager();
        var maxPrice = auctionEconomy.getMaxPrice(AuctionItemType.SELL);
        var minPrice = auctionEconomy.getMinPrice(AuctionItemType.SELL);

        if (price.compareTo(maxPrice) > 0) {
            message(plugin, player, Message.PRICE_TOO_HIGH, "%max-price%", economyManager.format(auctionEconomy, maxPrice));
            return;
        }

        if (price.compareTo(minPrice) < 0) {
            message(plugin, player, Message.PRICE_TOO_LOW, "%min-price%", economyManager.format(auctionEconomy, minPrice));
            return;
        }

        long listedItems = manager.getItemsListedForSale(player).size();
        long maxSellPermission = configuration.getPermission().getLimit(AuctionItemType.SELL, player);
        if (listedItems >= maxSellPermission) {
            message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
            return;
        }

        if (configuration.getWorld().isWorldBanned(AuctionItemType.SELL, player.getWorld().getName())) {
            message(plugin, player, Message.WORLD_BANNED);
            return;
        }

        if (ruleManager.isBlacklistEnabled() && ruleManager.isBlacklisted(itemStack)) {
            message(plugin, player, Message.ITEM_BLACKLISTED);
            return;
        }

        if (ruleManager.isWhitelistEnabled() && !ruleManager.isWhitelisted(itemStack)) {
            message(plugin, player, Message.ITEM_WHITELISTED);
            return;
        }

        var clonedItemStack = itemStack.clone();
        clonedItemStack.setAmount(amount);

        removeItemInHand(player, amount);

        var storageManager = this.plugin.getStorageManager();
        storageManager.createAuctionItem(player, price, expiredAt, clonedItemStack, auctionEconomy)
                .thenAccept(auctionItem -> this.postSell(player, auctionItem, amount, price, clonedItemStack, auctionEconomy))
                .exceptionally(throwable -> {
                    this.plugin.getLogger().severe("Unable to sell item");
                    throwable.printStackTrace();
                    player.getInventory().addItem(clonedItemStack);
                    return null;
                });
    }

    private void postSell(Player player, AuctionItem auctionItem, int amount, BigDecimal price, ItemStack clonedItemStack, AuctionEconomy auctionEconomy) {

        this.manager.addItem(StorageType.LISTED, auctionItem);

        this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global
        this.manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_OWNED); // Suppression du cache du joueur

        this.manager.updateListedItems(auctionItem, true, player);

        message(this.plugin, player, Message.ITEM_SOLD, "%price%", auctionItem.getFormattedPrice(), "%amount%", clonedItemStack.getAmount(), "%item-translation-key%", auctionItem.getTranslationKey());

        this.plugin.getStorageManager().log(LogType.SALE, LogContentType.ITEM, auctionItem.getId(), player, null, clonedItemStack, price, auctionEconomy.getName(), "added_auction_item_to_listed");

        this.plugin.getAuctionClusterBridge().notifyItemSold(auctionItem).thenAccept(v -> {
            this.plugin.getLogger().info("Cluster notify item sold");
        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to notify item sold");
            throwable.printStackTrace();
            return null;
        });
    }
}
