package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionExpireEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ExpireService implements AuctionExpireService {

    private final AuctionPlugin plugin;
    private final AuctionManager auctionManager;
    // Coalesces concurrent cluster-aware expiration dispatches for the same item id, so a seller
    // spamming their selling tab cannot trigger a stampede of redundant Redis round-trips while
    // one expiration is already in flight for that item.
    private final Set<Integer> expiringItemIds = ConcurrentHashMap.newKeySet();

    public ExpireService(AuctionPlugin plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @Override
    public void processExpiredItem(Item item, StorageType storageType) {

        // Guard: skip items already processed by another server (e.g., claimed via Redis cluster)
        if (item.getStatus() == ItemStatus.DELETED) {
            this.auctionManager.removeItem(storageType, item);
            return;
        }

        // Multi-server: route LISTED -> EXPIRED through a cluster-aware path that locks the item,
        // re-validates authoritative DB state (skipping items sold on another server), performs
        // the move, then broadcasts it so other nodes converge. Prevents a sold item from being
        // resurrected as EXPIRED for the seller (duplication). Single-server keeps the fast path.
        if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {
            expireListedItemClustered(item);
            return;
        }

        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(List.of(item), storageType);
            event.callEvent();
        });

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH); // Suppression du cache global

        var offlineSeller = item.getSeller();
        if (offlineSeller.isOnline()) {
            var sellerPlayer = offlineSeller.getPlayer();
            if (sellerPlayer != null) {
                this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
            }
        }

        if (storageType == StorageType.LISTED) {

            item.setStatus(ItemStatus.REMOVED);
            this.auctionManager.removeItem(StorageType.LISTED, item);

            Consumer<Long> applyExpiration = expiration -> this.plugin.getScheduler().runNextTick(w -> {
                long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                item.setExpiredAt(new Date(expiredAt));

                this.auctionManager.addItem(StorageType.EXPIRED, item);
                // C-078 : ecriture compare-and-set. Si la ligne n'est plus LISTED (vendue ou
                // expiree par un autre noeud), le future echoue avec StaleItemException et le
                // fantome memoire est purge au lieu de rester reclamable.
                storageManager.updateItem(item, StorageType.LISTED, StorageType.EXPIRED).exceptionally(throwable -> {
                    handleStaleTransition(item, StorageType.EXPIRED, throwable);
                    return null;
                });
            });

            var onlinePlayer = offlineSeller.isOnline() ? offlineSeller.getPlayer() : null;
            if (onlinePlayer != null) {
                var expiration = configuration.getExpireExpiration().getExpiration(onlinePlayer);
                applyExpiration.accept(expiration);
            } else {
                configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), offlineSeller)
                        .whenComplete((expiration, throwable) -> {
                            long safeExpiration = expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration();
                            if (throwable != null) {
                                this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + offlineSeller.getName(), throwable);
                            }
                            applyExpiration.accept(safeExpiration);
                        });
            }

        } else {

            item.setStatus(ItemStatus.DELETED);
            // C-078 : l'objet doit QUITTER le store. Le laisser dedans avec un statut DELETED
            // en fait un fantome que le joueur voit encore et peut tenter de reclamer.
            this.auctionManager.removeItem(storageType, item);
            storageManager.updateItem(item, storageType, StorageType.DELETED).exceptionally(throwable -> {
                handleStaleTransition(item, StorageType.DELETED, throwable);
                return null;
            });
        }

        // Log expiration for debugging purposes
        if (this.plugin.getConfiguration().isEnableDebug()) {
            this.plugin.getLogger().info("Item " + item.getId() + " expired from " + storageType + " (seller: " + item.getSellerName() + ")");
        }
    }

    @Override
    public void processExpiredItems(List<Item> items, StorageType storageType) {
        if (items.isEmpty()) return;

        // C-088 : ce point d'entree n'est plus seulement appele par un clic joueur, il l'est
        // aussi par le balayage d'expiration PLANIFIE. Entre la selection du lot et son
        // traitement (au moins un tick plus tard, davantage sous charge) un item a pu etre
        // vendu, retire, sorti du store ou entrer en section critique. Le lot est donc refiltre
        // ici sur trois criteres au lieu d'un seul. Aucune etape de cette methode ne suppose
        // qu'un joueur est connecte : le vendeur est resolu en OfflinePlayer et chaque acces a
        // getPlayer() est garde.
        List<Item> filtered = new ArrayList<>();
        for (Item item : items) {

            if (item == null) continue;

            // Deja traite ailleurs (reclame via le bus cluster) : l'instance doit quitter le store.
            if (item.getStatus() == ItemStatus.DELETED) {
                this.auctionManager.removeItem(storageType, item);
                continue;
            }

            // Reference fantome : le store ne detient plus CETTE instance. Un autre chemin l'a
            // deja sortie ou remplacee ; la traiter ecraserait une ligne qui n'est plus a nous.
            if (this.auctionManager.getItem(storageType, item.getId()) != item) continue;

            // Etat transitoire : une confirmation est ouverte ou une section critique d'achat /
            // de retrait est en vol. C'est a cette chaine de conclure ; le balayage suivant
            // reprendra l'item si elle ne revient jamais (le balayage TTL des statuts de
            // confirmation la debloque de son cote).
            if (isTransientStatus(item.getStatus())) continue;

            filtered.add(item);
        }
        if (filtered.isEmpty()) return;

        // Multi-server: route LISTED -> EXPIRED through the cluster-aware per-item path.
        if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {
            for (Item item : filtered) {
                expireListedItemClustered(item);
            }
            return;
        }

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        List<Item> validItems = filtered;
        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(validItems, storageType);
            event.callEvent();
        });

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);

        // Clear player caches for online sellers
        Set<OfflinePlayer> offlinePlayers = new HashSet<>();
        for (Item item : validItems) {
            var offlineSeller = item.getSeller();
            if (offlineSeller.isOnline() && !offlinePlayers.contains(offlineSeller)) {
                offlinePlayers.add(offlineSeller);
                var sellerPlayer = offlineSeller.getPlayer();
                if (sellerPlayer != null) {
                    this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
                }
            }
        }

        if (storageType == StorageType.LISTED) {
            // Items from LISTED storage go to EXPIRED
            List<Item> onlineSellerItems = new ArrayList<>();
            List<Item> offlineSellerItems = new ArrayList<>();

            // Remove all items from LISTED storage and separate by seller online status
            for (Item item : validItems) {
                item.setStatus(ItemStatus.REMOVED);
                this.auctionManager.removeItem(StorageType.LISTED, item);
                if (item.getSeller().isOnline()) {
                    onlineSellerItems.add(item);
                } else {
                    offlineSellerItems.add(item);
                }
            }

            // Process online sellers synchronously and batch update
            if (!onlineSellerItems.isEmpty()) {
                for (Item item : onlineSellerItems) {
                    var sellerPlayer = item.getSeller().getPlayer();
                    // Player may have disconnected between isOnline() check and now
                    long expiration = sellerPlayer != null
                            ? configuration.getExpireExpiration().getExpiration(sellerPlayer)
                            : configuration.getExpireExpiration().defaultExpiration();
                    long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                    item.setExpiredAt(new Date(expiredAt));
                    this.auctionManager.addItem(StorageType.EXPIRED, item);
                }

                // Batch update all online seller items
                // C-094 : le batch rend les ids qui ont PERDU la course, dont on purge la memoire.
                Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                batchUpdate.put(StorageType.EXPIRED, onlineSellerItems);
                storageManager.updateItems(batchUpdate, StorageType.LISTED)
                        .thenAccept(staleIds -> dropStaleGhosts(staleIds, StorageType.EXPIRED))
                        .exceptionally(throwable -> {
                            this.plugin.getLogger().log(Level.SEVERE, "Failed to persist batch expiration of " + onlineSellerItems.size() + " item(s)", throwable);
                            return null;
                        });
            }

            // Process offline sellers asynchronously then batch update
            if (!offlineSellerItems.isEmpty()) {
                AtomicInteger remaining = new AtomicInteger(offlineSellerItems.size());
                List<Item> processedItems = new CopyOnWriteArrayList<>();

                for (Item item : offlineSellerItems) {
                    configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), item.getSeller())
                            .whenComplete((expiration, throwable) -> {
                                long safeExpiration = expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration();
                                if (throwable != null) {
                                    this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + item.getSeller().getName(), throwable);
                                }

                                long expiredAt = safeExpiration > 0 ? System.currentTimeMillis() + (safeExpiration * 1000) : 0;
                                item.setExpiredAt(new Date(expiredAt));

                                processedItems.add(item);
                                this.auctionManager.addItem(StorageType.EXPIRED, item);

                                // When all items are processed, batch update
                                if (remaining.decrementAndGet() == 0) {
                                    this.plugin.getScheduler().runNextTick(w -> {
                                        Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                                        batchUpdate.put(StorageType.EXPIRED, new ArrayList<>(processedItems));
                                        storageManager.updateItems(batchUpdate, StorageType.LISTED)
                                                .thenAccept(staleIds -> dropStaleGhosts(staleIds, StorageType.EXPIRED))
                                                .exceptionally(batchThrowable -> {
                                                    this.plugin.getLogger().log(Level.SEVERE, "Failed to persist batch expiration (offline sellers)", batchThrowable);
                                                    return null;
                                                });
                                    });
                                }
                            });
                }
            }

        } else {
            // Items from EXPIRED storage go to DELETED
            for (Item item : validItems) {
                item.setStatus(ItemStatus.DELETED);
                // C-078 : idem, l'objet quitte le store, il n'est plus reclamable.
                this.auctionManager.removeItem(storageType, item);
            }

            // Batch update all items to DELETED
            Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
            batchUpdate.put(StorageType.DELETED, validItems);
            storageManager.updateItems(batchUpdate, storageType)
                    .thenAccept(staleIds -> {
                        if (staleIds != null && !staleIds.isEmpty()) {
                            this.plugin.getLogger().warning(staleIds.size() + " item(s) lost the deletion race: " + staleIds);
                        }
                    })
                    .exceptionally(throwable -> {
                        this.plugin.getLogger().log(Level.SEVERE, "Failed to persist batch deletion of " + validItems.size() + " item(s)", throwable);
                        return null;
                    });
        }
    }

    /**
     * Cluster-aware LISTED -> EXPIRED transition used in multi-server (distributed) setups.
     * <p>
     * Sequence: check availability -> acquire the cluster lock -> re-read authoritative DB state
     * -> if the item was sold/deleted on another server, drop the local ghost and skip; otherwise
     * perform the local move + DB update, then broadcast the removal (source=LISTED,
     * destination=EXPIRED) so other nodes converge -> release the lock.
     * <p>
     * Holding the lock across the whole operation serializes expiration against purchases and
     * against expiration on other nodes, so a sold item can never be resurrected as EXPIRED for
     * the seller. Items being purchased (cluster state LOCKED) or already handled by another node
     * are skipped silently and re-evaluated on a later sweep.
     */
    private void expireListedItemClustered(Item item) {
        // Coalesce concurrent dispatches for the same item id (see expiringItemIds). If an
        // expiration is already in flight, skip; it will be re-evaluated on a later sweep if needed.
        if (!this.expiringItemIds.add(item.getId())) {
            return;
        }
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var perf = this.plugin.getConfiguration().getPerformance();
        var storageManager = this.plugin.getStorageManager();
        var logger = this.plugin.getLogger();
        var tokenHolder = new AtomicReference<LockToken>();

        clusterBridge.checkAvailability(item)
                .orTimeout(perf.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(available -> {
                    if (!available) {
                        // Item is locked (being purchased) on the cluster: do not expire now.
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    return clusterBridge.lockItem(item, item.getSellerUniqueId(), StorageType.LISTED)
                            .orTimeout(perf.lockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                            .thenCompose(token -> {
                                tokenHolder.set(token);
                                // Un jeton non acquis couvre a la fois la contention (noop) et
                                // l'etat terminal a portee (unavailable) : dans les deux cas, cet
                                // item ne nous appartient pas.
                                if (token == null || !token.isAcquired()) {
                                    // Another server is already processing this item.
                                    return CompletableFuture.<Void>completedFuture(null);
                                }
                                return storageManager.selectItemState(item.getId())
                                        .orTimeout(perf.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                                        .thenCompose(lookup -> {
                                            // C-091 : la ligne doit etre ENCORE listee. ItemRepository.select(int)
                                            // ne filtre que DELETED : une ligne deja passee a EXPIRED par un
                                            // autre noeud remonte ici avec buyer_unique_id null et franchirait
                                            // l'ancienne garde. ItemLoaderUtils traduit LISTED -> AVAILABLE,
                                            // donc tester le statut equivaut a tester storage_type = 'LISTED'.
                                            var dbItem = lookup.item();
                                            boolean stillListed = lookup.isFound() && dbItem != null
                                                    && dbItem.getBuyerUniqueId() == null
                                                    && dbItem.getStatus() == ItemStatus.AVAILABLE;

                                            if (!stillListed) {
                                                // C-109 : on ne purge la copie memoire QUE lorsque la base est
                                                // formelle. UNAVAILABLE signifie que la ligne EXISTE mais n'a pas
                                                // su etre relue (economie absente d'economies.yml, contenu
                                                // illisible) : purger ferait disparaitre une annonce parfaitement
                                                // valide pour une simple erreur de configuration.
                                                if (lookup.state() == StorageManager.LookupState.UNAVAILABLE) {
                                                    logger.warning("Item " + item.getId() + " could not be re-read from the database (unknown economy or unreadable content):"
                                                            + " expiration postponed, the listing is left untouched.");
                                                    return CompletableFuture.<Void>completedFuture(null);
                                                }
                                                // Sold/deleted/already expired on another server: remove the
                                                // local ghost, do NOT expire.
                                                this.plugin.getScheduler().runNextTick(w -> {
                                                    this.auctionManager.removeItem(StorageType.LISTED, item.getId());
                                                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                                                });
                                                return CompletableFuture.<Void>completedFuture(null);
                                            }
                                            // Genuinely still listed: perform the move locally, then broadcast.
                                            return performListedToExpired(item)
                                                    .thenCompose(v -> clusterBridge.removeItem(item, StorageType.LISTED, StorageType.EXPIRED)
                                                            .orTimeout(perf.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS));
                                        });
                            });
                })
                .whenComplete((v, throwable) -> {
                    this.expiringItemIds.remove(item.getId());
                    if (throwable != null) {
                        logger.warning("Cluster-aware expiration skipped/failed for item " + item.getId() + ": " + throwable.getMessage());
                    }
                    var token = tokenHolder.get();
                    if (token != null && token.isAcquired()) {
                        // C-061 : le verdict de liberation est desormais consomme. Un false signifie
                        // que le jeton presente n'etait plus celui inscrit cote cluster : le verrou a
                        // ete repris par un autre noeud pendant la section critique.
                        clusterBridge.releaseLock(item, token, StorageType.LISTED).whenComplete((released, ex) -> {
                            if (ex != null) {
                                logger.severe("Failed to unlock item " + item.getId() + " after expiration: " + ex.getMessage());
                            } else if (!Boolean.TRUE.equals(released)) {
                                logger.severe("Cluster lock for item " + item.getId() + " was NOT held at release time after expiration.");
                            }
                        });
                    }
                });
    }

    /**
     * Performs the local LISTED -> EXPIRED move (fire event, clear caches, compute expiration,
     * mutate the item, update the in-memory stores and the database). Returns a future that
     * completes only after the database row has been updated, so the caller can keep the cluster
     * lock held until the change is durable.
     */
    private CompletableFuture<Void> performListedToExpired(Item item) {
        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();
        var offlineSeller = item.getSeller();

        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(List.of(item), StorageType.LISTED);
            event.callEvent();
            this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            if (offlineSeller.isOnline()) {
                var sellerPlayer = offlineSeller.getPlayer();
                if (sellerPlayer != null) {
                    this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
                }
            }
        });

        CompletableFuture<Long> expirationFuture;
        var onlinePlayer = offlineSeller.isOnline() ? offlineSeller.getPlayer() : null;
        if (onlinePlayer != null) {
            expirationFuture = CompletableFuture.completedFuture(configuration.getExpireExpiration().getExpiration(onlinePlayer));
        } else {
            expirationFuture = configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), offlineSeller)
                    .thenApply(expiration -> expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration())
                    .exceptionally(throwable -> {
                        this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + offlineSeller.getName(), throwable);
                        return configuration.getExpireExpiration().defaultExpiration();
                    });
        }

        return expirationFuture.thenCompose(expiration -> {
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            CompletableFuture<Void> done = new CompletableFuture<>();
            this.plugin.getScheduler().runNextTick(w -> {
                var previousExpiredAt = item.getExpiredAt();
                item.setExpiredAt(new Date(expiredAt));
                storageManager.updateItem(item, StorageType.LISTED, StorageType.EXPIRED).whenComplete((u, t) -> {
                    if (t != null) {
                        // DB update failed: revert the expiredAt change and leave the item in the
                        // LISTED store unchanged so a later sweep retries. Never create a local
                        // EXPIRED phantom against a still-LISTED database row.
                        item.setExpiredAt(previousExpiredAt);
                        if (StaleItemException.unwrap(t) != null) {
                            // Course perdue : l'item n'est plus a nous, on retire le fantome local
                            // au lieu de laisser un LISTED memoire face a une base qui dit autre chose.
                            handleStaleTransition(item, StorageType.EXPIRED, t);
                        }
                        done.completeExceptionally(t);
                        return;
                    }
                    // DB row is now EXPIRED: apply the in-memory move on the main thread, only once
                    // the change is durable.
                    this.plugin.getScheduler().runNextTick(w2 -> {
                        item.setStatus(ItemStatus.REMOVED);
                        this.auctionManager.removeItem(StorageType.LISTED, item);
                        this.auctionManager.addItem(StorageType.EXPIRED, item);
                        done.complete(null);
                    });
                });
            });
            return done;
        });
    }

    /**
     * Un statut transitoire signale une chaine en cours sur cet item : confirmation ouverte cote
     * joueur, ou section critique d'achat / de retrait deja engagee (verrou pose, argent ou lot
     * potentiellement en mouvement). L'expiration ne doit alors JAMAIS s'inviter : elle
     * deplacerait la ligne sous les pieds de la chaine et le lot serait duplique ou perdu.
     *
     * @param status le statut de l'item au moment du balayage
     * @return {@code true} si l'item appartient a une operation en cours
     */
    private static boolean isTransientStatus(ItemStatus status) {
        return status == ItemStatus.IS_BEING_PURCHASED
                || status == ItemStatus.IS_BEING_REMOVED
                || status == ItemStatus.IS_PURCHASE_CONFIRM
                || status == ItemStatus.IS_REMOVE_CONFIRM;
    }

    /**
     * La transition a perdu la course : la ligne ne portait plus l'etat source attendu (un autre
     * serveur l'a vendue, expiree ou detruite entre-temps). On ne conserve JAMAIS le fantome en
     * memoire, il serait reclamable localement alors que la base dit autre chose.
     *
     * @param item        l'item dont la transition a echoue
     * @param destination l'etat destination vise par le compare-and-set
     * @param throwable   l'erreur remontee par la couche de stockage
     */
    private void handleStaleTransition(Item item, StorageType destination, Throwable throwable) {
        var stale = StaleItemException.unwrap(throwable);
        if (stale == null) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to persist transition of item " + item.getId() + " to " + destination, throwable);
            return;
        }
        this.plugin.getLogger().warning("Item " + item.getId() + " lost the " + destination + " race (" + stale.getMessage() + "), dropping the local ghost");
        dropStaleGhosts(List.of(item.getId()), destination);
    }

    /**
     * Purge de la memoire les items qui n'ont pas remporte leur transition, dans le conteneur
     * source comme dans le conteneur destination, puis invalide les caches d'affichage.
     *
     * @param staleIds    les identifiants des items ayant perdu la course
     * @param destination l'etat destination vise par le compare-and-set
     */
    private void dropStaleGhosts(List<Integer> staleIds, StorageType destination) {
        if (staleIds == null || staleIds.isEmpty()) return;
        this.plugin.getLogger().warning(staleIds.size() + " item(s) lost the " + destination + " race, dropping local ghosts: " + staleIds);
        this.plugin.getScheduler().runNextTick(w -> {
            for (Integer staleId : staleIds) {
                this.auctionManager.removeItem(StorageType.LISTED, staleId);
                this.auctionManager.removeItem(StorageType.EXPIRED, staleId);
                this.auctionManager.removeItem(StorageType.PURCHASED, staleId);
            }
            this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED, PlayerCacheKey.ITEMS_SEARCH);
        });
    }
}
