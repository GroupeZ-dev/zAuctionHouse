package fr.maxlego08.zauctionhouse.api;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import org.bukkit.plugin.Plugin;

public interface AuctionPlugin extends Plugin {

    void reload();

    PlatformScheduler getScheduler();

    StorageManager getStorageManager();

    Configuration getConfiguration();

    boolean resourceExist(String resourcePath);

    void saveResource(String resourcePath, String toPath, boolean replace);

    void saveOrUpdateConfiguration(String resourcePath, String toPath, boolean replace);
}
