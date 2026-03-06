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
import java.util.concurrent.ForkJoinPool;
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
    // Using AtomicReference for lock-free reads with copy-on-write semantics
    private final AtomicReference<Map<SortItem, IntList>> sortedAllItems = new AtomicReference<>(new ConcurrentHashMap<>());

    // Cache for items filtered by category, then sorted
    // Key format: "categoryId:sortItem"
    private final AtomicReference<Map<String, IntList>> sortedByCategoryItems = new AtomicReference<>(new ConcurrentHashMap<>());

    // Lock for rebuilding the cache (only used during rebuild, not for reads)
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Flag indicating the cache needs to be rebuilt
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    // Flag indicating a rebuild is currently in progress
    private final AtomicBoolean rebuildInProgress = new AtomicBoolean(false);

    // Reference to ongoing async rebuild (null if none in progress)
    private final AtomicReference<CompletableFuture<Void>> ongoingRebuild = new AtomicReference<>(null);

    // Timestamp of last rebuild for debugging
    private volatile long lastRebuildTime = 0;

    // Configurable thresholds (loaded from config)
    private final int parallelSortThreshold;
    private final int parallelCategoryThreshold;

    // Custom ForkJoinPool for parallel operations (avoids blocking common pool)
    private final ForkJoinPool forkJoinPool;

    public SortedItemsCache(AuctionPlugin plugin, Supplier<Collection<Item>> itemsSupplier) {
        this.plugin = plugin;
        this.performanceDebug = new PerformanceDebug(plugin);
        this.itemsSupplier = itemsSupplier;

        // Load configurable thresholds
        var performanceConfig = plugin.getConfiguration().getPerformance();
        this.parallelSortThreshold = performanceConfig.parallelSortThreshold();
        this.parallelCategoryThreshold = performanceConfig.parallelCategoryThreshold();

        // Use a dedicated pool with configurable parallelism
        this.forkJoinPool = new ForkJoinPool(performanceConfig.getEffectiveParallelism());
    }

    /**
     * Gets the sorted list of item IDs for all available items.
     * <p>
     * This method is NON-BLOCKING. If the cache is dirty, it triggers an async
     * rebuild and returns the current (possibly stale) data immediately.
     * This ensures players are never blocked waiting for cache rebuilds.
     *
     * @param sortItem the sort order
     * @return list of item IDs in sorted order (may be stale during rebuild)
     */
    public IntList getSortedIds(SortItem sortItem) {
        // Trigger async rebuild if needed (non-blocking)
        triggerRebuildIfNeeded();

        // Lock-free read from AtomicReference
        Map<SortItem, IntList> cache = sortedAllItems.get();
        IntList cached = cache.get(sortItem);
        return cached != null ? cached.clone() : new IntArrayList();
    }

    /**
     * Gets the sorted list of item IDs filtered by category.
     * <p>
     * This method is NON-BLOCKING. Returns current data immediately,
     * triggering async rebuild if cache is dirty.
     *
     * @param category the category to filter by (null for all items)
     * @param sortItem the sort order
     * @return list of item IDs in sorted order (may be stale during rebuild)
     */
    public IntList getSortedIds(Category category, SortItem sortItem) {
        if (category == null) {
            return getSortedIds(sortItem);
        }

        // Trigger async rebuild if needed (non-blocking)
        triggerRebuildIfNeeded();

        String cacheKey = buildCacheKey(category.getId(), sortItem);

        // Lock-free read from AtomicReference
        Map<String, IntList> cache = sortedByCategoryItems.get();
        IntList cached = cache.get(cacheKey);
        return cached != null ? cached.clone() : new IntArrayList();
    }

    /**
     * Triggers an async cache rebuild if the cache is dirty and no rebuild is in progress.
     * This method is non-blocking and returns immediately.
     */
    private void triggerRebuildIfNeeded() {
        if (dirty.get() && rebuildInProgress.compareAndSet(false, true)) {
            // We won the race, trigger async rebuild
            plugin.getScheduler().runAsync(w -> {
                try {
                    rebuildCache();
                } finally {
                    rebuildInProgress.set(false);
                }
            });
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
     * Non-blocking, returns current count (may be stale during rebuild).
     */
    public int getTotalCount(SortItem sortItem) {
        triggerRebuildIfNeeded();
        Map<SortItem, IntList> cache = sortedAllItems.get();
        IntList cached = cache.get(sortItem);
        return cached != null ? cached.size() : 0;
    }

    /**
     * Returns the total number of cached items for the given category and sort type.
     * Non-blocking, returns current count (may be stale during rebuild).
     */
    public int getTotalCount(Category category, SortItem sortItem) {
        if (category == null) {
            return getTotalCount(sortItem);
        }

        triggerRebuildIfNeeded();
        String cacheKey = buildCacheKey(category.getId(), sortItem);

        Map<String, IntList> cache = sortedByCategoryItems.get();
        IntList cached = cache.get(cacheKey);
        return cached != null ? cached.size() : 0;
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
            int totalItems = allItems.size();

            // OPTIMIZATION 1: Single pass to filter available items AND group by category
            // Pre-allocate with estimated size to avoid resizing
            List<Item> availableItems = new ArrayList<>(totalItems);
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

            int itemCount = availableItems.size();
            int categoryCount = itemsByCategory.size();

            // Build new maps (copy-on-write pattern)
            // These are built completely before being published to readers
            Map<SortItem, IntList> newSortedAllItems = new ConcurrentHashMap<>();
            Map<String, IntList> newSortedByCategoryItems = new ConcurrentHashMap<>();

            if (itemCount == 0) {
                // No items, publish empty maps and mark as clean
                sortedAllItems.set(newSortedAllItems);
                sortedByCategoryItems.set(newSortedByCategoryItems);
                dirty.set(false);
                lastRebuildTime = System.currentTimeMillis();
                performanceDebug.end("SortedItemsCache.rebuild", startTime, "items=0");
                return;
            }

            // OPTIMIZATION 2: Convert to array for faster sorting
            // Arrays.parallelSort is significantly faster for large datasets
            Item[] itemArray = availableItems.toArray(new Item[0]);

            // Sort by date and extract IDs
            if (itemCount >= parallelSortThreshold) {
                Arrays.parallelSort(itemArray, SortItem.ASCENDING_DATE.getComparator());
            } else {
                Arrays.sort(itemArray, SortItem.ASCENDING_DATE.getComparator());
            }
            int[] ascDateIds = extractIdsToArray(itemArray);
            int[] descDateIds = reverseArray(ascDateIds);

            newSortedAllItems.put(SortItem.ASCENDING_DATE, wrapArray(ascDateIds));
            newSortedAllItems.put(SortItem.DECREASING_DATE, wrapArray(descDateIds));

            // Sort by price and extract IDs
            if (itemCount >= parallelSortThreshold) {
                Arrays.parallelSort(itemArray, SortItem.ASCENDING_PRICE.getComparator());
            } else {
                Arrays.sort(itemArray, SortItem.ASCENDING_PRICE.getComparator());
            }
            int[] ascPriceIds = extractIdsToArray(itemArray);
            int[] descPriceIds = reverseArray(ascPriceIds);

            newSortedAllItems.put(SortItem.ASCENDING_PRICE, wrapArray(ascPriceIds));
            newSortedAllItems.put(SortItem.DECREASING_PRICE, wrapArray(descPriceIds));

            // OPTIMIZATION 3: Process categories in parallel using dedicated ForkJoinPool
            if (categoryCount > 2 && itemCount >= parallelCategoryThreshold) {
                try {
                    forkJoinPool.submit(() ->
                        itemsByCategory.entrySet().parallelStream().forEach(entry ->
                            buildCategorySortedLists(entry.getKey(), entry.getValue(), newSortedByCategoryItems)
                        )
                    ).get();
                } catch (Exception e) {
                    // Fallback to sequential if parallel fails
                    for (Map.Entry<String, List<Item>> entry : itemsByCategory.entrySet()) {
                        buildCategorySortedLists(entry.getKey(), entry.getValue(), newSortedByCategoryItems);
                    }
                }
            } else {
                // Sequential processing for small datasets (less overhead)
                for (Map.Entry<String, List<Item>> entry : itemsByCategory.entrySet()) {
                    buildCategorySortedLists(entry.getKey(), entry.getValue(), newSortedByCategoryItems);
                }
            }

            // Atomically publish the new cache (readers will see either old or new, never partial)
            sortedAllItems.set(newSortedAllItems);
            sortedByCategoryItems.set(newSortedByCategoryItems);

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
     * Optimized to use array-based sorting for better cache locality.
     *
     * @param categoryId the category ID
     * @param categoryItems the items in this category
     * @param targetMap the map to store results (thread-safe ConcurrentHashMap)
     */
    private void buildCategorySortedLists(String categoryId, List<Item> categoryItems, Map<String, IntList> targetMap) {
        int size = categoryItems.size();
        if (size == 0) return;

        // Convert to array for faster sorting
        Item[] itemArray = categoryItems.toArray(new Item[0]);

        // Sort by date ascending
        if (size >= parallelSortThreshold) {
            Arrays.parallelSort(itemArray, SortItem.ASCENDING_DATE.getComparator());
        } else {
            Arrays.sort(itemArray, SortItem.ASCENDING_DATE.getComparator());
        }
        int[] ascDateIds = extractIdsToArray(itemArray);
        int[] descDateIds = reverseArray(ascDateIds);

        targetMap.put(buildCacheKey(categoryId, SortItem.ASCENDING_DATE), wrapArray(ascDateIds));
        targetMap.put(buildCacheKey(categoryId, SortItem.DECREASING_DATE), wrapArray(descDateIds));

        // Sort by price ascending
        if (size >= parallelSortThreshold) {
            Arrays.parallelSort(itemArray, SortItem.ASCENDING_PRICE.getComparator());
        } else {
            Arrays.sort(itemArray, SortItem.ASCENDING_PRICE.getComparator());
        }
        int[] ascPriceIds = extractIdsToArray(itemArray);
        int[] descPriceIds = reverseArray(ascPriceIds);

        targetMap.put(buildCacheKey(categoryId, SortItem.ASCENDING_PRICE), wrapArray(ascPriceIds));
        targetMap.put(buildCacheKey(categoryId, SortItem.DECREASING_PRICE), wrapArray(descPriceIds));
    }

    /**
     * Extracts item IDs from an array of items into a primitive int array.
     * Using primitive arrays improves cache locality and reduces memory overhead.
     */
    private int[] extractIdsToArray(Item[] items) {
        int[] ids = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            ids[i] = items[i].getId();
        }
        return ids;
    }

    /**
     * Creates a reversed copy of a primitive int array.
     * This is O(n) but avoids sorting again.
     */
    private int[] reverseArray(int[] source) {
        int length = source.length;
        int[] reversed = new int[length];
        for (int i = 0; i < length; i++) {
            reversed[i] = source[length - 1 - i];
        }
        return reversed;
    }

    /**
     * Wraps a primitive int array into an IntList.
     */
    private IntList wrapArray(int[] array) {
        IntList list = new IntArrayList(array.length);
        for (int id : array) {
            list.add(id);
        }
        return list;
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

    /**
     * Shuts down the ForkJoinPool used for parallel processing.
     * Should be called when the plugin is disabled to prevent resource leaks.
     * Uses a 10-second timeout to allow ongoing cache rebuilds to complete.
     */
    public void shutdown() {
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                plugin.getLogger().warning("ForkJoinPool did not terminate within 10 seconds, forcing shutdown");
                forkJoinPool.shutdownNow();
                if (!forkJoinPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    plugin.getLogger().severe("ForkJoinPool did not terminate properly after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            plugin.getLogger().warning("ForkJoinPool shutdown interrupted, forcing shutdown");
            forkJoinPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
