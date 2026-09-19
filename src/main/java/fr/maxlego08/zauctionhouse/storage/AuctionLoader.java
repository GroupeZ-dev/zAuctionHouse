package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;
import fr.maxlego08.zauctionhouse.utils.ItemLoaderUtils;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AuctionLoader extends ItemLoaderUtils {

    private final AuctionPlugin plugin;
    private final Logger logger;
    private final AuctionManager auctionManager;
    private final StorageManager storageManager;
    private final EconomyManager economyManager;
    private final PerformanceDebug performanceDebug;

    public AuctionLoader(AuctionPlugin plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageManager = storageManager;
        this.auctionManager = plugin.getAuctionManager();
        this.economyManager = plugin.getEconomyManager();
        this.performanceDebug = new PerformanceDebug(plugin);
    }

    /**
     * Recharge l'integralite des annonces depuis la base, puis remplace le contenu des storages.
     * <p>
     * CHARGER D'ABORD, REMPLACER ENSUITE. loadItems() n'est pas appele qu'au demarrage :
     * CommandAuctionAdminMigrate l'invoque A CHAUD, sur un serveur vivant. Depuis C-001 les deux
     * lectures ci-dessous PROPAGENT leur echec ({@code selectAllOrFail} / {@code selectOrFail}) :
     * vider les storages AVANT de lire laissait, sur une simple coupure MySQL, les conteneurs
     * VIDES et toutes les instances precedentes marquees DELETED, donc un hotel des ventes hors
     * service, invisible et non reclamable, jusqu'au redemarrage.
     * <p>
     * La lecture et la construction des instances se font donc integralement HORS LIGNE, sans
     * toucher aux storages en service ; la bascule n'a lieu qu'une fois la lecture reussie et
     * n'est plus qu'une manipulation memoire. Un echec de lecture ne modifie plus rien : il est
     * journalise en SEVERE puis propage a l'appelant (ZAuctionPlugin refuse alors le demarrage,
     * /ah admin migrate doit afficher un vrai message d'erreur).
     * <p>
     * La purge des fantomes apportee par C-044 est CONSERVEE : c'est la bascule qui devient
     * atomique, pas la purge qui disparait. Au demarrage le comportement est strictement
     * inchange : les storages y sont vides, il n'y a rien a purger.
     *
     * @throws RuntimeException si la lecture base echoue ; les storages sont alors INTACTS
     */
    public void loadItems() {
        long totalStartTime = performanceDebug.start();

        // Photographie des ids presents AVANT la lecture : seules ces entrees seront purgees a la
        // bascule. Une annonce publiee par un autre thread PENDANT la lecture (SellService,
        // ExpireService, listeners de l'addon Redis) est absente de la photo base et ne doit
        // surtout pas etre detruite par le rechargement.
        Map<StorageType, int[]> previousIds = captureStoredIds();

        Map<UUID, String> players;
        List<StagedItem> stagedItems = new ArrayList<>();
        ItemLoaderUtils.Result result;

        try {

            // Load players
            long playersStartTime = performanceDebug.start();
            players = this.storageManager.with(PlayerRepository.class).select().stream().collect(Collectors.toMap(PlayerDTO::unique_id, PlayerDTO::name));
            performanceDebug.end("loadItems.loadPlayers", playersStartTime, "count=" + players.size());

            // Load items from database
            long itemsStartTime = performanceDebug.start();
            var items = this.storageManager.with(ItemRepository.class).select();
            performanceDebug.end("loadItems.loadItemsFromDB", itemsStartTime, "count=" + items.size());

            // Les instances sont construites dans une liste LOCALE : aucun addItem, donc aucun
            // effet de bord visible tant que la lecture n'est pas terminee avec succes.
            result = this.createItems(this.plugin, players, items, performanceDebug, (storageType, item) -> stagedItems.add(new StagedItem(storageType, item)));

        } catch (RuntimeException | Error throwable) {
            this.logger.log(Level.SEVERE, "Unable to read the auction items from the database: the in-memory auction "
                    + "house has been left UNTOUCHED (nothing was cleared, " + stagedItems.size()
                    + " partially loaded item(s) discarded). The previous state is still being served.", throwable);
            throw throwable;
        }

        this.logger.info("Loaded " + players.size() + " players successfully");

        // Bascule : purge des anciennes entrees puis publication des nouvelles. Memoire pure,
        // aucune I/O, donc aucun point d'echec pouvant laisser l'hotel des ventes a moitie vide.
        long swapStartTime = performanceDebug.start();
        var swapResult = this.swapStorages(previousIds, stagedItems);
        performanceDebug.end("loadItems.swapStorages", swapStartTime, "removed=" + swapResult.removed() + ", added=" + swapResult.added() + ", skipped=" + swapResult.skipped());

        performanceDebug.end("loadItems.total", totalStartTime, "players=" + players.size() + ", items=" + result.amount() + ", auctionItems=" + result.auctionItems());
        this.logger.info("Loaded " + result.amount() + " items successfully (" + result.auctionItems() + " total)");

        // Rebuild the sorted items cache after bulk loading
        long cacheStartTime = performanceDebug.start();
        auctionManager.rebuildSortedItemsCache();
        performanceDebug.end("loadItems.rebuildSortedItemsCache", cacheStartTime, "scheduled async rebuild");
    }

    /**
     * Photographie, conteneur par conteneur, les identifiants actuellement detenus en memoire.
     * <p>
     * {@code getItems(StorageType)} rend une copie ({@code new ArrayList<>(...)}) : la photo est
     * sure meme si un autre thread mute le store pendant l'appel.
     *
     * @return les identifiants presents, par conteneur, au moment de l'appel
     */
    private Map<StorageType, int[]> captureStoredIds() {
        Map<StorageType, int[]> storedIds = new EnumMap<>(StorageType.class);
        for (StorageType storageType : StorageType.values()) {
            storedIds.put(storageType, this.auctionManager.getItems(storageType).stream().mapToInt(Item::getId).toArray());
        }
        return storedIds;
    }

    /**
     * Remplace le contenu des storages par les instances fraichement chargees.
     * <p>
     * N'utilise QUE des methodes deja publiees par {@link AuctionManager} ({@code getItem},
     * {@code addItem}, {@code removeItem}, {@code clearPlayersCache}) : aucun changement d'API.
     *
     * @param previousIds les identifiants photographies AVANT la lecture base
     * @param stagedItems les instances construites a partir de la lecture base
     * @return le detail de la bascule (purges, publications, conflits ignores)
     */
    private SwapResult swapStorages(Map<StorageType, int[]> previousIds, List<StagedItem> stagedItems) {

        int removed = 0;
        for (Map.Entry<StorageType, int[]> entry : previousIds.entrySet()) {
            StorageType storageType = entry.getKey();
            for (int itemId : entry.getValue()) {
                // L'entree a pu quitter ce conteneur pendant la lecture (achat, retrait,
                // convergence Redis) : dans ce cas on n'y touche pas, et surtout on ne marque pas
                // DELETED l'instance vivante qu'un autre conteneur detient desormais.
                if (this.auctionManager.getItem(storageType, itemId) == null) continue;
                this.auctionManager.removeItem(storageType, itemId);
                removed++;
            }
        }

        int added = 0;
        int skipped = 0;
        for (StagedItem stagedItem : stagedItems) {

            int itemId = stagedItem.item().getId();
            StorageType holder = findHolder(itemId);
            if (holder != null) {
                // Tout ce qui survit a la purge a ete ecrit APRES notre photo base : l'etat vivant
                // est plus recent que la ligne relue, il gagne. Republier la ligne relue
                // ressusciterait en AVAILABLE une annonce achetee entre-temps, que son vendeur
                // pourrait alors retirer alors que l'acheteur a deja recu le lot (C-044, point D :
                // RemoveService n'a aucune revalidation autoritaire sous verrou).
                this.logger.warning("Auction item #" + itemId + " changed while the auction house was reloading "
                        + "(live instance held by " + holder + "): keeping the live state, the database snapshot is stale.");
                skipped++;
                continue;
            }

            this.auctionManager.addItem(stagedItem.storageType(), stagedItem.item());
            added++;
        }

        if (removed > 0) {
            this.logger.warning("Cleared " + removed + " in-memory item(s) and replaced them with the database state");
        }

        if (removed > 0 || added > 0) {
            // On NE purge PAS les cles SELL_* : un joueur en cours de mise en vente perdrait son
            // panier. ITEM_SHOW et CURRENT_PAGE sont purges volontairement : ils referencent des
            // objets Item qui viennent d'etre remplaces. Purge en DERNIER, apres les addItem :
            // un recalcul concurrent ne peut plus remettre en cache l'etat d'avant la bascule.
            this.auctionManager.clearPlayersCache(
                    PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_PURCHASED,
                    PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH,
                    PlayerCacheKey.ITEM_SHOW, PlayerCacheKey.CURRENT_PAGE);
        }

        return new SwapResult(removed, added, skipped);
    }

    /**
     * Cherche le conteneur qui detient encore une instance vivante pour cet identifiant.
     *
     * @param itemId identifiant a chercher
     * @return le conteneur detenteur, ou {@code null} si aucun ne detient cet identifiant
     */
    private StorageType findHolder(int itemId) {
        for (StorageType storageType : StorageType.values()) {
            if (this.auctionManager.getItem(storageType, itemId) != null) return storageType;
        }
        return null;
    }

    /**
     * Une annonce construite depuis la base, en attente de publication dans son conteneur.
     *
     * @param storageType conteneur de destination
     * @param item        l'instance construite
     */
    private record StagedItem(StorageType storageType, Item item) {
    }

    /**
     * Comptes de la bascule.
     *
     * @param removed nombre d'entrees purgees
     * @param added   nombre d'annonces publiees
     * @param skipped nombre d'annonces ignorees car l'etat memoire vivant est plus recent
     */
    private record SwapResult(int removed, int added, int skipped) {
    }
}
