package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SellService extends ZUtils implements AuctionSellService {

    private final AuctionPlugin plugin;
    private final AuctionManager manager;

    public SellService(AuctionPlugin plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void sellAuctionItem(Player player, BigDecimal price, int amount, long expiredAt, ItemStack itemStack, AuctionEconomy auctionEconomy) {

        var clonedItemStack = itemStack.clone();
        clonedItemStack.setAmount(amount);

        if (this.invalidateItems(player, price, auctionEconomy, List.of(clonedItemStack))) return;

        var currentItemInHand = player.getInventory().getItemInMainHand();
        if (!currentItemInHand.isSimilar(itemStack) || currentItemInHand.getAmount() < amount) {
            message(this.plugin, player, Message.SELL_ERROR_CHANGE);
            return;
        }

        removeItemInHand(player, amount);

        var storageManager = this.plugin.getStorageManager();
        storageManager.createAuctionItem(player, price, expiredAt, List.of(clonedItemStack), auctionEconomy) //
                .thenAccept(auctionItem -> this.postSell(player, auctionItem, auctionEconomy)) //
                .exceptionally(throwable -> { //
                    this.plugin.getLogger().severe("Unable to sell item");
                    throwable.printStackTrace();
                    player.getInventory().addItem(clonedItemStack);
                    return null;
                });
    }

    @Override
    public void sellAuctionItems(Player player, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {

        var sellableItems = itemStacks.stream().filter(Objects::nonNull).filter(item -> !item.getType().isAir()).map(ItemStack::clone).toList();
        if (sellableItems.isEmpty()) {
            message(this.plugin, player, Message.SELL_ERROR_AIR);
            return;
        }

        if (this.invalidateItems(player, price, auctionEconomy, sellableItems)) {
            sellableItems.forEach(itemStack -> player.getInventory().addItem(itemStack));
            return;
        }

        var storageManager = this.plugin.getStorageManager();
        storageManager.createAuctionItem(player, price, expiredAt, sellableItems, auctionEconomy).thenAccept(auctionItem -> this.postSell(player, auctionItem, auctionEconomy)).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to sell item");
            throwable.printStackTrace();
            sellableItems.forEach(itemStack -> player.getInventory().addItem(itemStack));
            return null;
        });
    }

    @Override
    public void openSellCommandInventory(Player player, BigDecimal price, AuctionEconomy auctionEconomy) {
        var cache = this.manager.getCache(player);
        var configuration = this.plugin.getConfiguration();
        var economyManager = this.plugin.getEconomyManager();

        long expiration = configuration.getSellExpiration().getExpiration(player);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        cache.set(PlayerCacheKey.SELL_PRICE, price);
        cache.set(PlayerCacheKey.SELL_ECONOMY, auctionEconomy);
        cache.set(PlayerCacheKey.SELL_EXPIRED_AT, expiredAt);
        cache.set(PlayerCacheKey.SELL_AMOUNT, 1);
        cache.remove(PlayerCacheKey.SELL_ITEMS);

        this.plugin.getInventoriesLoader().openInventory(player, Inventories.SELL_INVENTORY);
    }

    /**
     * Check if the player is allowed to sell items.
     * This method checks if the price is valid, if the player has reached the maximum
     * number of items for sale, if the world is banned, if the items are blacklisted
     * or whitelisted.
     *
     * @param player         the player who wants to sell items
     * @param price          the price of the items
     * @param auctionEconomy the economy of the items
     * @param itemStacks     the items the player wants to sell
     * @return true if the player is not allowed to sell items, false otherwise
     */
    private boolean invalidateItems(Player player, BigDecimal price, AuctionEconomy auctionEconomy, List<ItemStack> itemStacks) {

        var economyManager = this.plugin.getEconomyManager();
        var configuration = this.plugin.getConfiguration();
        var ruleManager = this.plugin.getItemRuleManager();
        var maxPrice = auctionEconomy.getMaxPrice(ItemType.AUCTION);
        var minPrice = auctionEconomy.getMinPrice(ItemType.AUCTION);

        if (price.compareTo(maxPrice) > 0) {
            message(plugin, player, Message.PRICE_TOO_HIGH, "%max-price%", economyManager.format(auctionEconomy, maxPrice));
            return true;
        }

        if (price.compareTo(minPrice) < 0) {
            message(plugin, player, Message.PRICE_TOO_LOW, "%min-price%", economyManager.format(auctionEconomy, minPrice));
            return true;
        }

        long listedItems = manager.getItemsListedForSale(player).size();
        long maxSellPermission = configuration.getPermission().getLimit(ItemType.AUCTION, player);
        System.out.println(listedItems + " - " + maxSellPermission);
        if (listedItems >= maxSellPermission) {
            message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
            return true;
        }

        if (configuration.getWorld().isWorldBanned(ItemType.AUCTION, player.getWorld().getName())) {
            message(plugin, player, Message.WORLD_BANNED);
            return true;
        }

        for (ItemStack itemStack : itemStacks) {

            if (itemStack.getType().isAir()) {
                message(plugin, player, Message.SELL_ERROR_AIR);
                return true;
            }

            if (ruleManager.isBlacklistEnabled() && ruleManager.isBlacklisted(itemStack)) {
                message(plugin, player, Message.ITEM_BLACKLISTED);
                return true;
            }

            if (ruleManager.isWhitelistEnabled() && !ruleManager.isWhitelisted(itemStack)) {
                message(plugin, player, Message.ITEM_WHITELISTED);
                return true;
            }
        }
        return false;
    }

    /**
     * Notify the cluster and the database that an auction item has been sold.
     * This method is called after an auction item has been successfully sold.
     *
     * @param player         the player who sold the auction item
     * @param auctionItem    the auction item that was sold
     * @param auctionEconomy the economy of the auction item
     */
    private void postSell(Player player, AuctionItem auctionItem, AuctionEconomy auctionEconomy) {

        // Ajout des catégories de l'item
        this.plugin.getCategoryManager().applyCategories(auctionItem);

        this.manager.addItem(StorageType.LISTED, auctionItem);

        this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global
        this.manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING); // Suppression du cache du joueur

        this.manager.updateListedItems(auctionItem, true, player);

        message(this.plugin, player, Message.ITEM_SOLD, "%price%", auctionItem.getFormattedPrice(), "%items%", auctionItem.getItemDisplay());

        String encodedItemStack = auctionItem.getItemStacks().stream().map(Base64ItemStack::encode).collect(Collectors.joining(";"));
        this.plugin.getStorageManager().log(LogType.SALE, auctionItem.getId(), player, null, encodedItemStack, auctionItem.getPrice(), auctionEconomy.getName(), "added_auction_item_to_listed");

        this.plugin.getAuctionClusterBridge().notifyItemListed(auctionItem).thenAccept(v -> {
            this.plugin.getLogger().info("Cluster notify item sold");
        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to notify item sold");
            throwable.printStackTrace();
            return null;
        });

        // Discord webhook notification
        if (this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
            var discordService = zAuctionPlugin.getDiscordWebhookService();
            if (discordService != null && discordService.isEnabled()) {
                discordService.notifyItemSold(player, auctionItem);
            }
        }
    }
}
