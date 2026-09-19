package fr.maxlego08.zauctionhouse;

import com.tcoded.folialib.enums.EntityTaskResult;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.CompatibilityUtil;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
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
import fr.maxlego08.zauctionhouse.api.services.AuctionOptionService;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveResult;
import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import fr.maxlego08.zauctionhouse.discord.DiscordWebhookService;
import fr.maxlego08.zauctionhouse.services.*;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.tax.ZPurchaseCharge;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ZAuctionManager extends ZUtils implements AuctionManager {

    /**
     * Filet temporel de la remise physique. Sur Folia, si l'entite est retiree APRES la
     * planification, FoliaLib 0.5.1 ne complete jamais le future de la tache : sans ce filet
     * la chaine de retrait resterait en vol pour toujours. 15 s est tres au-dessus du delai
     * reel (1 tick) et ne se declenche donc jamais pour un joueur en ligne.
     */
    private static final long GIVE_ITEM_TIMEOUT_SECONDS = 15L;

    private final AuctionPlugin plugin;
    private final AuctionPurchaseService auctionPurchaseService;
    private final AuctionSellService auctionSellService;
    private final RemoveService auctionRemoveService;
    private final AuctionExpireService auctionExpireService;
    private final AuctionClaimService auctionClaimService;
    private final AuctionHistoryService auctionHistoryService;
    private final AuctionOptionService auctionOptionService;
    private final PerformanceDebug performanceDebug;
    private final SearchService searchService;

    // Indexation par UUID et non par Player : la cle Player retenait une reference forte vers
    // le CraftPlayer, son inventaire et son monde pour toute deconnexion dont removeCache
    // n'etait pas atteint (C-107). Les signatures publiees de l'API (getCache/clearPlayerCache/
    // removeCache prennent un Player) sont INCHANGEES : le changement est purement interne.
    private final Map<UUID, PlayerCache> caches = new ConcurrentHashMap<>();
    private final Map<StorageType, Map<Integer, Item>> storageItemsById = new EnumMap<>(StorageType.class);
    private final Map<UUID, IntList> idsListedByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, IntList> idsExpiredByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, IntList> idsPurchasedByBuyer = new ConcurrentHashMap<>();
    private SortedItemsCache sortedItemsCache;

    public ZAuctionManager(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.auctionPurchaseService = new PurchaseService(plugin);
        this.auctionSellService = new SellService(plugin, this);
        this.auctionRemoveService = new RemoveService(plugin, this);
        this.auctionExpireService = new ExpireService(plugin, this);
        this.auctionClaimService = new ClaimService(plugin);
        this.auctionHistoryService = new HistoryService(plugin);
        this.auctionOptionService = new OptionService(plugin);
        this.performanceDebug = new PerformanceDebug(plugin);
        this.searchService = new SearchService(plugin);

        for (StorageType value : StorageType.values()) {
            this.storageItemsById.put(value, new ConcurrentHashMap<>());
        }

    }

    @Override
    public void setupSortedItemsCache() {
        // Initialize sorted items cache for LISTED items
        this.sortedItemsCache = new SortedItemsCache(plugin, () -> this.storageItemsById.get(StorageType.LISTED).values());
    }

    @Override
    public void openMainAuction(Player player) {
        this.openMainAuction(player, 1);
    }

    @Override
    public void openMainAuction(Player player, int page) {
        var cache = getCache(player);

        // Reset category filter if configured
        if (this.plugin.getConfiguration().getActions().resetCategoryOnOpen() && cache.has(PlayerCacheKey.CURRENT_CATEGORY)) {
            cache.remove(PlayerCacheKey.CURRENT_CATEGORY);
            cache.remove(PlayerCacheKey.ITEMS_LISTED);
        }

        // Reset search filter if configured
        if (this.plugin.getConfiguration().getActions().resetSearchOnOpen() && cache.has(PlayerCacheKey.SEARCH_QUERY)) {
            cache.remove(PlayerCacheKey.SEARCH_QUERY);
            cache.remove(PlayerCacheKey.ITEMS_SEARCH);
            cache.remove(PlayerCacheKey.ITEMS_LISTED);
        }

        openAuctionInventory(player, page);
    }

    private void openAuctionInventory(Player player, int page) {
        var inventoriesLoader = this.plugin.getInventoriesLoader();
        var cache = getCache(player);

        // Check if player's cache is already ready (fast path)
        boolean playerCacheReady = cache.has(PlayerCacheKey.ITEMS_LISTED);
        boolean globalCacheReady = !sortedItemsCache.isDirty();

        if (playerCacheReady && globalCacheReady) {
            inventoriesLoader.openInventory(player, Inventories.AUCTION, page);
        } else {
            // Cache needs preparation - do it async then open on the entity thread
            prepareCacheAsync(player).thenRun(() -> {
                this.plugin.getScheduler().runAtEntity(player, w -> {
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
        this.plugin.getScheduler().runAtEntity(player, w -> this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player));
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
    public AuctionOptionService getOptionService() {
        return this.auctionOptionService;
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

    /**
     * Acces O(1) a l'instance detenue par un conteneur, sans copie du store.
     * <p>
     * L'implementation par defaut de {@link AuctionManager#getItem(StorageType, int)} copie
     * l'integralite du bucket puis le parcourt lineairement : inacceptable a 50 000 annonces.
     *
     * @param storageType conteneur a interroger
     * @param itemId      identifiant de l'item
     * @return l'instance partagee detenue par le conteneur, ou {@code null}
     */
    @Override
    public Item getItem(StorageType storageType, int itemId) {
        var storage = this.storageItemsById.get(storageType);
        return storage == null ? null : storage.get(itemId);
    }

    /**
     * Nombre d'annonces reellement affichables, servi par le cache trie plutot que par une copie
     * complete du store suivie d'un stream (implementation par defaut de l'interface).
     * <p>
     * Le compteur reflete donc exactement ce que le GUI affiche - memes donnees, meme fraicheur -
     * et non un recalcul independant qui pourrait le contredire.
     *
     * @return le nombre d'annonces actuellement visibles dans l'hotel des ventes
     */
    @Override
    public int getListedItemCount() {
        var cache = this.sortedItemsCache;
        if (cache == null) return AuctionManager.super.getListedItemCount();
        return cache.getTotalCount(this.plugin.getConfiguration().getSort().defaultSort());
    }

    /**
     * Meme compteur, restreint a une categorie.
     *
     * @param category categorie a compter, {@code null} valant « toutes categories »
     * @return le nombre d'annonces visibles dans cette categorie
     */
    @Override
    public int getListedItemCount(Category category) {
        var cache = this.sortedItemsCache;
        if (cache == null) return AuctionManager.super.getListedItemCount(category);
        if (category == null) return getListedItemCount();
        return cache.getTotalCount(category, this.plugin.getConfiguration().getSort().defaultSort());
    }

    @Override
    public void addItem(StorageType storageType, Item item) {
        var storage = this.storageItemsById.get(storageType);
        storage.put(item.getId(), item);
        this.indexItem(storageType, item);

        invalidateListedCaches(storageType);
    }

    @Override
    public void removeItem(StorageType storageType, Item item) {
        // ATTENTION : ne PAS deleguer a removeItem(StorageType, int). Cette surcharge est
        // utilisee par les transitions internes (removeListedItem LISTED -> EXPIRED,
        // performListedToExpired, purchaseAuctionItem LISTED -> PURCHASED) qui RE-AJOUTENT
        // ensuite la meme instance dans un autre conteneur : lui imposer le statut DELETED la
        // rendrait definitivement inreclamable.
        var storage = this.storageItemsById.get(storageType);
        if (storage == null) return;

        Item removed = storage.remove(item.getId());
        if (removed == null) return;

        this.deindexItem(storageType, removed);
        invalidateListedCaches(storageType);
    }

    @Override
    public void removeItem(StorageType storageType, int itemId) {
        var storage = this.storageItemsById.get(storageType);
        if (storage == null) return;

        Item removed = storage.remove(itemId);
        if (removed == null) return;

        // C-046 : sortie TERMINALE par identifiant (purge d'un fantome par ExpireService, ou
        // convergence demandee par ItemBoughtListener / ItemRemovedListener de l'addon Redis).
        // L'instance qu'on sort du store reste referencee par les setClick des boutons deja
        // rendus et par le cache ITEM_SHOW : la marquer terminale la rend inoffensive, elle ne
        // franchit plus les gardes de statut de RemoveService et ConfirmHelper cesse de
        // rediffuser AVAILABLE derriere.
        removed.setStatus(ItemStatus.DELETED);

        this.deindexItem(storageType, removed);
        invalidateListedCaches(storageType);
    }

    private void invalidateListedCaches(StorageType storageType) {
        if (storageType != StorageType.LISTED) return;
        this.plugin.getCategoryManager().invalidateCategoryCountCache();
        this.sortedItemsCache.invalidate();
    }

    /**
     * Relecture autoritaire d'une ligne {@code %prefix%items}, destinee aux revalidations
     * SOUS VERROU (retrait joueur et retrait admin).
     * <p>
     * Une seule requete sur la cle primaire, contrairement a {@code StorageManager.selectItem(int)}
     * qui en fait trois et rend {@code null} quand l'economie de l'item a disparu de
     * economies.yml. Postee explicitement sur l'executor de la base et non sur le ForkJoinPool
     * commun, deja sature par les appels Jedis bloquants du bridge.
     * <p>
     * {@code ItemRepository.select(int)} filtre deja {@code storage_type = DELETED} : un
     * {@link Optional} vide signifie donc « ligne detruite ».
     *
     * @param itemId identifiant de l'item
     * @return la ligne base, ou un Optional vide si elle est detruite ou absente
     */
    public CompletableFuture<Optional<ItemDTO>> selectItemRow(int itemId) {
        var storageManager = this.plugin.getStorageManager();
        return CompletableFuture.supplyAsync(() -> storageManager.with(ItemRepository.class).select(itemId), this.plugin.getExecutorService());
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

        // If a search is active, use search results
        String searchQuery = cache.get(PlayerCacheKey.SEARCH_QUERY);
        if (searchQuery != null && !searchQuery.isBlank()) {
            IntList ids = cache.getOrCompute(PlayerCacheKey.ITEMS_SEARCH, () -> searchService.search(sortedItemsCache, searchQuery, sort, category));
            performanceDebug.end("getItemIdsListedForSale[search]", startTime, "query=" + searchQuery + ", sort=" + sort + ", ids=" + ids.size());
            return ids;
        }

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
        IntList ids = getCache(player).getOrCompute(PlayerCacheKey.ITEMS_SELLING, () -> getItemIds(StorageType.LISTED, item -> item.getSellerUniqueId().equals(player.getUniqueId()) && item.getStatus() != ItemStatus.DELETED, Comparator.comparing(Item::getExpiredAt)));
        return resolveItems(StorageType.LISTED, ids);
    }

    @Override
    public List<Item> getPlayerSellingItems(UUID uniqueId) {
        return resolveItems(StorageType.LISTED, getItemIds(StorageType.LISTED, item -> item.getSellerUniqueId().equals(uniqueId) && item.getStatus() != ItemStatus.DELETED, Comparator.comparing(Item::getExpiredAt)));
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
        return this.caches.computeIfAbsent(player.getUniqueId(), uuid -> new ZPlayerCache());
    }

    /**
     * Accesseur NON creant, a utiliser sur tous les chemins de completion tardive (fin de
     * retrait en masse, callbacks d'achat/expiration, fermeture d'inventaire) ou
     * {@link #getCache(Player)} ressusciterait le cache d'un joueur deja deconnecte.
     * Volontairement prive : le remonter dans l'interface publiee AuctionManager rendrait ce
     * correctif de fuite api-breaking pour un gain nul.
     */
    private PlayerCache peekCache(Player player) {
        return this.caches.get(player.getUniqueId());
    }

    @Override
    public void clearPlayersCache(PlayerCacheKey... keys) {
        this.caches.forEach((uniqueId, cache) -> cache.remove(keys));
    }

    @Override
    public void clearPlayerCache(Player player, PlayerCacheKey... keys) {
        // No-op quand aucune entree n'existe : finishBulkRemoval nous appelle AVANT son test
        // isOnline(), ce qui ressuscitait le cache d'un joueur deconnecte a chaque
        // « tout recuperer ». Verifie : aucun appelant actuel ne comptait sur l'effet de bord
        // « creer le cache ».
        var cache = peekCache(player);
        if (cache != null) cache.remove(keys);
    }

    @Override
    public void removeCache(Player player) {
        this.caches.remove(player.getUniqueId());
    }

    /**
     * Decide UNE SEULE FOIS, sur le thread du joueur, ou doit aller une annonce retiree de la
     * vente.
     * <p>
     * Cette decision etait faite DEUX fois (RemoveService pour ce qui est diffuse au cluster,
     * ZAuctionManager pour ce qui est ecrit en base) avec, entre les deux, plusieurs sauts
     * asynchrones : un inventaire qui se remplit entre-temps suffisait a les faire diverger.
     * {@code canReceiveItem} lit {@code player.getInventory().firstEmpty()}, cet appel DOIT
     * rester sur le thread du joueur.
     *
     * @param player joueur qui retire son annonce
     * @param item   annonce retiree
     * @return {@link StorageType#DELETED} pour une remise immediate, {@link StorageType#EXPIRED}
     * pour un basculement dans les items expires
     */
    public StorageType resolveListedDestination(Player player, Item item) {
        var listedConfig = this.plugin.getConfiguration().getActions().listed();
        return (listedConfig.giveItem() && item.canReceiveItem(player)) ? StorageType.DELETED : StorageType.EXPIRED;
    }

    @Override
    public CompletableFuture<Void> removeListedItem(Player player, Item item) {
        return removeListedItem(player, item, resolveListedDestination(player, item)).thenApply(delivered -> null);
    }

    /**
     * Variante de {@link #removeListedItem(Player, Item, StorageType)} exprimee par le booleen
     * historique {@code giveItem}, pour les appelants qui decident encore par ce predicat
     * plutot que par une destination.
     *
     * @param player   joueur qui retire son annonce
     * @param item     annonce retiree
     * @param giveItem {@code true} pour rendre l'item au joueur (destination DELETED),
     *                 {@code false} pour le basculer dans les items expires
     * @return {@code true} si le lot a effectivement ete remis au joueur
     */
    public CompletableFuture<Boolean> removeListedItem(Player player, Item item, boolean giveItem) {
        return removeListedItem(player, item, giveItem ? StorageType.DELETED : StorageType.EXPIRED);
    }

    /**
     * ORDRE : ecriture en base D'ABORD, mutation memoire et remise physique ENSUITE.
     * <p>
     * Tant que l'UPDATE n'est pas confirme, RIEN n'est mute localement : en cas d'echec la
     * memoire et la base restent d'accord (la ligne est toujours LISTED) et RemoveService
     * restaure proprement le statut. Il n'y a donc aucun rollback memoire a ecrire.
     * <p>
     * L'ecriture est un compare-and-set {@code LISTED -> destination} : si un autre serveur a
     * deja pris la ligne, le future echoue avec une {@code StaleItemException} et RIEN n'est
     * mute ni remis.
     *
     * @param player      joueur qui retire son annonce
     * @param item        annonce retiree
     * @param destination decidee par l'appelant via {@link #resolveListedDestination}
     * @return {@code true} si le lot a effectivement ete remis au joueur
     */
    public CompletableFuture<Boolean> removeListedItem(Player player, Item item, StorageType destination) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        if (destination == StorageType.EXPIRED) {
            var expiration = configuration.getExpireExpiration().getExpiration(player);
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            item.setExpiredAt(new Date(expiredAt));
        }

        return storageManager.updateItem(item, StorageType.LISTED, destination).thenCompose(v -> {

            item.setStatus(destination == StorageType.EXPIRED ? ItemStatus.REMOVED : ItemStatus.DELETED);
            removeItem(StorageType.LISTED, item);
            if (destination == StorageType.EXPIRED) addItem(StorageType.EXPIRED, item);

            this.updateListedItems(item, false, player);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);

            message(this.plugin, player, Message.ITEM_REMOVE_LISTED, "%items%", item.getItemDisplay());

            if (configuration.getActions().listed().openInventory()) {
                openMainAuction(player, getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1));
            } else {
                this.plugin.getScheduler().runAtEntity(player, w -> {
                    if (player.isOnline()) player.closeInventory();
                });
            }

            callEvent(new AuctionRemoveListedItemEvent(item, player));
            logItemAction(LogType.REMOVE_LISTED, item, player, null, "removed_from_listed");

            if (destination != StorageType.DELETED) return CompletableFuture.completedFuture(Boolean.FALSE);
            return deliverOrRestore(player, item, StorageType.EXPIRED);
        });
    }

    @Override
    public CompletableFuture<Void> removeSellingItem(Player player, Item item) {
        return removeSellingItem(player, item, true).thenApply(delivered -> null);
    }

    /**
     * @param player       vendeur
     * @param item         annonce retiree
     * @param updatePlayer {@code false} pour un retrait en masse (aucun message unitaire)
     * @return {@code true} si le lot a effectivement ete remis au joueur
     */
    public CompletableFuture<Boolean> removeSellingItem(Player player, Item item, boolean updatePlayer) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        return storageManager.updateItem(item, StorageType.LISTED, StorageType.DELETED).thenCompose(v -> {

            item.setStatus(ItemStatus.DELETED);
            removeItem(StorageType.LISTED, item);

            this.updateListedItems(item, false, player);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);

            if (updatePlayer) {
                message(this.plugin, player, Message.ITEM_REMOVE_SELLING, "%items%", item.getItemDisplay());

                if (configuration.getActions().listed().openInventory()) {
                    this.updateInventory(player);
                } else {
                    this.plugin.getScheduler().runAtEntity(player, w -> {
                        if (player.isOnline()) player.closeInventory();
                    });
                }
            }

            callEvent(new AuctionRemoveListedItemEvent(item, player));
            logItemAction(LogType.REMOVE_SELLING, item, player, null, updatePlayer ? "removed_selling_item" : "removed_selling_item_bulk");

            return deliverOrRestore(player, item, StorageType.EXPIRED);
        });
    }

    @Override
    public CompletableFuture<Void> removeExpiredItem(Player player, Item item) {
        return removeExpiredItem(player, item, true).thenApply(delivered -> null);
    }

    /**
     * @param player       proprietaire de l'annonce expiree
     * @param item         annonce reclamee
     * @param updatePlayer {@code false} pour un retrait en masse (aucun message unitaire)
     * @return {@code true} si le lot a effectivement ete remis au joueur
     */
    public CompletableFuture<Boolean> removeExpiredItem(Player player, Item item, boolean updatePlayer) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        return storageManager.updateItem(item, StorageType.EXPIRED, StorageType.DELETED).thenCompose(v -> {

            item.setStatus(ItemStatus.DELETED);
            removeItem(StorageType.EXPIRED, item);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_EXPIRED);

            if (updatePlayer) {
                message(this.plugin, player, Message.ITEM_REMOVE_EXPIRED, "%items%", item.getItemDisplay());

                if (configuration.getActions().expired().openInventory()) {
                    this.updateInventory(player);
                } else {
                    this.plugin.getScheduler().runAtEntity(player, w -> {
                        if (player.isOnline()) player.closeInventory();
                    });
                }
            }

            callEvent(new AuctionRemoveExpiredItemEvent(item, player));
            logItemAction(LogType.REMOVE_EXPIRED, item, player, null, updatePlayer ? "removed_expired_item" : "removed_expired_item_bulk");

            // Compensation vers le conteneur d'ORIGINE : l'item redevient reclamable la ou il etait.
            return deliverOrRestore(player, item, StorageType.EXPIRED);
        });
    }

    @Override
    public CompletableFuture<Void> removePurchasedItem(Player player, Item item) {
        return removePurchasedItem(player, item, true).thenApply(delivered -> null);
    }

    /**
     * @param player       acheteur
     * @param item         annonce achetee et reclamee
     * @param updatePlayer {@code false} pour un retrait en masse (aucun message unitaire)
     * @return {@code true} si le lot a effectivement ete remis au joueur
     */
    public CompletableFuture<Boolean> removePurchasedItem(Player player, Item item, boolean updatePlayer) {

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        return storageManager.updateItem(item, StorageType.PURCHASED, StorageType.DELETED).thenCompose(v -> {

            item.setStatus(ItemStatus.DELETED);
            removeItem(StorageType.PURCHASED, item);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);

            if (updatePlayer) {
                message(this.plugin, player, Message.ITEM_REMOVE_PURCHASED, "%items%", item.getItemDisplay());

                if (configuration.getActions().purchased().openInventory()) {
                    this.updateInventory(player);
                } else {
                    this.plugin.getScheduler().runAtEntity(player, w -> {
                        if (player.isOnline()) player.closeInventory();
                    });
                }
            }

            callEvent(new AuctionRemovePurchasedItemEvent(item, player));
            logItemAction(LogType.REMOVE_PURCHASED, item, player, item.getSellerUniqueId(), updatePlayer ? "removed_purchased_item" : "removed_purchased_item_bulk");

            return deliverOrRestore(player, item, StorageType.PURCHASED);
        });
    }

    @Override
    public void adminRemoveItem(Player admin, UUID targetUniqueId, Item item, StorageType storageType) {

        var clusterBridge = this.plugin.getAuctionClusterBridge();
        if (clusterBridge == null) {
            this.plugin.getLogger().severe("Cluster bridge is not initialized");
            return;
        }

        var inventoriesLoader = this.plugin.getInventoriesLoader();
        if (inventoriesLoader == null) {
            this.plugin.getLogger().severe("Inventories loader is not initialized");
            return;
        }

        var inventoryManager = inventoriesLoader.getInventoryManager();
        if (inventoryManager == null) {
            this.plugin.getLogger().severe("Inventory manager is not initialized");
            return;
        }

        // C-046 : garde d'identite. Le bouton admin capture la reference du store au rendu ; si
        // un autre serveur l'a remplacee entre-temps, la supprimer ecraserait la ligne d'un etat
        // qui ne nous appartient plus.
        if (getItem(storageType, item.getId()) != item) {
            this.plugin.getLogger().info("Stale item reference for item " + item.getId() + " in " + storageType + ", admin removal refused");
            message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
            inventoryManager.updateInventory(admin);
            return;
        }

        var storageManager = this.plugin.getStorageManager();
        var previousStatus = item.getStatus();
        var tokenHolder = new AtomicReference<LockToken>();
        var statusChanged = new AtomicBoolean(false);

        clusterBridge.checkAvailability(item, storageType).thenCompose(available -> {

            if (!Boolean.TRUE.equals(available)) {
                this.plugin.getLogger().info("Item " + item.getId() + " is not available on the cluster");
                message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
                return this.<LockToken>failedFuture(new IllegalStateException("Item indisponible"));
            }

            return clusterBridge.lockItem(item, admin.getUniqueId(), storageType);

        }).thenCompose(lockToken -> {

            tokenHolder.set(lockToken);

            // C-042 : convention commune aux deux bridges, un echec d'acquisition se signale par
            // un jeton noop (ou unavailable), jamais par un future en erreur. Sans cette garde,
            // deux retraits admin concurrents - ou un simple double-clic - executent TOUS LES
            // DEUX la remise : duplication franche.
            if (lockToken == null || !lockToken.isAcquired()) {
                this.plugin.getLogger().info("Item " + item.getId() + " is already locked, admin removal aborted");
                message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
                return this.<Void>failedFuture(new IllegalStateException("Item deja verrouille"));
            }

            // C-060 : relecture autoritaire SOUS VERROU. Sans elle, un clic admin sur un noeud
            // desynchronise ecrase en aveugle la ligne PURCHASED d'un acheteur et duplique l'item.
            return revalidateAdminRemoval(item, storageType, clusterBridge).thenCompose(fresh -> {

                if (!Boolean.TRUE.equals(fresh)) {
                    this.plugin.getLogger().warning("Admin removal refused for item " + item.getId() + ": the database row is no longer " + storageType);
                    removeItem(storageType, item.getId()); // purge du fantome + marquage terminal
                    clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                    message(this.plugin, admin, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
                    return this.<Void>failedFuture(new IllegalStateException("Item deja traite sur un autre serveur"));
                }

                // C-012 : durabilite D'ABORD, diffusion d'un etat TERMINAL ensuite. L'ancienne
                // surcharge 2 arguments passait destination = null, donc Redis ecrivait
                // state=REMOVED et ItemRemovedListener rechargeait la ligne (encore LISTED, le
                // future d'ecriture etant jete) pour la remettre EN VENTE.
                item.setStatus(ItemStatus.DELETED);
                statusChanged.set(true);

                return storageManager.updateItem(item, storageType, StorageType.DELETED)
                        .thenCompose(v -> clusterBridge.removeItem(item, storageType, StorageType.DELETED));
            });

        }).thenCompose(v -> {

            removeItem(storageType, item);
            clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);

            // La remise est chainee (giveItem est confine au thread de l'entite) et compensee :
            // si elle n'a pas pu avoir lieu, la ligne redevient reclamable par son proprietaire
            // au lieu d'etre detruite.
            var claimable = storageType == StorageType.PURCHASED ? StorageType.PURCHASED : StorageType.EXPIRED;

            return deliverOrRestore(admin, item, claimable).thenAccept(delivered -> {

                var targetName = item.getSellerUniqueId().equals(targetUniqueId) ? item.getSellerName() : item.getBuyerName();
                message(this.plugin, admin, Message.ADMIN_ITEM_REMOVED, "%items%", item.getItemDisplay(), "%target%", targetName == null ? "unknown" : targetName);

                inventoryManager.updateInventory(admin);
            });

        }).whenComplete((v, throwable) -> {

            if (throwable != null) {

                var stale = StaleItemException.unwrap(throwable);
                if (stale != null) {
                    // Course perdue en base : la ligne ne portait plus l'etat source. On converge
                    // vers la base, on ne restaure surtout pas le statut precedent (le rediffuser
                    // remettrait en vente sur tout le cluster un item traite ailleurs).
                    this.plugin.getLogger().severe("ADMIN REMOVE LOST THE RACE - item " + item.getId() + " admin " + admin.getName()
                            + " : the row was already taken by another server, nothing was handed over, manual check advised");
                    removeItem(storageType, item.getId());
                    clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                    // Site (b) de ITEM_NO_LONGER_AVAILABLE : on distingue la course PERDUE EN BASE
                    // - l'ecriture est partie, une autre machine avait deja pris la ligne - des
                    // refus prealables ci-dessus, qui eux n'ont rien tente et portent
                    // ADMIN_ITEM_NOT_AVAILABLE.
                    message(this.plugin, admin, Message.ITEM_NO_LONGER_AVAILABLE);
                } else {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to remove item " + item.getId() + " for admin: " + throwable.getMessage(), throwable);

                    // Restaurer le statut si on l'avait deja passe a DELETED : sinon l'item
                    // resterait invisible localement tout en occupant le store, et deviendrait
                    // ni vendable ni recuperable.
                    if (statusChanged.get()) item.setStatus(previousStatus);
                }

                inventoryManager.updateInventory(admin);
            }

            // Le verrou n'etait relache que sur le chemin nominal : toute sortie par exception
            // (revalidation, ecriture, diffusion) le laissait fuir jusqu'au TTL. La garde
            // isAcquired() est indispensable : liberer avec un jeton noop casserait le verrou
            // legitime d'un autre appelant cote bridge local.
            var token = tokenHolder.get();
            if (token != null && token.isAcquired()) {
                clusterBridge.releaseLock(item, token, storageType).whenComplete((released, unlockError) -> {
                    if (unlockError != null) {
                        this.plugin.getLogger().severe("Failed to unlock item " + item.getId() + " after admin removal: " + unlockError.getMessage());
                    } else if (!Boolean.TRUE.equals(released)) {
                        this.plugin.getLogger().severe("Cluster lock for item " + item.getId() + " was NOT held by this server at release time (admin removal).");
                    }
                });
            }
        });
    }

    /**
     * Relecture autoritaire de la ligne base pour le retrait admin, effectuee SOUS VERROU.
     * <p>
     * Court-circuitee en mono-serveur : la memoire y est la verite et la garde d'identite en
     * tete de {@link #adminRemoveItem} suffit ; on ne facture pas un aller-retour SQL par clic.
     *
     * @param item          annonce visee
     * @param storageType   conteneur d'ou elle est censee sortir
     * @param clusterBridge pont cluster courant
     * @return {@code true} si la ligne est toujours celle qu'on croit detenir
     */
    private CompletableFuture<Boolean> revalidateAdminRemoval(Item item, StorageType storageType, AuctionClusterBridge clusterBridge) {

        if (!clusterBridge.isDistributed()) return CompletableFuture.completedFuture(Boolean.TRUE);

        var timeoutMs = this.plugin.getConfiguration().getPerformance().checkAvailabilityTimeoutMs();

        return selectItemRow(item.getId())
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .thenApply(optional -> optional.isPresent()
                        && optional.get().storage_type() == storageType
                        && (storageType != StorageType.LISTED || optional.get().buyer_unique_id() == null));
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
        var seller = auctionItem.getSeller();
        var storageManager = this.plugin.getStorageManager();
        var configuration = this.plugin.getConfiguration();
        var cache = this.getCache(player);
        var economyName = auctionEconomy.getName();
        var economyManager = this.plugin.getEconomyManager();

        String items = auctionItem.getItemsAsString();
        var itemsDisplay = auctionItem.getItemDisplay();

        // Calcul UNIQUE du montant de l'achat. Strictement le meme appel que celui fait par
        // PurchaseService pour verifier le solde et par ListedItemsButton pour l'affichage : le
        // montant verifie et le montant preleve ne peuvent plus diverger, et le type de taxe
        // reellement applique est celui porte par le TaxResult, plus jamais celui de l'economie
        // rededuit a posteriori.
        final ZPurchaseCharge charge;
        try {
            charge = ZPurchaseCharge.resolve(player, auctionItem, auctionEconomy);
        } catch (IllegalStateException exception) {
            this.plugin.getLogger().severe("Invalid tax configuration for item " + auctionItem.getId() + ": " + exception.getMessage());
            return failedFuture(exception);
        }

        TaxResult taxResult = charge.taxResult();
        BigDecimal buyerPays = charge.buyerPays();
        BigDecimal sellerReceives = charge.sellerReceives();

        if (taxResult.isBypassed()) {

            message(this.plugin, player, Message.TAX_EXEMPT);

        } else if (taxResult.hasTax()) {

            if (taxResult.isReduced()) {
                message(this.plugin, player, Message.TAX_REDUCED, "%percentage%",
                        String.format("%.1f", 100 - taxResult.reductionPercentage()));
            }

            if (taxResult.appliedType() == TaxType.CAPITALISM) {
                // TVA : l'acheteur paie price + taxe, le vendeur touche le prix plein.
                message(player, Message.TAX_CAPITALISM_INFO,
                        "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()),
                        "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
            } else {
                // PURCHASE / BOTH : l'acheteur paie le prix plein, le vendeur touche price - taxe.
                message(player, Message.TAX_PURCHASE_APPLIED,
                        "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()),
                        "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
            }
        }

        String resolvedSellerName = auctionItem.getSellerName();
        if (resolvedSellerName == null) {
            resolvedSellerName = storageManager.getPlayerName(auctionItem.getSellerUniqueId());
            if (resolvedSellerName != null) {
                this.plugin.getLogger().info("[ZAH] Resolved missing seller name for UUID "
                        + auctionItem.getSellerUniqueId() + " -> " + resolvedSellerName
                        + " (cross-server purchase, item " + auctionItem.getId() + ")");
            } else {

                resolvedSellerName = auctionItem.getSellerUniqueId().toString();
                this.plugin.getLogger().warning("[ZAH] Could not resolve seller name for UUID "
                        + auctionItem.getSellerUniqueId() + " (item " + auctionItem.getId()
                        + "). Using UUID string as fallback. Check that all servers share"
                        + " the same MySQL database and that upsertPlayer is being called.");
            }
        }
        final String sellerName = resolvedSellerName;

        // On retire l'argent de l'acheteur. withdrawChecked pre-controle le solde SOUS VERROU
        // et rend false quand le provider refuse : l'achat s'arrete AVANT que l'item ne bouge,
        // AVANT que le vendeur ne soit credite et AVANT toute ecriture d'etat.
        boolean withdrawn = auctionEconomy.withdrawChecked(player.getUniqueId(), buyerPays,
                args(auctionEconomy.getWithdrawReason(), "%seller%", sellerName, "%items%", items));

        if (!withdrawn) {
            this.plugin.getLogger().severe("Withdraw of " + buyerPays + " from buyer " + player.getName()
                    + " was refused for item " + auctionItem.getId()
                    + ". Purchase aborted before any item or status movement.");
            message(this.plugin, player, Message.NOT_ENOUGH_MONEY);
            // Remonte dans le .exceptionally de PurchaseService, qui restaure le statut et
            // relache le verrou. Rien n'a bouge : c'est le comportement voulu.
            throw new IllegalStateException("Buyer payment refused for item " + auctionItem.getId());
        }

        // On donne l'argent au vendeur
        TransactionStatus transactionStatus;
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        boolean sellerOnThisServer = seller.isOnline();
        boolean deferDeposit = !auctionEconomy.isAutoClaim()
                || (!sellerOnThisServer && auctionEconomy.mustBeOnline())
                || (!sellerOnThisServer && clusterBridge.isDistributed())
                // C-032 : ces economies (LEVEL, EXPERIENCE, ITEM) ne savent pas crediter un
                // joueur hors ligne et se contentent d'un no-op silencieux. Le paiement doit
                // devenir une transaction PENDING au lieu d'etre detruit.
                || (!sellerOnThisServer && !auctionEconomy.supportsOfflineDeposit());

        if (deferDeposit) {

            transactionStatus = TransactionStatus.PENDING;
        } else {

            transactionStatus = TransactionStatus.RETRIEVED;

            boolean deposited = auctionEconomy.depositChecked(seller.getUniqueId(), sellerReceives,
                    args(auctionEconomy.getDepositReason(), "%buyer%", player.getName(), "%items%", items));

            if (!deposited) {
                this.plugin.getLogger().severe("Failed to deposit " + sellerReceives + " to seller " + sellerName
                        + " for item " + auctionItem.getId() + ". Refunding the buyer and aborting the purchase.");

                if (!auctionEconomy.depositChecked(player.getUniqueId(), buyerPays,
                        args(auctionEconomy.getDepositReason(), "%buyer%", player.getName(), "%items%", items))) {
                    this.plugin.getLogger().severe("CRITICAL: buyer refund of " + buyerPays + " to "
                            + player.getName() + " ALSO failed for item " + auctionItem.getId()
                            + ". Manual reconciliation required.");
                }

                throw new IllegalStateException("Failed to deposit seller payment for item " + auctionItem.getId());
            }
        }

        // Comptabilite. Elle ne doit JAMAIS interrompre la livraison : a ce stade l'argent a
        // deja bouge. Toute exception du provider est journalisee, jamais propagee. Les
        // .exceptionally d'origine etaient de toute facon du code mort : ZAuctionEconomy.get
        // rend un CompletableFuture DEJA complete, aucune exception n'y transite.
        final BigDecimal finalBuyerPays = buyerPays;
        final BigDecimal finalSellerReceives = sellerReceives;
        final boolean deferred = deferDeposit;

        try {
            BigDecimal buyerBalance = readBalanceOrZero(auctionEconomy, player.getUniqueId());
            storageManager.createTransaction(auctionItem, player.getUniqueId(), economyName,
                    buyerBalance.add(finalBuyerPays), buyerBalance, finalBuyerPays.negate(), TransactionStatus.RETRIEVED);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to create buyer transaction for item "
                    + auctionItem.getId() + ": " + exception.getMessage());
        }

        try {
            BigDecimal sellerBalance = readBalanceOrZero(auctionEconomy, seller.getUniqueId());
            var beforeBalance = deferred ? sellerBalance : sellerBalance.subtract(finalSellerReceives);
            storageManager.createTransaction(auctionItem, seller.getUniqueId(), economyName,
                    beforeBalance, sellerBalance, finalSellerReceives, transactionStatus);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to create seller transaction for item "
                    + auctionItem.getId() + ": " + exception.getMessage());
        }

        // ------------------------------------------------------------------
        // A partir d'ici : la ligne en base bouge AVANT toute mutation memoire
        // et AVANT toute remise physique.
        //
        // setBuyer et setExpiredAt sont les seules mutations qui precedent l'ecriture, parce
        // qu'ils sont la CHARGE UTILE de l'UPDATE (le schema lit getBuyerUniqueId() et
        // getExpiredAt()).
        //
        // NOTE : le debit de l'acheteur et le credit du vendeur restent en amont. Apres un
        // echec de l'UPDATE l'acheteur est debite sans rien recevoir : c'est une perte
        // reversible a la main, la ou l'ordre precedent produisait une DUPLICATION
        // irreversible.
        // ------------------------------------------------------------------

        auctionItem.setBuyer(player);

        var purchasedConfiguration = configuration.getActions().purchased();
        final StorageType destination;

        if (purchasedConfiguration.giveItem()) {
            destination = StorageType.DELETED;
        } else {
            var expiration = configuration.getPurchaseExpiration().getExpiration(player);
            long purchaseExpiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            auctionItem.setExpiredAt(new Date(purchaseExpiredAt));
            destination = StorageType.PURCHASED;
        }

        final BigDecimal displayedBuyerPays = buyerPays;
        final BigDecimal displayedSellerReceives = sellerReceives;

        return storageManager.updateItem(auctionItem, StorageType.LISTED, destination).thenCompose(v -> {

            // --- 1. Mutations memoire (la base a confirme) ---
            auctionItem.setStatus(destination == StorageType.PURCHASED ? ItemStatus.PURCHASED : ItemStatus.DELETED);
            removeItem(StorageType.LISTED, auctionItem);
            if (destination == StorageType.PURCHASED) addItem(StorageType.PURCHASED, auctionItem);

            this.updateListedItems(auctionItem, false, player);
            clearPlayerCache(player, PlayerCacheKey.ITEMS_PURCHASED);
            if (seller.isOnline()) {
                var sellerPlayer = seller.getPlayer();
                if (sellerPlayer != null) {
                    clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.HISTORY_DATA, PlayerCacheKey.PENDING_MONEY_DATA);
                }
            }

            // --- 2. Notifications (l'achat est acquis, on peut l'annoncer) ---
            if (seller.isOnline()) {
                var sellerPlayer = seller.getPlayer();
                if (sellerPlayer != null) {
                    message(this.plugin, sellerPlayer, Message.ITEM_BOUGHT_SELLER, "%items%", itemsDisplay, "%price%", economyManager.format(auctionEconomy, displayedSellerReceives), "%seller%", sellerName, "%buyer%", player.getName());
                }
            }
            message(player, Message.ITEM_BOUGHT_BUYER, "%items%", itemsDisplay, "%price%", economyManager.format(auctionEconomy, displayedBuyerPays), "%seller%", sellerName, "%buyer%", player.getName());

            cache.remove(PlayerCacheKey.ITEM_SHOW);
            if (purchasedConfiguration.openInventory()) {
                openMainAuction(player, cache.get(PlayerCacheKey.CURRENT_PAGE, 1));
            } else {
                this.plugin.getScheduler().runAtEntity(player, w -> {
                    if (player.isOnline()) player.closeInventory();
                });
            }

            logItemAction(LogType.PURCHASE, auctionItem, player, auctionItem.getSellerUniqueId(), "purchase_item", seller.isOnline() ? new Date() : null);

            if (this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
                DiscordWebhookService discordService = zAuctionPlugin.getDiscordWebhookService();
                if (discordService != null && discordService.isEnabled()) {
                    discordService.notifyItemPurchased(player, auctionItem);
                }
                zAuctionPlugin.getBroadcastService().broadcastPurchase(player, auctionItem);
            }

            // --- 3. Remise physique, chainee et compensee ---
            if (destination != StorageType.DELETED) return CompletableFuture.<Void>completedFuture(null);

            // Si la remise ne peut pas avoir lieu, la ligne redevient PURCHASED : l'acheteur la
            // reclamera dans son onglet « items achetes » au lieu de perdre son achat.
            return deliverOrRestore(player, auctionItem, StorageType.PURCHASED).thenApply(delivered -> null);

        }).whenComplete((v, throwable) -> {
            // L'argent a deja bouge : si la ligne nous echappe ici, il faut la trace complete
            // pour un remboursement manuel.
            if (throwable != null && StaleItemException.unwrap(throwable) != null) {
                this.plugin.getLogger().severe("PURCHASE LOST THE RACE - item " + auctionItem.getId()
                        + " buyer " + player.getUniqueId() + " (" + player.getName() + ") price " + displayedBuyerPays
                        + " economy " + auctionEconomy.getName()
                        + " : money was moved but the row was already taken by another server, manual refund required");
            }
        });
    }

    /**
     * Lecture defensive d'un solde, uniquement destinee aux colonnes before/after de
     * l'historique.
     * <p>
     * {@code AuctionEconomy.get} rend aujourd'hui un future DEJA complete (voir
     * {@code ZAuctionEconomy.get}) : le {@code join()} ne bloque donc jamais.
     * <p>
     * En cas de panne du provider on rend ZERO plutot que de faire echouer un achat deja
     * commis : les colonnes before/after sont alors fausses pour cette ligne, ce qui est
     * strictement preferable a la perte de la dette PENDING du vendeur. Le SEVERE qui precede
     * rend le cas reperable.
     *
     * @param auctionEconomy economie interrogee
     * @param playerId       joueur dont on lit le solde
     * @return le solde, ou {@link BigDecimal#ZERO} si le provider ne repond pas
     */
    private BigDecimal readBalanceOrZero(AuctionEconomy auctionEconomy, UUID playerId) {
        try {
            var balance = auctionEconomy.get(playerId).join();
            return balance == null ? BigDecimal.ZERO : balance;
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Unable to read the balance of " + playerId + " for economy "
                    + auctionEconomy.getName() + ": " + exception.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Override
    public void message(Player player, Message message, Object... args) {
        this.message(this.plugin, player, message, args);
    }

    /**
     * Resultat d'une remise physique.
     *
     * @param executed  true si la tache a reellement tourne sur le thread de l'entite
     * @param delivered nombre d'ItemStack effectivement remis (ou deposes au sol)
     * @param total     nombre d'ItemStack que portait l'annonce
     */
    public record GiveResult(boolean executed, int delivered, int total) {

        /**
         * @param total nombre d'ItemStack que portait l'annonce
         * @return un resultat marquant qu'aucune remise n'a eu lieu
         */
        public static GiveResult notExecuted(int total) {
            return new GiveResult(false, 0, total);
        }

        /**
         * @return true si RIEN n'a ete remis : le lot doit etre rendu reclamable en base
         */
        public boolean isNothingDelivered() {
            return this.delivered == 0;
        }
    }

    /**
     * Remet le contenu d'une annonce dans l'inventaire d'un joueur.
     * <p>
     * La remise est TOUJOURS executee sur le thread de l'entite : c'est obligatoire sur Folia,
     * et c'est deja necessaire sur Paper puisque la chaine d'achat bascule sur un pool commun
     * des la relecture en base et la chaine de retrait sur l'executor de la base.
     * <p>
     * Le future rendu n'est JAMAIS complete exceptionnellement : un echec se lit dans le
     * {@link GiveResult}. La boucle ne s'interrompt plus au premier ItemStack null ou fautif,
     * ce qui evite qu'un lot partiellement corrompu soit detruit a moitie.
     *
     * @param player joueur destinataire
     * @param item   annonce a remettre
     * @return le compte-rendu de la remise
     */
    public CompletableFuture<GiveResult> giveItem(Player player, Item item) {

        if (!(item instanceof AuctionItem auctionItem)) {
            this.plugin.getLogger().severe("[ZAH] give item not implemented for item #" + item.getId());
            return CompletableFuture.completedFuture(GiveResult.notExecuted(0));
        }

        var itemStacks = auctionItem.getItemStacks();
        final int total = itemStacks == null ? 0 : itemStacks.size();
        if (total == 0) {
            this.plugin.getLogger().severe("[ZAH] Item #" + item.getId() + " has no content to give to " + player.getName());
            return CompletableFuture.completedFuture(GiveResult.notExecuted(0));
        }

        var future = new CompletableFuture<GiveResult>();
        var settled = new AtomicBoolean(false);

        var scheduled = this.plugin.getScheduler().runAtEntity(player, wrappedTask -> {

            // Le filet temporel ci-dessous a pu abandonner la remise et declencher la
            // compensation : dans ce cas il ne faut SURTOUT pas remettre les items, on
            // dupliquerait le lot.
            if (!settled.compareAndSet(false, true)) {
                this.plugin.getLogger().severe("[ZAH] Give aborted for item #" + item.getId()
                        + ": the delivery was already compensated, the item is claimable again.");
                return;
            }

            if (!player.isOnline()) {
                future.complete(GiveResult.notExecuted(total));
                return;
            }

            int delivered = 0;
            try {
                for (ItemStack itemStack : itemStacks) {
                    if (itemStack == null) {
                        this.plugin.getLogger().severe("[ZAH] Null ItemStack in item #" + item.getId() + ", skipped.");
                        continue;
                    }
                    try {
                        player.getInventory().addItem(itemStack.clone())
                                .forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));
                        delivered++;
                    } catch (Throwable throwable) {
                        this.plugin.getLogger().severe("[ZAH] Unable to give a stack of item #" + item.getId() + ": " + throwable);
                    }
                }
            } finally {
                future.complete(new GiveResult(true, delivered, total));
            }
        });

        // Filet 1 : la tache n'a pas pu etre planifiee (ENTITY_RETIRED / SCHEDULER_RETIRED sur Folia).
        scheduled.whenComplete((result, throwable) -> {
            if (throwable == null && result == EntityTaskResult.SUCCESS) return;
            if (settled.compareAndSet(false, true)) future.complete(GiveResult.notExecuted(total));
        });

        // Filet 2 : l'entite est retiree APRES la planification, personne ne complete le future.
        this.plugin.getScheduler().runLaterAsync(wrappedTask -> {
            if (settled.compareAndSet(false, true)) {
                this.plugin.getLogger().severe("[ZAH] Give timed out for item #" + item.getId()
                        + " (player " + player.getName() + "), the item will be made claimable again.");
                future.complete(GiveResult.notExecuted(total));
            }
        }, GIVE_ITEM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return future;
    }

    /**
     * Remet le lot au joueur APRES que l'ecriture en base a ete confirmee, et compense si la
     * remise n'a pas pu avoir lieu DU TOUT : la ligne est repositionnee dans
     * {@code claimableStorage} au lieu d'etre perdue. C'est ce qui empeche l'inversion d'ordre
     * d'echanger une duplication contre une destruction.
     * <p>
     * Ce future ne se termine JAMAIS en erreur : passe l'ecriture en base le retrait est commis
     * et ne doit plus jamais pouvoir etre rapporte en echec.
     *
     * @param player           destinataire de la remise
     * @param item             annonce concernee
     * @param claimableStorage conteneur ou la rendre reclamable si la remise echoue
     * @return true si le lot a effectivement ete remis au joueur
     */
    private CompletableFuture<Boolean> deliverOrRestore(Player player, Item item, StorageType claimableStorage) {

        return giveItem(player, item).thenCompose(giveResult -> {

            if (!giveResult.isNothingDelivered()) return CompletableFuture.completedFuture(Boolean.TRUE);

            this.plugin.getLogger().severe("[ZAH] Item #" + item.getId() + " could not be delivered to "
                    + player.getName() + ", restoring it as " + claimableStorage + " so it stays claimable.");

            return restoreClaimable(item, claimableStorage).thenApply(restored -> {
                if (!Boolean.TRUE.equals(restored)) {
                    this.plugin.getLogger().severe("[ZAH] CRITICAL: item #" + item.getId()
                            + " is neither delivered nor claimable, a manual restore is required.");
                }
                return Boolean.FALSE;
            });

        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("[ZAH] Unexpected error while delivering item #" + item.getId() + ": " + throwable);
            return Boolean.FALSE;
        });
    }

    /**
     * Repositionne dans un conteneur RECLAMABLE une ligne deja passee a DELETED dont la remise
     * physique n'a finalement pas pu avoir lieu.
     * <p>
     * Une SEULE primitive suffit desormais : {@link ItemRepository#restoreFromDeleted(Item, StorageType)}
     * porte un compare-and-set dont la source est DELETED (et non LISTED) et n'impose pas le
     * {@code whereNull("buyer_unique_id")} du chemin de vente. Elle couvre donc aussi bien la
     * compensation vers EXPIRED que celle vers PURCHASED, en rendant son propre rowcount : les
     * deux branches de contournement qui vivaient ici - dont un {@code updateItem} deprecie qui
     * jetait son rowcount et rendait donc un succes inconditionnel - disparaissent.
     * <p>
     * {@code expired_at} est remis a zero, ce qui vaut « n'expire jamais » ({@code ZItem.isExpired}) :
     * une compensation ne doit pas produire un item deja perime, donc immediatement re-balaye
     * par le service d'expiration. La primitive ecrit cette valeur telle quelle.
     *
     * @param item             annonce a rendre reclamable
     * @param claimableStorage conteneur cible (EXPIRED ou PURCHASED)
     * @return true si la ligne a bien ete repositionnee
     */
    private CompletableFuture<Boolean> restoreClaimable(Item item, StorageType claimableStorage) {

        var storageManager = this.plugin.getStorageManager();
        item.setExpiredAt(new Date(0));

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1 = ligne restauree, 0 = un autre noeud l'a deja bougee : dans ce dernier cas
                // la compensation est inutile ET interdite, la ligne ne nous appartient plus.
                return storageManager.with(ItemRepository.class).restoreFromDeleted(item, claimableStorage) == 1;
            } catch (Throwable throwable) {
                this.plugin.getLogger().severe("[ZAH] Unable to restore item #" + item.getId() + ": " + throwable);
                return Boolean.FALSE;
            }
        }, this.plugin.getExecutorService()).thenCompose(restored -> {

            if (!Boolean.TRUE.equals(restored)) return CompletableFuture.completedFuture(Boolean.FALSE);

            // La mutation du store et des index par proprietaire (IntArrayList non thread-safe)
            // repasse par le thread principal.
            var applied = new CompletableFuture<Boolean>();
            this.plugin.getScheduler().runNextTick(wrappedTask -> {
                item.setStatus(claimableStorage == StorageType.PURCHASED ? ItemStatus.PURCHASED : ItemStatus.REMOVED);
                addItem(claimableStorage, item);
                applied.complete(Boolean.TRUE);
            });
            return applied;
        });
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

        // Wait for the sorted cache to be rebuilt before updating inventories,
        // otherwise players get stale data cached from a dirty sorted cache
        this.sortedItemsCache.ensureCacheValidAsync().thenRun(() -> {
            for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {

                // Le `return` sortait du lambda thenRun, donc de TOUTE la boucle : tous les
                // joueurs situes apres le joueur ignore gardaient un GUI perime.
                if (onlinePlayer == ignoredPlayer) continue;

                this.plugin.getScheduler().runAtEntity(onlinePlayer, w -> {

                    var topInventory = CompatibilityUtil.getTopInventory(onlinePlayer);
                    if (topInventory == null) return;

                    var holder = topInventory.getHolder();
                    if (holder instanceof InventoryEngine inventoryEngine) {
                        var buttons = inventoryEngine.getMenuInventory().getButtons(ListedItemsButton.class);
                        if (buttons.isEmpty()) return;

                        var listedItemsButton = buttons.getFirst();
                        listedItemsButton.updateInventory(onlinePlayer, inventoryEngine, item, added, this);
                    }

                    if (!added) removeFromCache(onlinePlayer, item);
                });
            }
        });
    }

    private void removeFromCache(Player player, Item item) {
        // ITEMS_LISTED est un IntArrayList NON thread-safe partage avec le rendu de l'inventaire
        // du joueur. La branche update-inventory-on-action:false le mute pour TOUS les joueurs en
        // ligne depuis un thread arbitraire, pendant que le thread proprietaire l'itere. On
        // confine donc la mutation au thread du joueur (C-105).
        var scheduler = this.plugin.getScheduler();
        if (scheduler.isOwnedByCurrentRegion(player)) {
            removeFromCacheNow(player, item);
            return;
        }
        scheduler.runAtEntity(player, wrappedTask -> removeFromCacheNow(player, item));
    }

    private void removeFromCacheNow(Player player, Item item) {
        var cache = peekCache(player);
        if (cache == null) return;
        IntList items = cache.get(PlayerCacheKey.ITEMS_LISTED);
        if (items != null && !items.isEmpty()) {
            items.rem(item.getId());
        }
    }

    private <T> CompletableFuture<T> failedFuture(Throwable ex) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    private void logItemAction(LogType logType, Item item, Player player, UUID targetUniqueId, String additionalData, Date readedAt) {
        var storageManager = this.plugin.getStorageManager();
        var economy = item.getAuctionEconomy();
        var economyName = economy == null ? null : economy.getName();

        String encodedItemStack = null;
        if (item instanceof AuctionItem auctionItem) {
            var itemStacks = auctionItem.getItemStacks();
            if (itemStacks != null && !itemStacks.isEmpty()) {
                // Base64ItemStack.encode rend desormais null sur charge illisible : sans le
                // filtre, un null traverserait Collectors.joining sous la forme de la chaine
                // litterale "null" et corromprait le journal admin.
                encodedItemStack = itemStacks.stream().map(Base64ItemStack::encode).filter(Objects::nonNull).collect(Collectors.joining(";"));
            }
        }

        storageManager.log(logType, item.getId(), player, targetUniqueId, encodedItemStack, item.getPrice(), economyName, additionalData, readedAt);
    }

    private void logItemAction(LogType logType, Item item, Player player, UUID targetUniqueId, String additionalData) {
        logItemAction(logType, item, player, targetUniqueId, additionalData, null);
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

    @Override
    public void shutdown() {
        if (this.sortedItemsCache != null) {
            this.sortedItemsCache.shutdown();
        }
    }

    @Override
    public void startSearch(Player player, String query) {
        String normalizedQuery = query == null ? null : query.trim();
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            clearSearch(player);
            openAuctionInventory(player, 1);
            return;
        }

        var cache = getCache(player);
        cache.set(PlayerCacheKey.SEARCH_QUERY, normalizedQuery);
        cache.remove(PlayerCacheKey.ITEMS_SEARCH);
        cache.remove(PlayerCacheKey.ITEMS_LISTED);

        message(player, Message.SEARCH_SEARCHING, "%query%", normalizedQuery);

        openAuctionInventory(player, 1);
    }

    @Override
    public void clearSearch(Player player) {
        var cache = getCache(player);
        cache.remove(PlayerCacheKey.SEARCH_QUERY);
        cache.remove(PlayerCacheKey.ITEMS_SEARCH);
        cache.remove(PlayerCacheKey.ITEMS_LISTED);

        message(player, Message.SEARCH_CLEARED);
    }

    @Override
    public void removeAllExpiredItems(Player player) {
        var items = new ArrayList<>(getExpiredItems(player));
        if (items.isEmpty()) return;

        processBulkItems(player, items, item -> this.auctionRemoveService.removeExpiredItem(player, item, false))
                .thenAccept(progress -> finishBulkRemoval(player, progress, items.size(), this.plugin.getConfiguration().getActions().expired().openInventory(), PlayerCacheKey.ITEMS_EXPIRED));
    }

    @Override
    public void removeAllSellingItems(Player player) {
        var items = new ArrayList<>(getPlayerSellingItems(player));
        if (items.isEmpty()) return;

        processBulkItems(player, items, item -> this.auctionRemoveService.removeSellingItem(player, item, false))
                .thenAccept(progress -> finishBulkRemoval(player, progress, items.size(), true, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED));
    }

    @Override
    public void removeAllPurchasedItems(Player player) {
        var items = new ArrayList<>(getPurchasedItems(player));
        if (items.isEmpty()) return;

        processBulkItems(player, items, item -> this.auctionRemoveService.removePurchasedItem(player, item, false))
                .thenAccept(progress -> finishBulkRemoval(player, progress, items.size(), this.plugin.getConfiguration().getActions().purchased().openInventory(), PlayerCacheKey.ITEMS_PURCHASED));
    }

    private CompletableFuture<BulkRemovalProgress> processBulkItems(Player player, List<Item> items, Function<Item, CompletableFuture<RemoveResult>> removal) {
        CompletableFuture<BulkRemovalProgress> future = CompletableFuture.completedFuture(new BulkRemovalProgress(0, 0, false));

        for (Item item : items) {
            future = future.thenCompose(progress -> {
                if (progress.stopped()) return CompletableFuture.completedFuture(progress);

                return runBulkRemovalOnPlayerThread(player, item, removal).handle((result, throwable) -> {

                    if (throwable != null) {
                        // Une annonce en echec ne doit PAS emporter tout le lot : on la compte
                        // comme traitee-non-rendue et on continue avec les suivantes.
                        this.plugin.getLogger().severe("Bulk removal failed for item " + item.getId() + ": " + throwable.getMessage());
                        return new BulkRemovalProgress(progress.given(), progress.processed() + 1, false);
                    }

                    boolean success = result != null && result.isSuccess() && result.isItemGiven();

                    // Seul un inventaire plein justifie d'arreter : continuer n'aurait aucun sens.
                    // INTERNAL_ERROR ne doit plus interrompre la chaine (C-053).
                    boolean stopped = result == null || result.getFailReason() == RemoveFailReason.INSUFFICIENT_SPACE;

                    return new BulkRemovalProgress(progress.given() + (success ? 1 : 0), progress.processed() + 1, stopped);
                });
            });
        }

        return future;
    }

    private CompletableFuture<RemoveResult> runBulkRemovalOnPlayerThread(Player player, Item item, Function<Item, CompletableFuture<RemoveResult>> removal) {
        var future = new CompletableFuture<RemoveResult>();

        try {
            this.plugin.getScheduler().runAtEntity(player, wrappedTask -> {
                if (!player.isOnline()) {
                    future.complete(RemoveResult.failure("Player is offline", RemoveFailReason.INTERNAL_ERROR));
                    return;
                }

                try {
                    removal.apply(item).whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            future.complete(result);
                        } else {
                            future.completeExceptionally(throwable);
                        }
                    });
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }

        return future;
    }

    private void finishBulkRemoval(Player player, BulkRemovalProgress progress, int requested, boolean openInventory, PlayerCacheKey... cacheKeys) {
        this.plugin.getScheduler().runAtEntity(player, wrappedTask -> {
            clearPlayerCache(player, cacheKeys);

            int given = progress.given();
            if (given > 0) {
                message(this.plugin, player, Message.REMOVE_ALL_ITEMS, "%amount%", String.valueOf(given));
            }

            // Les annonces non rendues etaient jusqu'ici passees sous silence cote joueur : un
            // simple warning console lui laissait croire qu'il avait tout recupere alors que le
            // lot s'etait arrete au premier incident.
            int skipped = requested - given;
            if (skipped > 0) {
                // L'inventaire plein est la SEULE cause qui interrompt le lot : on la nomme, sinon
                // le joueur ne sait pas quoi faire pour obtenir le reste.
                if (progress.stopped()) {
                    message(this.plugin, player, Message.NOT_ENOUGH_SPACE);
                }

                message(this.plugin, player, Message.REMOVE_ALL_ITEMS_PARTIAL, "%amount%", String.valueOf(skipped));
            }

            if (!player.isOnline()) return;

            if (openInventory) {
                updateInventory(player);
            } else {
                player.closeInventory();
            }
        });
    }

    private record BulkRemovalProgress(int given, int processed, boolean stopped) { }
}
