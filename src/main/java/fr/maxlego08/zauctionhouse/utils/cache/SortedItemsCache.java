package fr.maxlego08.zauctionhouse.utils.cache;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * High-performance cache for sorted item lists.
 * <p>
 * Maintains pre-sorted lists for all sort types and category combinations,
 * providing O(1) access for pagination instead of O(n log n) sorting on each request.
 * <p>
 * Thread-safe implementation using read-write locks to allow concurrent reads
 * while ensuring consistency during cache rebuilds.
 */
public class SortedItemsCache {

    private final AuctionPlugin plugin;
    private final PerformanceDebug performanceDebug;
    private final Supplier<Collection<Item>> itemsSupplier;

    // Cache for all items sorted by each SortItem type
    private final Map<SortItem, IntList> sortedAllItems = new ConcurrentHashMap<>();

    // Cache for items filtered by category, then sorted
    // Key format: "categoryId:sortItem"
    private final Map<String, IntList> sortedByCategoryItems = new ConcurrentHashMap<>();

    // Lock for rebuilding the cache
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Flag indicating the cache needs to be rebuilt
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    // Reference to ongoing async rebuild (null if none in progress)
    private final AtomicReference<CompletableFuture<Void>> ongoingRebuild = new AtomicReference<>(null);

    // Timestamp of last rebuild for debugging
    private volatile long lastRebuildTime = 0;

    public SortedItemsCache(AuctionPlugin plugin, Supplier<Collection<Item>> itemsSupplier) {
        this.plugin = plugin;
        this.performanceDebug = new PerformanceDebug(plugin);
        this.itemsSupplier = itemsSupplier;
    }

