package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.inventory.SellInventoryHolder;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

        if (!this.validateSell(player, price, auctionEconomy, List.of(clonedItemStack))) return;

        removeItemInHand(player, amount);

        var storageManager = this.plugin.getStorageManager();
        storageManager.createAuctionItem(player, price, expiredAt, List.of(clonedItemStack), auctionEconomy)
                .thenAccept(auctionItem -> this.postSell(player, auctionItem, auctionEconomy))
                .exceptionally(throwable -> {
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

        if (!this.validateSell(player, price, auctionEconomy, sellableItems)) {
            sellableItems.forEach(itemStack -> player.getInventory().addItem(itemStack));
            return;
        }

        var storageManager = this.plugin.getStorageManager();
        storageManager.createAuctionItem(player, price, expiredAt, sellableItems, auctionEconomy)
                .thenAccept(auctionItem -> this.postSell(player, auctionItem, auctionEconomy))
                .exceptionally(throwable -> {
                    this.plugin.getLogger().severe("Unable to sell item");
                    throwable.printStackTrace();
                    sellableItems.forEach(itemStack -> player.getInventory().addItem(itemStack));
                    return null;
                });
    }

    @Override
    public void openSellInventory(Player player, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {

        SellInventoryHolder holder = new SellInventoryHolder(player.getUniqueId(), price, expiredAt, auctionEconomy);
        Inventory inventory = this.plugin.getServer().createInventory(holder, 54, ChatColor.translateAlternateColorCodes('&', this.getMessage(Message.SELL_INVENTORY_TITLE)));
        holder.setInventory(inventory);

        this.decorateInventory(inventory, price, auctionEconomy);

        player.openInventory(inventory);
    }

    private void decorateInventory(Inventory inventory, BigDecimal price, AuctionEconomy auctionEconomy) {

        var economyManager = this.plugin.getEconomyManager();
        var confirmItem = new ItemStack(Material.LIME_CONCRETE);
        var confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', this.getMessage(Message.SELL_INVENTORY_CONFIRM_NAME)));
        confirmMeta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', this.getMessage(Message.SELL_INVENTORY_CONFIRM_LORE, "%price%", economyManager.format(auctionEconomy, price))),
                ChatColor.translateAlternateColorCodes('&', this.getMessage(Message.SELL_INVENTORY_CONFIRM_ECONOMY, "%economy%", auctionEconomy.getName()))
        ));
        confirmItem.setItemMeta(confirmMeta);

        var cancelItem = new ItemStack(Material.RED_CONCRETE);
        var cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', this.getMessage(Message.SELL_INVENTORY_CANCEL_NAME)));
        cancelMeta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', this.getMessage(Message.SELL_INVENTORY_CANCEL_LORE))));
        cancelItem.setItemMeta(cancelMeta);

        var decoration = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var decorationMeta = decoration.getItemMeta();
        decorationMeta.setDisplayName(" ");
        decoration.setItemMeta(decorationMeta);

        Set<Integer> lockedSlots = SellInventoryHolder.getLockedSlots();
        for (int i = 45; i < inventory.getSize(); i++) {
            if (lockedSlots.contains(i)) {
                inventory.setItem(i, decoration);
            }
        }

        inventory.setItem(SellInventoryHolder.CONFIRM_SLOT, confirmItem);
        inventory.setItem(SellInventoryHolder.CANCEL_SLOT, cancelItem);
    }

    private boolean validateSell(Player player, BigDecimal price, AuctionEconomy auctionEconomy, List<ItemStack> itemStacks) {

        var economyManager = this.plugin.getEconomyManager();
        var configuration = this.plugin.getConfiguration();
        var ruleManager = this.plugin.getItemRuleManager();
        var maxPrice = auctionEconomy.getMaxPrice(ItemType.AUCTION);
        var minPrice = auctionEconomy.getMinPrice(ItemType.AUCTION);

        if (price.compareTo(maxPrice) > 0) {
            message(plugin, player, Message.PRICE_TOO_HIGH, "%max-price%", economyManager.format(auctionEconomy, maxPrice));
            return false;
        }

        if (price.compareTo(minPrice) < 0) {
            message(plugin, player, Message.PRICE_TOO_LOW, "%min-price%", economyManager.format(auctionEconomy, minPrice));
            return false;
        }

        long listedItems = manager.getItemsListedForSale(player).size();
        long maxSellPermission = configuration.getPermission().getLimit(ItemType.AUCTION, player);
        if (listedItems >= maxSellPermission) {
            message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
            return false;
        }

        if (configuration.getWorld().isWorldBanned(ItemType.AUCTION, player.getWorld().getName())) {
            message(plugin, player, Message.WORLD_BANNED);
            return false;
        }

        for (ItemStack itemStack : itemStacks) {

            if (itemStack.getType().isAir()) {
                message(plugin, player, Message.SELL_ERROR_AIR);
                return false;
            }

            if (ruleManager.isBlacklistEnabled() && ruleManager.isBlacklisted(itemStack)) {
                message(plugin, player, Message.ITEM_BLACKLISTED);
                return false;
            }

            if (ruleManager.isWhitelistEnabled() && !ruleManager.isWhitelisted(itemStack)) {
                message(plugin, player, Message.ITEM_WHITELISTED);
                return false;
            }
        }
        return true;
    }

    private void postSell(Player player, AuctionItem auctionItem, AuctionEconomy auctionEconomy) {

        this.manager.addItem(StorageType.LISTED, auctionItem);

        this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global
        this.manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_OWNED); // Suppression du cache du joueur

        this.manager.updateListedItems(auctionItem, true, player);

        message(this.plugin, player, Message.ITEM_SOLD, "%price%", auctionItem.getFormattedPrice(), "%items%", auctionItem.getItemDisplay());

        this.plugin.getStorageManager().log(LogType.SALE, auctionItem.getId(), player, null, auctionItem.getPrice(), auctionEconomy.getName(), "added_auction_item_to_listed");

        this.plugin.getAuctionClusterBridge().notifyItemListed(auctionItem).thenAccept(v -> {
            this.plugin.getLogger().info("Cluster notify item sold");
        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to notify item sold");
            throwable.printStackTrace();
            return null;
        });
    }
}
