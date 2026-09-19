package fr.maxlego08.zauctionhouse.api;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.command.CommandManager;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentManager;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermission;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.migration.MigrationRegistry;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleManager;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import org.bukkit.command.CommandSender;
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
     * @return command manager for registering and resolving commands
     */
    CommandManager getCommandManager();

    /**
     * Sends a formatted message to a command sender, resolving placeholders and message types.
     *
     * @param sender  the recipient
     * @param message the message enum entry
     * @param args    placeholder key-value pairs (must be even-length)
     */
    void sendMessage(CommandSender sender, Message message, Object... args);

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
     * Indique si le plugin a commence son arret.
     * <p>
     * Les services (achat, vente, retrait) doivent refuser toute NOUVELLE operation quand
     * cette methode rend {@code true} : leurs chaines tournent sur des pools que le plugin ne
     * controle pas (ForkJoinPool.commonPool, pool Jedis de l'addon) et survivent donc a
     * {@code onDisable}, ou elles debiteraient un acheteur pendant que la connexion base se
     * ferme (C-022).
     * <p>
     * Methode {@code default} : source- et binaire-compatible pour toute implementation tierce
     * existante, comme {@link fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge#isDistributed()}.
     *
     * @return {@code true} si l'arret du plugin a commence
     */
    default boolean isShuttingDown() {
        return false;
    }

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
     * @return manager responsible for category matching and organization
     */
    CategoryManager getCategoryManager();

    /**
     * @return registry for rule loaders, allowing external plugins to register custom rules
     */
    RuleLoaderRegistry getRuleLoaderRegistry();

    /**
     * @return registry for migration providers, allowing external plugins to register custom migrations
     */
    MigrationRegistry getMigrationRegistry();

    /**
     * Overrides the default cluster bridge at runtime, allowing integrations to swap transport
     * mechanisms.
     *
     * @param auctionClusterBridge cluster bridge implementation to use
     */
    void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge);

    /**
     * @return manager for item content providers (shulker box contents, custom containers, etc.)
     */
    ItemContentManager getItemContentManager();

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
