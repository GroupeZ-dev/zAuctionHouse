package fr.maxlego08.zauctionhouse.api;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermission;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ExecutorService;

public interface AuctionPlugin extends Plugin {

    void reload();

    PlatformScheduler getScheduler();

    StorageManager getStorageManager();

    Configuration getConfiguration();

    AuctionManager getAuctionManager();

    InventoriesLoader getInventoriesLoader();

    EconomyManager getEconomyManager();

    ExecutorService getExecutorService();

    Placeholder getPlaceholder();

    AuctionClusterBridge getAuctionClusterBridge();

    void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge);

    OfflinePermission getOfflinePermission();

    void setOfflinePermission(OfflinePermission offlinePermission);

    boolean resourceExist(String resourcePath);

    void saveResource(String resourcePath, String toPath, boolean replace);

    void saveOrUpdateConfiguration(String resourcePath, String toPath, boolean replace);

    void saveFile(String resourcePath, boolean saveOrUpdate);

    void saveFile(String resourcePath, String toPath, boolean saveOrUpdate);

}
