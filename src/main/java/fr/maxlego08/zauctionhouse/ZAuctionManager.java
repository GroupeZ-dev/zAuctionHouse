package fr.maxlego08.zauctionhouse;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemovePurchasedItemEvent;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.*;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import fr.maxlego08.zauctionhouse.discord.DiscordWebhookService;
import fr.maxlego08.zauctionhouse.services.*;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import fr.maxlego08.zauctionhouse.utils.cache.SortedItemsCache;
import fr.maxlego08.zauctionhouse.utils.cache.ZPlayerCache;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ZAuctionManager extends ZUtils implements AuctionManager {

    private final AuctionPlugin plugin;
    private final AuctionPurchaseService auctionPurchaseService;
    private final AuctionSellService auctionSellService;
    private final AuctionRemoveService auctionRemoveService;
    private final AuctionExpireService auctionExpireService;
    private final AuctionClaimService auctionClaimService;
    private final AuctionHistoryService auctionHistoryService;
    private final PerformanceDebug performanceDebug;

    private final Map<Player, PlayerCache> caches = new ConcurrentHashMap<>();
    private final Map<StorageType, Map<Integer, Item>> storageItemsById = new EnumMap<>(StorageType.class);
    private final Map<UUID, IntList> idsListedByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, IntList> idsExpiredByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, IntList> idsPurchasedByBuyer = new ConcurrentHashMap<>();
    private final SortedItemsCache sortedItemsCache;

    public ZAuctionManager(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.auctionPurchaseService = new PurchaseService(plugin);
        this.auctionSellService = new SellService(plugin, this);
        this.auctionRemoveService = new RemoveService(plugin);
        this.auctionExpireService = new ExpireService(plugin, this);
        this.auctionClaimService = new ClaimService(plugin);
        this.auctionHistoryService = new HistoryService(plugin);
        this.performanceDebug = new PerformanceDebug(plugin);

        for (StorageType value : StorageType.values()) {
            this.storageItemsById.put(value, new ConcurrentHashMap<>());
        }

        // Initialize sorted items cache for LISTED items
        this.sortedItemsCache = new SortedItemsCache(plugin, () -> this.storageItemsById.get(StorageType.LISTED).values());
    }

    @Override
    public void openMainAuction(Player player) {
        this.openMainAuction(player, 1);
    }

    @Override
    public void openMainAuction(Player player, int page) {
        var inventoriesLoader = this.plugin.getInventoriesLoader();
        var cache = getCache(player);

        // Check if player's cache is already ready (fast path)
        boolean playerCacheReady = cache.has(PlayerCacheKey.ITEMS_LISTED);
        boolean globalCacheReady = !sortedItemsCache.isDirty();

        if (playerCacheReady && globalCacheReady) {
            inventoriesLoader.openInventory(player, Inventories.AUCTION, page);
        } else {
            // Cache needs preparation - do it async then open on main thread
            prepareCacheAsync(player).thenRun(() -> {
                this.plugin.getScheduler().runNextTick(w -> {
                    if (player.isOnline()) {
                        inventoriesLoader.openInventory(player, Inventories.AUCTION, page);
                    }
                });
            });
        }
    }

    /**
     * Prepares the cache asynchronously for the given player.
     * This includes ensuring the global sorted items cache is valid
     * and computing the player's item list cache.
     *
     * @param player the player to prepare the cache for
     * @return CompletableFuture that completes when the cache is ready
     */
    public CompletableFuture<Void> prepareCacheAsync(Player player) {
        return sortedItemsCache.ensureCacheValidAsync().thenRun(() -> {
            // Now compute the player's cache (this is fast after global cache is ready)
            var cache = getCache(player);
            var sort = cache.get(PlayerCacheKey.ITEM_SORT, this.plugin.getConfiguration().getSort().defaultSort());
            var category = cache.get(PlayerCacheKey.CURRENT_CATEGORY, (Category) null);

            // Pre-compute the player's items list
            cache.getOrCompute(PlayerCacheKey.ITEMS_LISTED, () -> sortedItemsCache.getSortedIds(category, sort));
        });
    }

    @Override
    public void updateInventory(Player player) {
        if (this.plugin.getServer().isPrimaryThread()) {
            this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
        } else {
            this.plugin.getScheduler().runNextTick(w -> this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player));
        }
    }

    public AuctionPlugin getPlugin() {
        return plugin;
    }

    /**
     * Returns the sorted items cache for performance optimization.
     */
    public SortedItemsCache getSortedItemsCache() {
        return sortedItemsCache;
    }

    /**
     * Rebuilds the sorted items cache asynchronously.
     * Should be called after bulk item operations (e.g., loading from database).
     */
    @Override
    public void rebuildSortedItemsCache() {
        this.sortedItemsCache.rebuildAsync();
    }

    @Override
    public AuctionPurchaseService getPurchaseService() {
        return auctionPurchaseService;
    }

    @Override
    public AuctionSellService getSellService() {
        return auctionSellService;
    }

    @Override
    public AuctionRemoveService getRemoveService() {
        return this.auctionRemoveService;
    }

    @Override
    public AuctionExpireService getExpireService() {
        return this.auctionExpireService;
    }

    @Override
    public AuctionClaimService getClaimService() {
        return this.auctionClaimService;
    }

    @Override
    public AuctionHistoryService getHistoryService() {
        return this.auctionHistoryService;
    }

    @Override
    public List<Item> getItems(StorageType storageType) {
        return new ArrayList<>(this.storageItemsById.getOrDefault(storageType, Map.of()).values());
    }

    @Override
    public List<Item> getItems(StorageType storageType, Predicate<Item> predicate) {
        return resolveItems(storageType, getItemIds(storageType, predicate, null));
    }

    @Override
    public List<Item> getItems(StorageType storageType, Predicate<Item> predicate, Comparator<Item> comparator) {
        return resolveItems(storageType, getItemIds(storageType, predicate, comparator));
    }

    @Override
    public void addItem(StorageType storageType, Item item) {
        var storage = this.storageItemsById.get(storageType);
        storage.put(item.getId(), item);
        this.indexItem(storageType, item);

        if (storageType == StorageType.LISTED) {
            this.plugin.getCategoryManager().invalidateCategoryCountCache();
            this.sortedItemsCache.invalidate();
        }
    }

    @Override
    public void removeItem(StorageType storageType, Item item) {
        removeItem(storageType, item.getId());
    }

    @Override
    public void removeItem(StorageType storageType, int itemId) {
        var storage = this.storageItemsById.get(storageType);
        if (storage == null) return;

        Item removed = storage.remove(itemId);
        if (removed != null) {
            this.deindexItem(storageType, removed);

            if (storageType == StorageType.LISTED) {
                this.plugin.getCategoryManager().invalidateCategoryCountCache();
                this.sortedItemsCache.invalidate();
            }
        }
    }

    @Override
    public List<Item> getItemsListedForSale(Player player) {
        long startTime = performanceDebug.start();

        IntList ids = getItemIdsListedForSale(player);

        performanceDebug.end("getItemsListedForSale", startTime, "items=" + ids.size());
        return resolveItems(StorageType.LISTED, ids);
    }

    @Override
    public IntList getItemIdsListedForSale(Player player) {
        long startTime = performanceDebug.start();

        var cache = getCache(player);
        var sort = cache.get(PlayerCacheKey.ITEM_SORT, this.plugin.getConfiguration().getSort().defaultSort());
        var category = cache.get(PlayerCacheKey.CURRENT_CATEGORY, (Category) null);

        // Use the global sorted items cache for O(1) access
        IntList ids = cache.getOrCompute(PlayerCacheKey.ITEMS_LISTED, () -> sortedItemsCache.getSortedIds(category, sort));

        performanceDebug.end("getItemIdsListedForSale", startTime, "sort=" + sort + ", category=" + (category != null ? category.getId() : "all") + ", ids=" + ids.size());
        return ids;
    }

    @Override
    public List<Item> getExpiredItems(Player player) {
        IntList ids = getCache(player).getOrCompute(PlayerCacheKey.ITEMS_EXPIRED, () -> getItemIds(StorageType.EXPIRED, item -> item.getSellerUniqueId().equals(player.getUniqueId()), Comparator.comparing(Item::getExpiredAt)));
        return resolveItems(StorageType.EXPIRED, ids);
    }

    @Override
    public List<Item> getExpiredItems(UUID uniqueId) {
        return resolveItems(StorageType.EXPIRED, getItemIds(StorageType.EXPIRED, item -> item.getSellerUniqueId().equals(uniqueId), Comparator.comparing(Item::getExpiredAt)));
    }

    @Override
    public List<Item> getPlayerSellingItems(Player player) {
        IntList ids = getCache(player).getOrCompute(PlayerCacheKey.ITEMS_SELLING, () -> getItemIds(StorageType.LISTED, item -> item.getSellerUniqueId().equals(player.getUniqueId()), Comparator.comparing(Item::getExpiredAt)));
        return resolveItems(StorageType.LISTED, ids);
    }

    @Override
    public List<Item> getPlayerSellingItems(UUID uniqueId) {
        return resolveItems(StorageType.LISTED, getItemIds(StorageType.LISTED, item -> item.getSellerUniqueId().equals(uniqueId), Comparator.comparing(Item::getExpiredAt)));
    }

    @Override
    public List<Item> getPurchasedItems(Player player) {
        IntList ids = getCache(player).getOrCompute(PlayerCacheKey.ITEMS_PURCHASED, () -> getItemIds(StorageType.PURCHASED, item -> item.getBuyerUniqueId() != null && item.getBuyerUniqueId().equals(player.getUniqueId()), Comparator.comparing(Item::getExpiredAt)));
        return resolveItems(StorageType.PURCHASED, ids);
    }

    @Override
    public List<Item> getPurchasedItems(UUID uniqueId) {
        return resolveItems(StorageType.PURCHASED, getItemIds(StorageType.PURCHASED, item -> item.getBuyerUniqueId() != null && item.getBuyerUniqueId().equals(uniqueId), Comparator.comparing(Item::getExpiredAt)));
    }

    @Override
    public List<Item> resolveItems(StorageType storageType, IntList ids) {
        long startTime = performanceDebug.start();

        if (ids == null || ids.isEmpty()) {
            performanceDebug.end("resolveItems[" + storageType + "]", startTime, "empty ids");
            return List.of();
        }

        Map<Integer, Item> storage = this.storageItemsById.get(storageType);
        if (storage == null || storage.isEmpty()) {
            performanceDebug.end("resolveItems[" + storageType + "]", startTime, "empty storage");
            return List.of();
        }

        List<Item> resolved = new ArrayList<>(ids.size());
        for (int id : ids) {
            Item item = storage.get(id);
            if (item != null) {
                resolved.add(item);
            }
        }

        performanceDebug.end("resolveItems[" + storageType + "]", startTime, "requested=" + ids.size() + ", resolved=" + resolved.size());
        return resolved;
    }

    @Override
    public List<Item> resolveItemsForPage(StorageType storageType, IntList allIds, int page, int pageSize) {
        long startTime = performanceDebug.start();

        if (allIds == null || allIds.isEmpty()) {
            performanceDebug.end("resolveItemsForPage[" + storageType + "]", startTime, "empty ids");
            return List.of();
        }

        int start = page * pageSize;
        if (start >= allIds.size()) {
            performanceDebug.end("resolveItemsForPage[" + storageType + "]", startTime, "page out of range");
            return List.of();
        }

        int end = Math.min(start + pageSize, allIds.size());

        // Create a subset of IDs for this page
        IntList pageIds = new IntArrayList(end - start);
        for (int i = start; i < end; i++) {
            pageIds.add(allIds.getInt(i));
        }

        List<Item> resolved = resolveItems(storageType, pageIds);

        performanceDebug.end("resolveItemsForPage[" + storageType + "]", startTime, "page=" + page + ", pageSize=" + pageSize + ", resolved=" + resolved.size() + "/" + allIds.size());
        return resolved;
    }

    public List<Item> onPlayerOpenMenu(Player player) {
        IntList ids = getCache(player).get(PlayerCacheKey.ITEMS_LISTED, new IntArrayList());
        return resolveItems(StorageType.LISTED, ids);
    }

    private IntList getItemIds(StorageType storageType, Predicate<Item> predicate, Comparator<Item> comparator) {
        long startTime = performanceDebug.start();

        Map<Integer, Item> items = this.storageItemsById.get(storageType);
        if (items == null || items.isEmpty()) {
            performanceDebug.end("getItemIds[" + storageType + "]", startTime, "empty");
            return new IntArrayList();
        }

        List<Item> filtered = new ArrayList<>();
        List<Item> expiredItems = new ArrayList<>();

        for (Item item : items.values()) {
            if (item.isExpired()) {
                expiredItems.add(item);
                continue;
            }

            if (predicate.test(item)) {
                filtered.add(item);
            }
        }

        if (!expiredItems.isEmpty()) {
            this.auctionExpireService.processExpiredItems(expiredItems, storageType);
        }

        if (comparator != null && filtered.size() > 1) {
            filtered.sort(comparator);
        }

        IntList ids = new IntArrayList(filtered.size());
        for (Item item : filtered) {
            ids.add(item.getId());
        }

        performanceDebug.end("getItemIds[" + storageType + "]", startTime, "total=" + items.size() + ", filtered=" + ids.size() + ", expired=" + expiredItems.size() + ", sorted=" + (comparator != null));
        return ids;
    }

    private void indexItem(StorageType storageType, Item item) {
        Map<UUID, IntList> index = getIndexFor(storageType);
        if (index == null) return;

        UUID owner = getOwner(storageType, item);
        addToIndex(index, owner, item.getId());
    }

    private void deindexItem(StorageType storageType, Item item) {
        Map<UUID, IntList> index = getIndexFor(storageType);
        if (index == null) return;

        UUID owner = getOwner(storageType, item);
        removeFromIndex(index, owner, item.getId());
    }

    private Map<UUID, IntList> getIndexFor(StorageType storageType) {
        return switch (storageType) {
            case LISTED -> this.idsListedByOwner;
            case EXPIRED -> this.idsExpiredByOwner;
            case PURCHASED -> this.idsPurchasedByBuyer;
            default -> null;
        };
    }

    private UUID getOwner(StorageType storageType, Item item) {
        return switch (storageType) {
            case LISTED, EXPIRED -> item.getSellerUniqueId();
            case PURCHASED -> item.getBuyerUniqueId();
            default -> null;
        };
    }

    private void addToIndex(Map<UUID, IntList> index, UUID owner, int itemId) {
        if (owner == null) return;

        index.computeIfAbsent(owner, uuid -> new IntArrayList()).add(itemId);
    }

    private void removeFromIndex(Map<UUID, IntList> index, UUID owner, int itemId) {
        if (owner == null) return;

        IntList ids = index.get(owner);
        if (ids == null) return;

        ids.rem(itemId);
        if (ids.isEmpty()) {
            index.remove(owner);
        }
    }

    @Override
    public PlayerCache getCache(Player player) {
        return this.caches.computeIfAbsent(player, p -> new ZPlayerCache());
    }

    @Override
    public void clearPlayersCache(PlayerCacheKey... keys) {
        this.caches.forEach((player, cache) -> cache.remove(keys));
    }

    @Override
    public void clearPlayerCache(Player player, PlayerCacheKey... keys) {
        getCache(player).remove(keys);
    }

    @Override
    public void removeCache(Player player) {
        this.caches.remove(player);
    }

    @Override
    public CompletableFuture<Void> removeListedItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        item.setStatus(ItemStatus.REMOVED);
        removeItem(StorageType.LISTED, item);

        this.updateListedItems(item, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED); // Suppression du cache du joueur

        CompletableFuture<Void> updateFuture;

        if (configuration.getActions().listed().giveItem() && item.canReceiveItem(player)) {

            updateFuture = storageManager.updateItem(item, StorageType.DELETED);
            giveItem(player, item);

        } else {

            var expiration = configuration.getExpireExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            item.setExpiredAt(new Date(expiredAt));

            addItem(StorageType.EXPIRED, item);
            updateFuture = storageManager.updateItem(item, StorageType.EXPIRED);
        }

        message(this.plugin, player, Message.ITEM_REMOVE_LISTED, "%items%", item.getItemDisplay());

        if (configuration.getActions().listed().openInventory()) {
            openMainAuction(player, getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1));
        } else {
            player.closeInventory();
        }

        callEvent(new AuctionRemoveListedItemEvent(item, player));

        logItemAction(LogType.REMOVE_LISTED, item, player, null, "removed_from_listed");

        return updateFuture;
    }

    @Override
    public CompletableFuture<Void> removeSellingItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        item.setStatus(ItemStatus.DELETED);
        removeItem(StorageType.LISTED, item);

        this.updateListedItems(item, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED); // Suppression du cache du joueur

        var updateFuture = storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

        message(this.plugin, player, Message.ITEM_REMOVE_SELLING, "%items%", item.getItemDisplay());

        if (configuration.getActions().listed().openInventory()) {
            this.updateInventory(player);
        } else {
            player.closeInventory();
        }

        callEvent(new AuctionRemoveListedItemEvent(item, player));

        logItemAction(LogType.REMOVE_SELLING, item, player, null, "removed_selling_item");

        return updateFuture;
    }

    @Override
    public CompletableFuture<Void> removeExpiredItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        removeItem(StorageType.EXPIRED, item);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);

        var updateFuture = storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

        message(this.plugin, player, Message.ITEM_REMOVE_EXPIRED, "%items%", item.getItemDisplay());

        if (configuration.getActions().expired().openInventory()) {
            this.updateInventory(player);
        } else {
            player.closeInventory();
        }

        callEvent(new AuctionRemoveExpiredItemEvent(item, player));

        logItemAction(LogType.REMOVE_EXPIRED, item, player, null, "removed_expired_item");

        return updateFuture;
    }

    @Override
    public CompletableFuture<Void> removePurchasedItem(Player player, Item item) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        removeItem(StorageType.PURCHASED, item);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);

        var updateFuture = storageManager.updateItem(item, StorageType.DELETED);
        giveItem(player, item);

        message(this.plugin, player, Message.ITEM_REMOVE_PURCHASED, "%items%", item.getItemDisplay());

        if (configuration.getActions().purchased().openInventory()) {
            this.updateInventory(player);
        } else {
            player.closeInventory();
        }

        callEvent(new AuctionRemovePurchasedItemEvent(item, player));

        logItemAction(LogType.REMOVE_PURCHASED, item, player, item.getSellerUniqueId(), "removed_purchased_item");

        return updateFuture;

    }

    @Override
    public void adminRemoveItem(Player admin, UUID targetUniqueId, Item item, StorageType storageType) {

        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var inventoryManager = this.plugin.getInventoriesLoader().getInventoryManager();

        clusterBridge.checkAvailability(item).thenCompose(available -> {

            if (!available) {
                this.plugin.getLogger().info("Item is not available");
                inventoryManager.updateInventory(admin);
                return failedFuture(new IllegalStateException("Item introuvable"));
            }

            return clusterBridge.lockItem(item, admin.getUniqueId(), storageType);

        }).thenCompose(lockToken -> clusterBridge.removeItem(item, storageType).thenApply(v -> lockToken)).thenAccept(lockToken -> {

            removeItem(storageType, item);

            this.plugin.getStorageManager().updateItem(item, StorageType.DELETED);
            clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING);

            giveItem(admin, item);

            var targetName = item.getSellerUniqueId().equals(targetUniqueId) ? item.getSellerName() : item.getBuyerName();
            message(this.plugin, admin, Message.ADMIN_ITEM_REMOVED, "%items%", item.getItemDisplay(), "%target%", targetName == null ? "unknown" : targetName);

            inventoryManager.updateInventory(admin);

            clusterBridge.unlockItem(item, lockToken, storageType);

        }).exceptionally(e -> {
            e.printStackTrace();
            inventoryManager.updateInventory(admin);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> purchaseItem(Player player, Item item) {
        if (item instanceof AuctionItem auctionItem) {
            return purchaseAuctionItem(player, auctionItem);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> purchaseAuctionItem(Player player, AuctionItem auctionItem) {

        var auctionEconomy = auctionItem.getAuctionEconomy();
        var price = auctionItem.getPrice();
        var seller = auctionItem.getSeller();
        var storageManager = this.plugin.getStorageManager();
        var configuration = this.plugin.getConfiguration();
        var cache = this.getCache(player);
        var economyName = auctionEconomy.getName();
        var economyManager = this.plugin.getEconomyManager();

        String items = auctionItem.getItemsAsString();
        var itemsDisplay = auctionItem.getItemDisplay();

        // Calculate purchase tax
        var taxConfig = auctionEconomy.getTaxConfiguration();
        TaxType taxType = taxConfig.getTaxType();
        TaxResult taxResult = TaxResult.disabled(price);

        // Get representative item for item-specific tax rules
        var itemStacks = auctionItem.getItemStacks();
        var representativeItem = itemStacks != null && !itemStacks.isEmpty() ? itemStacks.getFirst() : null;

        if (taxConfig.isEnabled() && (taxType == TaxType.PURCHASE || taxType == TaxType.BOTH || taxType == TaxType.CAPITALISM)) {
            taxResult = auctionEconomy.calculatePurchaseTax(player, price, representativeItem);

            if (taxResult.isBypassed()) {
                message(this.plugin, player, Message.TAX_EXEMPT);
            }
        }

        // Calculate what buyer pays and seller receives
        BigDecimal buyerPays;
        BigDecimal sellerReceives;

        if (taxResult.hasTax()) {
            if (taxType == TaxType.CAPITALISM) {
                // VAT: buyer pays price + tax, seller receives full price
                buyerPays = taxResult.finalPrice(); // This is price + tax for CAPITALISM
                sellerReceives = price;

                // Send tax info message
                if (taxResult.isReduced()) {
                    message(this.plugin, player, Message.TAX_REDUCED, "%percentage%", String.format("%.1f", 100 - taxResult.reductionPercentage()));
                }
                message(player, Message.TAX_CAPITALISM_INFO, "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()), "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
            } else {
                // PURCHASE or BOTH: buyer pays full price, seller receives price - tax
                buyerPays = price;
                sellerReceives = taxResult.finalPrice(); // This is price - tax for PURCHASE/BOTH

                // Send tax info message
                if (taxResult.isReduced()) {
                    message(this.plugin, player, Message.TAX_REDUCED, "%percentage%", String.format("%.1f", 100 - taxResult.reductionPercentage()));
                }
                message(player, Message.TAX_PURCHASE_APPLIED, "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()), "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
            }
        } else {
            buyerPays = price;
            sellerReceives = price;
        }

        // On retire l'argent de l'acheteur
        auctionEconomy.withdraw(player, buyerPays, args(auctionEconomy.getWithdrawReason(), "%seller%", auctionItem.getSellerName(), "%items%", items));

        // On donne l'argent au vendeur
        TransactionStatus transactionStatus;
        if ((!seller.isOnline() && auctionEconomy.mustBeOnline()) || !auctionEconomy.isAutoClaim()) {

            transactionStatus = TransactionStatus.PENDING;
        } else {

            transactionStatus = TransactionStatus.RETRIEVED;
            auctionEconomy.deposit(seller, sellerReceives, args(auctionEconomy.getDepositReason(), "%buyer%", player.getName(), "%items%", items));
        }

        // Créer les transactions
        final BigDecimal finalBuyerPays = buyerPays;
        final BigDecimal finalSellerReceives = sellerReceives;
        auctionEconomy.get(player).thenAccept(buyerBalance -> {
            storageManager.createTransaction(auctionItem, player.getUniqueId(), economyName, buyerBalance.add(finalBuyerPays), buyerBalance, finalBuyerPays.negate(), TransactionStatus.RETRIEVED);
        });

        auctionEconomy.get(seller).thenAccept(sellerBalance -> {
            storageManager.createTransaction(auctionItem, seller.getUniqueId(), economyName, sellerBalance.subtract(finalSellerReceives), sellerBalance, finalSellerReceives, transactionStatus);
        });

        if (seller.isOnline()) {
            message(this.plugin, seller.getPlayer(), Message.ITEM_BOUGHT_SELLER, "%items%", itemsDisplay, "%price%", economyManager.format(auctionEconomy, sellerReceives), "%seller%", auctionItem.getSellerName(), "%buyer%", player.getName());
        }

        message(player, Message.ITEM_BOUGHT_BUYER, "%items%", itemsDisplay, "%price%", economyManager.format(auctionEconomy, buyerPays), "%seller%", auctionItem.getSellerName(), "%buyer%", player.getName());

        auctionItem.setBuyer(player);
        auctionItem.setStatus(ItemStatus.PURCHASED);

        this.updateListedItems(auctionItem, false, player);
        clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);
        if (seller.isOnline()) {
            clearPlayerCache(seller.getPlayer(), PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.HISTORY_DATA);
        }

        removeItem(StorageType.LISTED, auctionItem);

        var purchasedConfiguration = configuration.getActions().purchased();

        CompletableFuture<Void> updateFuture;

        if (purchasedConfiguration.giveItem()) {

            updateFuture = storageManager.updateItem(auctionItem, StorageType.DELETED);
            giveItem(player, auctionItem);

        } else {

            var expiration = configuration.getPurchaseExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            auctionItem.setExpiredAt(new Date(expiredAt));

            addItem(StorageType.PURCHASED, auctionItem);
            updateFuture = storageManager.updateItem(auctionItem, StorageType.PURCHASED);
        }

        cache.remove(PlayerCacheKey.ITEM_SHOW);
        if (purchasedConfiguration.openInventory()) {
            openMainAuction(player, cache.get(PlayerCacheKey.CURRENT_PAGE, 1));
        } else {
            player.closeInventory();
        }

        logItemAction(LogType.PURCHASE, auctionItem, player, auctionItem.getSellerUniqueId(), "purchase_item");

        // Discord webhook notification
        if (this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
            DiscordWebhookService discordService = zAuctionPlugin.getDiscordWebhookService();
            if (discordService != null && discordService.isEnabled()) {
                discordService.notifyItemPurchased(player, auctionItem);
            }
        }

        return updateFuture;
    }

    @Override
    public void message(Player player, Message message, Object... args) {
        this.message(this.plugin, player, message, args);
    }

    public void giveItem(Player player, Item item) {
        if (item instanceof AuctionItem auctionItem) {

            var itemStacks = auctionItem.getItemStacks();
            for (ItemStack itemStack : itemStacks) {
                player.getInventory().addItem(itemStack).forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));
            }

        } else plugin.getLogger().severe("give item not implemented");
    }

    @Override
    public void updateListedItems(Item item, boolean added, Player ignoredPlayer) {

        if (!this.plugin.getConfiguration().getActions().updateInventoryOnAction()) {
            if (!added) {
                for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
                    removeFromCache(onlinePlayer, item);
                }
            }
            return;
        }

        if (!added && ignoredPlayer != null) removeFromCache(ignoredPlayer, item);

        this.plugin.getScheduler().runAsync(w -> {
            for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {

                if (onlinePlayer == ignoredPlayer) continue;

                var openInventory = onlinePlayer.getOpenInventory().getTopInventory().getHolder();
                if (openInventory instanceof InventoryEngine inventoryEngine) {
                    var buttons = inventoryEngine.getMenuInventory().getButtons(ListedItemsButton.class);
                    if (buttons.isEmpty()) continue;

                    var listedItemsButton = buttons.getFirst();
                    listedItemsButton.updateInventory(onlinePlayer, inventoryEngine, item, added, this);
                }

                if (!added) removeFromCache(onlinePlayer, item);
            }
        });
    }

    private void removeFromCache(Player player, Item item) {
        if (this.caches.containsKey(player)) {
            var cache = this.caches.get(player);
            IntList items = cache.get(PlayerCacheKey.ITEMS_LISTED);
            items.rem(item.getId());
        }
    }

    private <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    private void logItemAction(LogType logType, Item item, Player player, UUID targetUniqueId, String additionalData) {
        var storageManager = this.plugin.getStorageManager();
        var economy = item.getAuctionEconomy();
        var economyName = economy == null ? null : economy.getName();

        String encodedItemStack = null;
        if (item instanceof AuctionItem auctionItem) {
            var itemStacks = auctionItem.getItemStacks();
            if (itemStacks != null && !itemStacks.isEmpty()) {
                encodedItemStack = itemStacks.stream().map(Base64ItemStack::encode).collect(Collectors.joining(";"));
            }
        }

        storageManager.log(logType, item.getId(), player, targetUniqueId, encodedItemStack, item.getPrice(), economyName, additionalData);
    }

    private void callEvent(AuctionEvent auctionEvent) {
        if (this.plugin.getServer().isPrimaryThread()) {
            auctionEvent.callEvent();
        } else {
            this.plugin.getScheduler().runNextTick(w -> auctionEvent.callEvent());
        }
    }

    @Override
    public void updateItemEconomies() {
        var economyManager = this.plugin.getEconomyManager();
        int updatedCount = 0;
        int missingCount = 0;

        for (StorageType storageType : StorageType.values()) {
            Map<Integer, Item> items = this.storageItemsById.get(storageType);
            if (items == null || items.isEmpty()) continue;

            for (Item item : items.values()) {
                String economyName = item.getEconomyName();
                var optionalEconomy = economyManager.getEconomy(economyName);

                if (optionalEconomy.isPresent()) {
                    item.setAuctionEconomy(optionalEconomy.get());
                    updatedCount++;
                } else {
                    this.plugin.getLogger().warning("Economy '" + economyName + "' not found for item ID " + item.getId() + " in " + storageType.name() + ". The item will keep its old economy reference.");
                    missingCount++;
                }
            }
        }

        if (updatedCount > 0 || missingCount > 0) {
            this.plugin.getLogger().info("Economy update completed: " + updatedCount + " items updated, " + missingCount + " items with missing economy.");
        }
    }
}
