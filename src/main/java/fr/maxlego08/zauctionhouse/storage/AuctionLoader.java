package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.PlayerRepository;
import fr.maxlego08.zauctionhouse.utils.ItemLoaderUtils;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;

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

    public void loadItems() {
        long totalStartTime = performanceDebug.start();

        // Load players
        long playersStartTime = performanceDebug.start();
        var players = this.storageManager.with(PlayerRepository.class).select().stream().collect(Collectors.toMap(PlayerDTO::unique_id, PlayerDTO::name));
        performanceDebug.end("loadItems.loadPlayers", playersStartTime, "count=" + players.size());
        this.plugin.getLogger().info("Loaded " + players.size() + " players successfully");

        var categoryManager = this.plugin.getCategoryManager();

        // Load items from database
        long itemsStartTime = performanceDebug.start();
        var items = this.storageManager.with(ItemRepository.class).select();
        performanceDebug.end("loadItems.loadItemsFromDB", itemsStartTime, "count=" + items.size());

        var result = this.createItems(this.plugin, players, items, performanceDebug, auctionManager::addItem);

        performanceDebug.end("loadItems.total", totalStartTime, "players=" + players.size() + ", items=" + result.amount() + ", auctionItems=" + result.auctionItems());
        this.logger.info("Loaded " + result.amount() + " items successfully (" + result.auctionItems() + " total)");

        // Rebuild the sorted items cache after bulk loading
        long cacheStartTime = performanceDebug.start();
        auctionManager.rebuildSortedItemsCache();
        performanceDebug.end("loadItems.rebuildSortedItemsCache", cacheStartTime, "scheduled async rebuild");
    }
}
