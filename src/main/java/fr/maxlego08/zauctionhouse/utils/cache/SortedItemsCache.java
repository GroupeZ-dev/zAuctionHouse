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
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
 * <p>
 * <b>Rythme des reconstructions</b> : une reconstruction complete coute une passe de
 * filtrage/groupement sur tout le store, 2 tris globaux et 2 tris par categorie. Elle n'est
 * donc JAMAIS declenchee une fois par invalidation : les invalidations sont coalescees dans le
 * temps par {@link #MIN_REBUILD_INTERVAL_MS}, et l'etat sale est porte par un compteur de
 * generation et non par un drapeau booleen.
 */
public class SortedItemsCache {

    /**
     * Intervalle minimal entre deux reconstructions completes, mesure depuis la FIN de la
     * precedente.
     * <p>
     * Sans cette fenetre, chaque invalidation (une mise en vente, un achat, ou le message
     * correspondant recu du bus Redis) declenchait un retri integral : a 10 mises en vente par
     * seconde sur le cluster, les reconstructions s'enchainaient dos a dos en permanence sur
     * chaque noeud. Avec la fenetre, le nombre de tris complets est borne par unite de temps
     * (au plus un toutes les 250 ms) au lieu d'etre proportionnel au trafic.
     * <p>
     * 250 ms est un compromis assume : c'est 5 ticks, sous le seuil de perception d'un joueur
     * qui ouvre l'hotel des ventes, et cela laisse le processeur respirer entre deux tris meme
     * quand la reconstruction elle-meme dure plusieurs dizaines de millisecondes. Constante
     * volontairement non configurable : une valeur trop basse ramene la tempete de
     * reconstruction que ce cache existe precisement pour eviter.
     */
    private static final long MIN_REBUILD_INTERVAL_MS = 250L;

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

    // Compteur d'invalidation, incremente a CHAQUE invalidate(). Il remplace l'ancien drapeau
    // booleen `dirty` : un booleen ne dit pas SI la mutation qui l'a leve est deja reflechie
    // dans l'instantane publie, il ne pouvait donc etre remis a false que de facon aveugle
    // (mutation concurrente perdue, C-101) ou jamais (drapeau collant : chaque lecture
    // relancait un tri complet, C-056).
    private final AtomicLong invalidationCounter = new AtomicLong();

    // Valeur du compteur que reflete l'instantane actuellement publie. Le cache est sale tant
    // que builtGeneration < invalidationCounter. Initialise a -1 : rien n'a encore ete
    // construit, le cache est donc sale des la construction de l'objet.
    private final AtomicLong builtGeneration = new AtomicLong(-1L);

    // Reference to ongoing async rebuild (null if none in progress).
    // Porte aussi bien une reconstruction en cours d'execution qu'une reconstruction DEJA
    // PLANIFIEE pour la fin de la fenetre de coalescence : toutes les demandes concurrentes se
    // partagent ce meme future, donc un seul tri les sert toutes.
    private final AtomicReference<CompletableFuture<Void>> ongoingRebuild = new AtomicReference<>(null);

    // Timestamp of the last completed rebuild, also used as the start of the coalescing window
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
     * Triggers an async cache rebuild if the cache is dirty. Non-blocking.
     * <p>
     * Chemin de RENDU : appele par chaque {@code getSortedIds} et chaque {@code getTotalCount},
     * donc par {@code %listed_items%} et par les neuf {@code %category_count_*%} de chaque
     * inventaire ouvert. Il doit rester a cout constant.
     * <p>
     * Pendant la fenetre de coalescence, la demande ne declenche AUCUN tri : le cache reste
     * simplement sale (le compteur d'invalidation porte deja cette information) et la premiere
     * lecture posterieure a la fenetre relancera la reconstruction. C'est ce qui rend le nombre
     * de tris borne par unite de temps au lieu de proportionnel au trafic (C-056).
     * <p>
     * Il n'existe qu'UN SEUL point d'entree de reconstruction, {@link #requestRebuild()} : deux
     * gardes independantes laissaient auparavant demarrer DEUX reconstructions concurrentes,
     * chacune prenant le writeLock et re-triant integralement le store.
     */
    private void triggerRebuildIfNeeded() {
        if (!isDirty()) return;
        if (remainingCooldownMillis() > 0L) return;
        requestRebuild();
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
     * <p>
     * Operation O(1) et non bloquante : elle ne fait qu'avancer le compteur de generation.
     * Aucune reconstruction n'est declenchee ici, c'est la prochaine demande (une lecture, ou
     * {@link #ensureCacheValidAsync()}) qui en planifiera une, au plus une par fenetre de
     * coalescence.
     */
    public void invalidate() {
        this.invalidationCounter.incrementAndGet();
    }

    /**
     * Forces an immediate synchronous cache rebuild.
     * Use sparingly as this blocks the calling thread.
     * <p>
     * Marque d'abord le cache sale : sans cela, une mutation qui n'est pas passee par
     * {@link #invalidate()} (un changement de statut applique en place) verrait un cache
     * considere comme propre et la reconstruction serait ignoree.
     */
    public void rebuild() {
        this.invalidationCounter.incrementAndGet();
        rebuildCache();
    }

    /**
     * Schedules an asynchronous cache rebuild.
     * <p>
     * Appelee apres des mutations en masse qui ne passent PAS par {@link #invalidate()} :
     * chargement initial depuis la base, et reprise des confirmations/expirations par le
     * {@code ZMaintenanceScheduler}, qui repositionne un statut en place. Le compteur est donc
     * avance ici aussi, faute de quoi la reconstruction serait ignoree comme inutile.
     * <p>
     * Passe par le point d'entree unique : la demande est coalescee avec les autres et respecte
     * la fenetre minimale entre deux reconstructions.
     */
    public void rebuildAsync() {
        this.invalidationCounter.incrementAndGet();
        requestRebuild();
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
     * <p>
     * Derive du compteur de generation : le cache est sale tant que l'instantane publie ne
     * reflete pas la derniere invalidation connue.
     */
    public boolean isDirty() {
        return this.builtGeneration.get() < this.invalidationCounter.get();
    }

    /**
     * Ensures the cache is valid asynchronously.
     * Returns a CompletableFuture that completes when the cache is ready.
     * If the cache is already valid, returns an already-completed future.
     * If a rebuild is already in progress or already scheduled, returns the existing future.
     * <p>
     * Chemin sensible a la fraicheur (ouverture de l'hotel des ventes,
     * {@code updateListedItems}) : contrairement a {@link #triggerRebuildIfNeeded()}, la
     * demande n'est pas abandonnee pendant la fenetre de coalescence, elle y est PLANIFIEE.
     * Toutes les demandes concurrentes se partagent le meme future, donc un seul tri les sert
     * toutes et la fenetre borne bien le nombre de reconstructions.
     * <p>
     * Le future rendu garantit qu'une reconstruction a eu lieu, pas que l'instantane publie
     * couvre l'invalidation de l'appelant : si une reconstruction etait deja engagee, elle peut
     * avoir lu le store avant cette invalidation. Dans ce cas le cache reste sale (le compteur
     * a bouge) et la lecture suivante replanifie une reconstruction. On ne relance
     * volontairement PAS un second tri ici : sous charge, une invalidation arrive pendant
     * chaque reconstruction, la relance systematique doublait donc le cout de chaque cycle
     * (C-056 / C-101).
     *
     * @return CompletableFuture that completes when cache is valid
     */
    public CompletableFuture<Void> ensureCacheValidAsync() {
        if (!isDirty()) {
            return CompletableFuture.completedFuture(null);
        }
        return requestRebuild();
    }

    /**
     * Temps restant avant que la prochaine reconstruction soit autorisee.
     *
     * @return le nombre de millisecondes restantes, 0 si une reconstruction peut demarrer
     */
    private long remainingCooldownMillis() {
        long last = this.lastRebuildTime;
        if (last == 0L) return 0L; // aucune reconstruction effectuee jusqu'ici

        long elapsed = System.currentTimeMillis() - last;
        if (elapsed < 0L) return 0L; // horloge systeme revenue en arriere

        long remaining = MIN_REBUILD_INTERVAL_MS - elapsed;
        return remaining > 0L ? remaining : 0L;
    }

    /**
     * Point d'entree UNIQUE des reconstructions asynchrones.
     * <p>
     * Coalesce : si une reconstruction est en cours ou deja planifiee, son future est rendu tel
     * quel. Sinon une seule est planifiee, immediatement si la fenetre de coalescence est
     * ecoulee, sinon a la fin de cette fenetre.
     *
     * @return le future de la reconstruction qui servira cette demande
     */
    private CompletableFuture<Void> requestRebuild() {
        CompletableFuture<Void> existing = this.ongoingRebuild.get();
        if (existing != null && !existing.isDone()) return existing;

        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!this.ongoingRebuild.compareAndSet(existing, future)) {
            // Un autre thread vient de planifier la reconstruction : on se greffe dessus.
            CompletableFuture<Void> other = this.ongoingRebuild.get();
            return other != null ? other : CompletableFuture.completedFuture(null);
        }

        long delay = remainingCooldownMillis();
        try {
            if (delay <= 0L) {
                this.plugin.getScheduler().runAsync(wrappedTask -> runRebuildTask(future));
            } else {
                this.plugin.getScheduler().runLaterAsync(() -> runRebuildTask(future), delay, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable throwable) {
            // Ordonnanceur indisponible (arret du serveur) : liberer la place et completer le
            // future. Le laisser pendant figerait definitivement les .thenRun de
            // ZAuctionManager : inventaire jamais ouvert, inventaires jamais rafraichis.
            this.ongoingRebuild.compareAndSet(future, null);
            this.plugin.getLogger().warning("[ZAH] Unable to schedule the sorted items cache rebuild: " + throwable.getMessage());
            future.complete(null);
        }
        return future;
    }

    /**
     * Corps de la tache asynchrone de reconstruction.
     * <p>
     * UNE SEULE reconstruction par tache. Une invalidation survenue pendant celle-ci laisse le
     * cache sale sans relance immediate : la demande suivante, apres la fenetre de coalescence,
     * s'en chargera.
     *
     * @param future le future a completer pour tous les demandeurs coalesces
     */
    private void runRebuildTask(CompletableFuture<Void> future) {
        try {
            rebuildCache();
        } catch (Exception exception) {
            this.ongoingRebuild.compareAndSet(future, null);
            future.completeExceptionally(exception);
            return;
        }
        // Liberer la place AVANT de completer : une continuation qui redemande immediatement une
        // reconstruction doit pouvoir en planifier une nouvelle.
        this.ongoingRebuild.compareAndSet(future, null);
        future.complete(null);
    }

    private void rebuildCache() {
        long startTime = performanceDebug.start();

        lock.writeLock().lock();
        try {
            // Capture AVANT toute lecture du store : une invalidation posterieure a cette ligne
            // n'est pas garantie visible dans les listes construites ci-dessous.
            long startedAtGeneration = this.invalidationCounter.get();

            // Double-check after acquiring lock : un autre thread a deja publie un instantane au
            // moins aussi recent.
            if (this.builtGeneration.get() >= startedAtGeneration) {
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
                markBuilt(startedAtGeneration);
                lastRebuildTime = System.currentTimeMillis();
                performanceDebug.end("SortedItemsCache.rebuild", startTime, "items=0");
                return;
            }

            // OPTIMIZATION 2: Convert to array for faster sorting
            // Arrays.parallelSort is significantly faster for large datasets
            Item[] itemArray = availableItems.toArray(new Item[0]);

            // Sort by date and extract IDs
            sortInPool(itemArray, SortItem.ASCENDING_DATE.getComparator(), itemCount);
            int[] ascDateIds = extractIdsToArray(itemArray);
            int[] descDateIds = reverseArray(ascDateIds);

            newSortedAllItems.put(SortItem.ASCENDING_DATE, wrapArray(ascDateIds));
            newSortedAllItems.put(SortItem.DECREASING_DATE, wrapArray(descDateIds));

            // Sort by price and extract IDs
            sortInPool(itemArray, SortItem.ASCENDING_PRICE.getComparator(), itemCount);
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

            markBuilt(startedAtGeneration);
            lastRebuildTime = System.currentTimeMillis();

            performanceDebug.end("SortedItemsCache.rebuild", startTime,
                    "items=" + itemCount + ", categories=" + categoryCount);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enregistre la generation que reflete l'instantane qui vient d'etre publie.
     * <p>
     * {@code max} : une reconstruction plus ancienne ne peut pas faire reculer la generation
     * publiee. Si le compteur d'invalidation a bouge pendant la reconstruction, il reste
     * strictement superieur a cette valeur : le cache demeure sale, la mutation concurrente
     * n'est donc pas perdue (C-101), et aucune reconstruction n'est relancee ici.
     *
     * @param generationAtSnapshot la valeur du compteur capturee avant la lecture du store
     */
    private void markBuilt(long generationAtSnapshot) {
        this.builtGeneration.accumulateAndGet(generationAtSnapshot, Math::max);
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
        sortInPool(itemArray, SortItem.ASCENDING_DATE.getComparator(), size);
        int[] ascDateIds = extractIdsToArray(itemArray);
        int[] descDateIds = reverseArray(ascDateIds);

        targetMap.put(buildCacheKey(categoryId, SortItem.ASCENDING_DATE), wrapArray(ascDateIds));
        targetMap.put(buildCacheKey(categoryId, SortItem.DECREASING_DATE), wrapArray(descDateIds));

        // Sort by price ascending
        sortInPool(itemArray, SortItem.ASCENDING_PRICE.getComparator(), size);
        int[] ascPriceIds = extractIdsToArray(itemArray);
        int[] descPriceIds = reverseArray(ascPriceIds);

        targetMap.put(buildCacheKey(categoryId, SortItem.ASCENDING_PRICE), wrapArray(ascPriceIds));
        targetMap.put(buildCacheKey(categoryId, SortItem.DECREASING_PRICE), wrapArray(descPriceIds));
    }

    /**
     * Trie sur le ForkJoinPool PRIVE du cache. {@link Arrays#parallelSort} utilise sinon le
     * ForkJoinPool COMMUN de la JVM, deja sature par les appels Jedis bloquants du bridge
     * (C-056).
     *
     * @param itemArray  le tableau a trier, modifie sur place
     * @param comparator l'ordre de tri
     * @param itemCount  la taille logique, comparee au seuil de parallelisme
     */
    private void sortInPool(Item[] itemArray, Comparator<Item> comparator, int itemCount) {
        if (itemCount < parallelSortThreshold) {
            Arrays.sort(itemArray, comparator);
            return;
        }
        // Deja dans un worker du pool prive (buildCategorySortedLists appele depuis le
        // parallelStream de rebuildCache) : Arrays.parallelSort y forke sur le pool COURANT,
        // un submit imbrique suivi d'un get() bloquant n'apporterait rien et forcerait le
        // pool a compenser en creant un thread supplementaire.
        if (ForkJoinTask.getPool() == this.forkJoinPool) {
            Arrays.parallelSort(itemArray, comparator);
            return;
        }
        try {
            forkJoinPool.submit(() -> Arrays.parallelSort(itemArray, comparator)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Arrays.sort(itemArray, comparator);
        } catch (Exception exception) {
            Arrays.sort(itemArray, comparator);
        }
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
            if (!forkJoinPool.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("ForkJoinPool did not terminate within 10 seconds, forcing shutdown");
                forkJoinPool.shutdownNow();
                if (!forkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
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