    /**
     * Gets the sorted list of item IDs for all available items.
     *
     * @param sortItem the sort order
     * @return list of item IDs in sorted order
     */
    public IntList getSortedIds(SortItem sortItem) {
        ensureCacheValid();

        lock.readLock().lock();
        try {
            IntList cached = sortedAllItems.get(sortItem);
            return cached != null ? cached.clone() : new IntArrayList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the sorted list of item IDs filtered by category.
     *
     * @param category the category to filter by (null for all items)
     * @param sortItem the sort order
     * @return list of item IDs in sorted order
     */
    public IntList getSortedIds(Category category, SortItem sortItem) {
        if (category == null) {
            return getSortedIds(sortItem);
        }

        ensureCacheValid();

        String cacheKey = buildCacheKey(category.getId(), sortItem);

        lock.readLock().lock();
        try {
            IntList cached = sortedByCategoryItems.get(cacheKey);
            return cached != null ? cached : new IntArrayList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets a paginated sublist of sorted item IDs.
     *
     * @param sortItem the sort order
     * @param offset   starting index
     * @param limit    maximum number of items to return
     * @return sublist of item IDs
     */
    public IntList getPage(SortItem sortItem, int offset, int limit) {
        IntList allIds = getSortedIds(sortItem);
        return getSubList(allIds, offset, limit);
    }

    /**
     * Gets a paginated sublist of sorted item IDs filtered by category.
     *
     * @param category the category to filter by
     * @param sortItem the sort order
     * @param offset   starting index
     * @param limit    maximum number of items to return
     * @return sublist of item IDs
     */
    public IntList getPage(Category category, SortItem sortItem, int offset, int limit) {
        IntList allIds = getSortedIds(category, sortItem);
        return getSubList(allIds, offset, limit);
    }

    /**
     * Invalidates the entire cache. The cache will be rebuilt on next access.
     */
    public void invalidate() {
        dirty.set(true);
    }

    /**
     * Forces an immediate synchronous cache rebuild.
     * Use sparingly as this blocks the calling thread.
     */
    public void rebuild() {
        rebuildCache();
    }

    /**
     * Schedules an asynchronous cache rebuild.
     */
    public void rebuildAsync() {
        plugin.getScheduler().runAsync(w -> rebuildCache());
    }

    /**
     * Returns the total number of cached items for the given sort type.
     */
    public int getTotalCount(SortItem sortItem) {
        ensureCacheValid();
        lock.readLock().lock();
        try {
            IntList cached = sortedAllItems.get(sortItem);
            return cached != null ? cached.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the total number of cached items for the given category and sort type.
     */
    public int getTotalCount(Category category, SortItem sortItem) {
        if (category == null) {
            return getTotalCount(sortItem);
        }

        ensureCacheValid();
        String cacheKey = buildCacheKey(category.getId(), sortItem);

        lock.readLock().lock();
        try {
            IntList cached = sortedByCategoryItems.get(cacheKey);
            return cached != null ? cached.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the timestamp of the last cache rebuild.
     */
    public long getLastRebuildTime() {
        return lastRebuildTime;
    }

    /**
     * Returns whether the cache is currently dirty (needs rebuild).
     */
    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Ensures the cache is valid asynchronously.
     * Returns a CompletableFuture that completes when the cache is ready.
     * If the cache is already valid, returns an already-completed future.
     * If a rebuild is already in progress, returns the existing future.
     *
     * @return CompletableFuture that completes when cache is valid
     */
    public CompletableFuture<Void> ensureCacheValidAsync() {
        // If cache is already valid, return completed future
        if (!dirty.get()) {
            return CompletableFuture.completedFuture(null);
        }

        // Check if there's already a rebuild in progress
        CompletableFuture<Void> existing = ongoingRebuild.get();
        if (existing != null && !existing.isDone()) {
            return existing;
        }

        // Create new rebuild future
        CompletableFuture<Void> newFuture = new CompletableFuture<>();

        // Try to set as the ongoing rebuild (atomic)
        if (ongoingRebuild.compareAndSet(existing, newFuture)) {
            // We won the race, start the async rebuild
            plugin.getScheduler().runAsync(w -> {
                try {
                    rebuildCache();
                    newFuture.complete(null);
                } catch (Exception e) {
                    newFuture.completeExceptionally(e);
                } finally {
                    ongoingRebuild.compareAndSet(newFuture, null);
                }
            });
            return newFuture;
        } else {
            // Another thread started rebuild, use their future
            CompletableFuture<Void> otherFuture = ongoingRebuild.get();
            return otherFuture != null ? otherFuture : CompletableFuture.completedFuture(null);
        }
    }

    private void ensureCacheValid() {
        if (dirty.get()) {
            rebuildCache();
        }
    }

    private void rebuildCache() {
        long startTime = performanceDebug.start();

        lock.writeLock().lock();
        try {
            // Double-check after acquiring lock
            if (!dirty.get()) {
                performanceDebug.end("SortedItemsCache.rebuild", startTime, "skipped (already rebuilt)");
                return;
            }

            // Get all items
            Collection<Item> allItems = itemsSupplier.get();

            // OPTIMIZATION 1: Single pass to filter available items AND group by category
            // This replaces the previous O(N × C) with O(N)
            List<Item> availableItems = new ArrayList<>(allItems.size());
            Map<String, List<Item>> itemsByCategory = new HashMap<>();

            for (Item item : allItems) {
                if (item.getStatus() == ItemStatus.AVAILABLE && !item.isExpired()) {
                    availableItems.add(item);

                    // Group by category in the same pass
                    Set<Category> categories = item.getCategories();
                    if (categories != null) {
                        for (Category category : categories) {
                            itemsByCategory.computeIfAbsent(category.getId(), k -> new ArrayList<>()).add(item);
                        }
                    }
                }
            }

            // Clear existing cache
            sortedAllItems.clear();
            sortedByCategoryItems.clear();

            int itemCount = availableItems.size();
            int categoryCount = itemsByCategory.size();

            // OPTIMIZATION 2: Sort once by each comparator, then extract IDs
            // Instead of creating new ArrayList copies, we sort in-place and extract
            // We need to sort by DATE and by PRICE only (reversed views are just reversed iteration)

            // Sort by date ascending (oldest first)
            availableItems.sort(SortItem.ASCENDING_DATE.getComparator());
            IntList ascendingDateIds = extractIds(availableItems);
            IntList descendingDateIds = reverseIds(ascendingDateIds);

            sortedAllItems.put(SortItem.ASCENDING_DATE, ascendingDateIds);
            sortedAllItems.put(SortItem.DECREASING_DATE, descendingDateIds);

            // Sort by price ascending (cheapest first)
            availableItems.sort(SortItem.ASCENDING_PRICE.getComparator());
            IntList ascendingPriceIds = extractIds(availableItems);
            IntList descendingPriceIds = reverseIds(ascendingPriceIds);

            sortedAllItems.put(SortItem.ASCENDING_PRICE, ascendingPriceIds);
            sortedAllItems.put(SortItem.DECREASING_PRICE, descendingPriceIds);

            // OPTIMIZATION 3: Process categories in parallel for large datasets
            if (categoryCount > 2 && itemCount > 10000) {
                // Parallel processing for large datasets
                itemsByCategory.entrySet().parallelStream().forEach(entry -> {
                    buildCategorySortedLists(entry.getKey(), entry.getValue());
                });
            } else {
                // Sequential processing for small datasets (less overhead)
                for (Map.Entry<String, List<Item>> entry : itemsByCategory.entrySet()) {
                    buildCategorySortedLists(entry.getKey(), entry.getValue());
                }
            }

            dirty.set(false);
            lastRebuildTime = System.currentTimeMillis();

            performanceDebug.end("SortedItemsCache.rebuild", startTime,
                    "items=" + itemCount + ", categories=" + categoryCount);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Builds sorted lists for a single category.
     * Optimized to sort only twice (by date and by price) and create reversed views.
     */
    private void buildCategorySortedLists(String categoryId, List<Item> categoryItems) {
        // Sort by date ascending
        categoryItems.sort(SortItem.ASCENDING_DATE.getComparator());
        IntList ascDateIds = extractIds(categoryItems);
        IntList descDateIds = reverseIds(ascDateIds);

        sortedByCategoryItems.put(buildCacheKey(categoryId, SortItem.ASCENDING_DATE), ascDateIds);
        sortedByCategoryItems.put(buildCacheKey(categoryId, SortItem.DECREASING_DATE), descDateIds);

        // Sort by price ascending
        categoryItems.sort(SortItem.ASCENDING_PRICE.getComparator());
        IntList ascPriceIds = extractIds(categoryItems);
        IntList descPriceIds = reverseIds(ascPriceIds);

        sortedByCategoryItems.put(buildCacheKey(categoryId, SortItem.ASCENDING_PRICE), ascPriceIds);
        sortedByCategoryItems.put(buildCacheKey(categoryId, SortItem.DECREASING_PRICE), descPriceIds);
    }

    /**
     * Extracts item IDs from a list of items.
     */
    private IntList extractIds(List<Item> items) {
        IntList ids = new IntArrayList(items.size());
        for (Item item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    /**
     * Creates a reversed copy of an IntList.
     * This is O(n) but avoids sorting again.
     */
    private IntList reverseIds(IntList source) {
        int size = source.size();
        IntList reversed = new IntArrayList(size);
        for (int i = size - 1; i >= 0; i--) {
            reversed.add(source.getInt(i));
        }
        return reversed;
    }

    private String buildCacheKey(String categoryId, SortItem sortItem) {
        return categoryId + ":" + sortItem.name();
    }

    private IntList getSubList(IntList source, int offset, int limit) {
        if (source == null || source.isEmpty()) {
            return new IntArrayList();
        }

        int size = source.size();
        if (offset >= size) {
            return new IntArrayList();
        }

        int fromIndex = Math.max(0, offset);
        int toIndex = Math.min(size, offset + limit);

        IntList result = new IntArrayList(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            result.add(source.getInt(i));
        }
        return result;
    }
}
