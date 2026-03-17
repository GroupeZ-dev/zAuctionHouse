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
import fr.maxlego08.zauctionhouse.api.services.result.SellFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.SellResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SellService extends ZUtils implements AuctionSellService {

    private final AuctionPlugin plugin;
    private final AuctionManager manager;

    public SellService(AuctionPlugin plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public CompletableFuture<SellResult> sellAuctionItems(Player player, BigDecimal price, long expiredAt, Map<Integer, ItemStack> slotItems, AuctionEconomy auctionEconomy) {

        // Filter out null or air items and clone them
        var validSlotItems = slotItems.entrySet().stream().filter(entry -> entry.getValue() != null && !entry.getValue().getType().isAir()).collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().clone()));

        if (validSlotItems.isEmpty()) {
            message(this.plugin, player, Message.SELL_ERROR_AIR);
            return CompletableFuture.completedFuture(SellResult.failure("No valid items to sell", SellFailReason.INVALID_ITEM));
        }

        List<ItemStack> itemsToSell = new ArrayList<>(validSlotItems.values());

        SellFailReason validationReason = this.validateItems(player, price, auctionEconomy, itemsToSell);
        if (validationReason != SellFailReason.NONE) {
            return CompletableFuture.completedFuture(SellResult.failure("Validation failed", validationReason));
        }

        // Verify items are still in their slots before the async operation
        if (!verifyItemsInSlots(player, validSlotItems)) {
            message(this.plugin, player, Message.SELL_ERROR_CHANGE);
            return CompletableFuture.completedFuture(SellResult.failure("Items changed", SellFailReason.ITEMS_CHANGED));
        }

        CompletableFuture<SellResult> resultFuture = new CompletableFuture<>();

        // Add timeout to prevent indefinite blocking (30 seconds)
        resultFuture.completeOnTimeout(
            SellResult.failure("Operation timed out", SellFailReason.DATABASE_ERROR),
            30, TimeUnit.SECONDS
        );

        // Calculate and apply sell tax asynchronously (considers all items for item-specific rules)
        applySellTaxAsync(player, price, itemsToSell, auctionEconomy).thenAccept(taxResult -> {
            if (taxResult == null) {
                resultFuture.complete(SellResult.failure("Insufficient funds for tax", SellFailReason.INSUFFICIENT_FUNDS));
                return;
            }

            // Return to main thread to verify items and remove them
            this.plugin.getScheduler().runNextTick(task -> {
                // Re-verify that items are still in their original slots after async tax check
                if (!verifyItemsInSlots(player, validSlotItems)) {
                    message(this.plugin, player, Message.SELL_ERROR_CHANGE);
                    // Refund the tax if items changed
                    if (taxResult.hasTax()) {
                        auctionEconomy.deposit(player, taxResult.taxAmount(), "Refund sell tax (items changed)");
                    }
                    resultFuture.complete(SellResult.failure("Items changed", SellFailReason.ITEMS_CHANGED));
                    return;
                }

                // Remove items from their slots
                removeItemsFromSlots(player, validSlotItems);

                var storageManager = this.plugin.getStorageManager();
                storageManager.createAuctionItem(player, price, expiredAt, itemsToSell, auctionEconomy).thenAccept(auctionItem -> {
                    this.postSell(player, auctionItem, auctionEconomy, taxResult);
                    resultFuture.complete(SellResult.success("Item listed successfully", auctionItem));
                }).exceptionally(throwable -> {
                    this.plugin.getLogger().severe("Unable to sell item: " + throwable.getMessage());
                    if (throwable.getCause() != null) {
                        this.plugin.getLogger().severe("Caused by: " + throwable.getCause().getMessage());
                    }
                    // Return items to player safely
                    if (player.isOnline() && itemsToSell != null) {
                        itemsToSell.forEach(itemStack -> {
                            if (itemStack != null) {
                                player.getInventory().addItem(itemStack);
                            }
                        });
                    }
                    // Refund the tax if the sale failed
                    if (taxResult.hasTax()) {
                        auctionEconomy.deposit(player, taxResult.taxAmount(), "Refund sell tax (sale failed)");
                    }
                    resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
                    return null;
                });
            });
        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to check tax for sell: " + throwable.getMessage());
            resultFuture.complete(SellResult.failure("Tax calculation error", SellFailReason.TAX_ERROR));
            return null;
        });

        return resultFuture;
    }

    @Override
    public void openSellCommandInventory(Player player, BigDecimal price, AuctionEconomy auctionEconomy) {
        var cache = this.manager.getCache(player);
        var configuration = this.plugin.getConfiguration();

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
     * Verifies that all items are still in their expected inventory slots.
     *
     * @param player    the player whose inventory to check
     * @param slotItems map of slot to expected ItemStack
     * @return true if all items are still in their slots with correct amounts, false otherwise
     */
    private boolean verifyItemsInSlots(Player player, Map<Integer, ItemStack> slotItems) {
        PlayerInventory inventory = player.getInventory();

        for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
            int slot = entry.getKey();
            ItemStack expectedItem = entry.getValue();

            ItemStack currentItem;
            if (slot == MAIN_HAND_SLOT) {
                currentItem = inventory.getItemInMainHand();
            } else {
                currentItem = inventory.getItem(slot);
            }

            if (currentItem == null || !currentItem.isSimilar(expectedItem) || currentItem.getAmount() < expectedItem.getAmount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes items from their inventory slots.
     *
     * @param player    the player whose inventory to modify
     * @param slotItems map of slot to ItemStack to remove
     */
    private void removeItemsFromSlots(Player player, Map<Integer, ItemStack> slotItems) {
        PlayerInventory inventory = player.getInventory();

        for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
            int slot = entry.getKey();
            ItemStack itemToRemove = entry.getValue();
            int amountToRemove = itemToRemove.getAmount();

            if (slot == MAIN_HAND_SLOT) {
                ItemStack currentItem = inventory.getItemInMainHand();
                if (currentItem.getAmount() > amountToRemove) {
                    currentItem.setAmount(currentItem.getAmount() - amountToRemove);
                } else {
                    inventory.setItemInMainHand(null);
                }
            } else {
                ItemStack currentItem = inventory.getItem(slot);
                if (currentItem != null) {
                    if (currentItem.getAmount() > amountToRemove) {
                        currentItem.setAmount(currentItem.getAmount() - amountToRemove);
                    } else {
                        inventory.setItem(slot, null);
                    }
                }
            }
        }
    }

    /**
     * Validates if the player is allowed to sell items.
     * This method checks if the price is valid, if the player has reached the maximum
     * number of items for sale, if the world is banned, if the items are blacklisted
     * or whitelisted.
     *
     * @param player         the player who wants to sell items
     * @param price          the price of the items
     * @param auctionEconomy the economy of the items
     * @param itemStacks     the items the player wants to sell
     * @return the fail reason if validation failed, NONE if validation passed
     */
    private SellFailReason validateItems(Player player, BigDecimal price, AuctionEconomy auctionEconomy, List<ItemStack> itemStacks) {

        var economyManager = this.plugin.getEconomyManager();
        var configuration = this.plugin.getConfiguration();
        var ruleManager = this.plugin.getItemRuleManager();
        var maxPrice = auctionEconomy.getMaxPrice(ItemType.AUCTION);
        var minPrice = auctionEconomy.getMinPrice(ItemType.AUCTION);

        if (price.compareTo(maxPrice) > 0) {
            message(plugin, player, Message.PRICE_TOO_HIGH, "%max-price%", economyManager.format(auctionEconomy, maxPrice));
            return SellFailReason.PRICE_TOO_HIGH;
        }

        if (price.compareTo(minPrice) < 0) {
            message(plugin, player, Message.PRICE_TOO_LOW, "%min-price%", economyManager.format(auctionEconomy, minPrice));
            return SellFailReason.PRICE_TOO_LOW;
        }

        long listedItems = manager.getPlayerSellingItems(player).size();
        long maxSellPermission = configuration.getPermission().getLimit(ItemType.AUCTION, player);
        if (listedItems >= maxSellPermission) {
            message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
            return SellFailReason.LISTING_LIMIT_REACHED;
        }

        if (configuration.getWorld().isWorldBanned(ItemType.AUCTION, player.getWorld().getName())) {
            message(plugin, player, Message.WORLD_BANNED);
            return SellFailReason.WORLD_RESTRICTED;
        }

        for (ItemStack itemStack : itemStacks) {

            if (itemStack.getType().isAir()) {
                message(plugin, player, Message.SELL_ERROR_AIR);
                return SellFailReason.INVALID_ITEM;
            }

            if (ruleManager.isBlacklistEnabled() && ruleManager.isBlacklisted(itemStack)) {
                message(plugin, player, Message.ITEM_BLACKLISTED);
                return SellFailReason.BLACKLISTED;
            }

            if (ruleManager.isWhitelistEnabled() && !ruleManager.isWhitelisted(itemStack)) {
                message(plugin, player, Message.ITEM_WHITELISTED);
                return SellFailReason.NOT_WHITELISTED;
            }
        }
        return SellFailReason.NONE;
    }

    /**
     * Calculates and applies the sell tax for a listing asynchronously.
     * Takes into account all items being sold and uses the highest tax among them
     * (in case of item-specific tax rules).
     *
     * @param player         the player selling
     * @param price          the sale price
     * @param itemStacks     the items being sold (for item-specific rules)
     * @param auctionEconomy the economy used
     * @return a CompletableFuture containing the tax result, or null if the player cannot afford the tax
     */
    private CompletableFuture<TaxResult> applySellTaxAsync(Player player, BigDecimal price, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        var taxConfig = auctionEconomy.getTaxConfiguration();
        var economyManager = this.plugin.getEconomyManager();

        // Check if sell tax applies
        TaxType taxType = taxConfig.getTaxType();
        if (!taxConfig.isEnabled() || (taxType != TaxType.SELL && taxType != TaxType.BOTH)) {
            return CompletableFuture.completedFuture(TaxResult.disabled(price));
        }

        // Calculate tax for each item and keep the highest one
        // This ensures item-specific tax rules are respected
        TaxResult highestTaxResult = null;
        for (ItemStack itemStack : itemStacks) {
            TaxResult itemTaxResult = auctionEconomy.calculateSellTax(player, price, itemStack);
            if (highestTaxResult == null || itemTaxResult.taxAmount().compareTo(highestTaxResult.taxAmount()) > 0) {
                highestTaxResult = itemTaxResult;
            }
        }

        // Fallback if no items (shouldn't happen, but safety check)
        if (highestTaxResult == null) {
            return CompletableFuture.completedFuture(TaxResult.disabled(price));
        }

        final TaxResult taxResult = highestTaxResult;

        if (taxResult.isBypassed()) {
            message(this.plugin, player, Message.TAX_EXEMPT);
            return CompletableFuture.completedFuture(taxResult);
        }

        if (!taxResult.hasTax()) {
            return CompletableFuture.completedFuture(taxResult);
        }

        // Check if player has enough money to pay the tax asynchronously
        return auctionEconomy.has(player, taxResult.taxAmount()).thenApply(hasMoney -> {
            if (!hasMoney) {
                // Send message on main thread
                this.plugin.getScheduler().runNextTick(task -> {
                    message(this.plugin, player, Message.TAX_INSUFFICIENT_FUNDS, "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()));
                });
                return null;
            }

            // Withdraw the tax
            auctionEconomy.withdraw(player, taxResult.taxAmount(), "Sell tax (zAuctionHouse)");

            // Send appropriate messages on main thread
            this.plugin.getScheduler().runNextTick(task -> {
                if (taxResult.isReduced()) {
                    message(this.plugin, player, Message.TAX_REDUCED, "%percentage%", String.format("%.1f", 100 - taxResult.reductionPercentage()));
                }

                message(this.plugin, player, Message.TAX_SELL_APPLIED, "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()), "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
            });

            return taxResult;
        });
    }

    /**
     * Notify the cluster and the database that an auction item has been sold.
     * This method is called after an auction item has been successfully sold.
     *
     * @param player         the player who sold the auction item
     * @param auctionItem    the auction item that was sold
     * @param auctionEconomy the economy of the auction item
     * @param taxResult      the tax result from the sell operation
     */
    private void postSell(Player player, AuctionItem auctionItem, AuctionEconomy auctionEconomy, TaxResult taxResult) {

        // Ajout des catégories de l'item
        this.plugin.getCategoryManager().applyCategories(auctionItem);

        this.manager.addItem(StorageType.LISTED, auctionItem);

        this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED); // Suppression du cache global
        this.manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING); // Suppression du cache du joueur

        this.manager.updateListedItems(auctionItem, true, player);

        message(this.plugin, player, Message.ITEM_SOLD, "%price%", auctionItem.getFormattedPrice(), "%items%", auctionItem.getItemDisplay());

        String encodedItemStack = auctionItem.getItemStacks().stream().map(Base64ItemStack::encode).collect(Collectors.joining(";"));
        String additionalData = "added_auction_item_to_listed";
        if (taxResult.hasTax()) {
            additionalData += ";sell_tax=" + taxResult.taxAmount();
        }
        this.plugin.getStorageManager().log(LogType.SALE, auctionItem.getId(), player, null, encodedItemStack, auctionItem.getPrice(), auctionEconomy.getName(), additionalData, null);

        this.plugin.getAuctionClusterBridge().notifyItemListed(auctionItem).thenAccept(v -> {
            this.plugin.getLogger().info("Cluster notify item sold");
        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to notify item sold: " + throwable.getMessage());
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
