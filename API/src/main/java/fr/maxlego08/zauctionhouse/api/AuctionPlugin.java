package fr.maxlego08.zauctionhouse.api;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermission;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleManager;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;

/**
 * Core access point exposed to external plugins. This interface mirrors the main plugin instance
 * while surfacing strongly typed services such as storage, economy, inventory loading, and cluster
 * synchronization. Implementations should return live services ready for immediate use after
 * plugin initialization.
 */
public interface AuctionPlugin extends Plugin {

    /**
     * Reloads configuration files and dependent services without requiring a full server restart.
     */
    void reload();

    /**
     * @return scheduler abstraction used to dispatch synchronous and asynchronous tasks
     */
    PlatformScheduler getScheduler();

    /**
     * @return persistence layer handling database connections, migrations, and repositories
     */
    StorageManager getStorageManager();

    /**
     * @return current runtime configuration snapshot
     */
    Configuration getConfiguration();

    /**
     * @return central manager orchestrating auction logic and player interactions
     */
    AuctionManager getAuctionManager();

    /**
     * @return loader capable of registering and opening inventory-based menus
     */
    InventoriesLoader getInventoriesLoader();

    /**
     * @return manager providing access to registered economy implementations and formatting helpers
     */
    EconomyManager getEconomyManager();

    /**
     * @return executor used for blocking operations such as disk or database I/O
     */
    ExecutorService getExecutorService();

    /**
     * @return placeholder service that resolves custom tokens inside messages
     */
    Placeholder getPlaceholder();

    /**
     * @return bridge used to synchronize auction data across clustered server instances
     */
    AuctionClusterBridge getAuctionClusterBridge();

    /**
     * @return manager responsible for applying rule checks when items are listed or purchased
     */
    ItemRuleManager getItemRuleManager();

    /**
     * Overrides the default cluster bridge at runtime, allowing integrations to swap transport
     * mechanisms.
     *
     * @param auctionClusterBridge cluster bridge implementation to use
     */
    void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge);

    /**
     * @return helper used to evaluate permissions for offline players
     */
    OfflinePermission getOfflinePermission();

    /**
     * Registers a custom offline permission checker.
     *
     * @param offlinePermission permission resolver to use
     */
    void setOfflinePermission(OfflinePermission offlinePermission);

    /**
     * Checks whether a file embedded in the plugin jar exists.
     *
     * @param resourcePath path inside the jar
     * @return {@code true} if the resource exists
     */
    boolean resourceExist(String resourcePath);

    /**
     * Saves an embedded resource to disk.
     *
     * @param resourcePath path inside the jar
     * @param toPath       filesystem target path
     * @param replace      whether to overwrite an existing file
     */
    void saveResource(String resourcePath, String toPath, boolean replace);

    /**
     * Saves or updates a configuration resource, optionally merging existing content.
     *
     * @param resourcePath path inside the jar
     * @param toPath       filesystem target path
     * @param replace      whether to overwrite an existing file
     */
    void saveOrUpdateConfiguration(String resourcePath, String toPath, boolean replace);

    /**
     * Saves an embedded file to the plugin's data directory using the same path name.
     *
     * @param resourcePath path inside the jar
     * @param saveOrUpdate {@code true} to merge with an existing file when possible
     */
    void saveFile(String resourcePath, boolean saveOrUpdate);

    /**
     * Saves an embedded file to a custom location in the plugin's data directory.
     *
     * @param resourcePath path inside the jar
     * @param toPath       filesystem target path
     * @param saveOrUpdate {@code true} to merge with an existing file when possible
     */
    void saveFile(String resourcePath, String toPath, boolean saveOrUpdate);

}
