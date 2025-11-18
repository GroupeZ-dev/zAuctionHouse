package fr.maxlego08.zauctionhouse;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.ConfigurationFile;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.command.CommandManager;
import fr.maxlego08.zauctionhouse.command.commands.CommandAuction;
import fr.maxlego08.zauctionhouse.configuration.MainConfiguration;
import fr.maxlego08.zauctionhouse.economy.ZEconomyManager;
import fr.maxlego08.zauctionhouse.listeners.PlayerListener;
import fr.maxlego08.zauctionhouse.loader.MessageLoader;
import fr.maxlego08.zauctionhouse.loader.ZInventoriesLoader;
import fr.maxlego08.zauctionhouse.storage.ZStorageManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class ZAuctionPlugin extends JavaPlugin implements AuctionPlugin {

    private final StorageManager storageManager = new ZStorageManager(this);
    private final Configuration configuration = new MainConfiguration(this);
    private final ConfigurationFile messageLoader = new MessageLoader(this);
    private final CommandManager commandManager = new CommandManager(this);
    private final AuctionManager auctionManager = new ZAuctionManager(this);
    private final EconomyManager economyManager = new ZEconomyManager(this);
    private InventoriesLoader inventoriesLoader;
    private boolean isEnabled = false;
    private PlatformScheduler platformScheduler;

    @Override
    public void onEnable() {

        this.saveDefaultConfig();

        FoliaLib foliaLib = new FoliaLib(this);
        this.platformScheduler = foliaLib.getScheduler();

        if (!this.storageManager.onEnable()) return;

        this.loadFiles();

        this.addListener(new PlayerListener(this));

        this.commandManager.registerCommand(this, "zauctionhouse", new CommandAuction(this), List.of("ah"));

        this.inventoriesLoader = new ZInventoriesLoader(this);
        this.inventoriesLoader.load();

        isEnabled = true;
        this.getLogger().info("zAuctionHouse has just been loaded successfully!");
    }

    @Override
    public void onDisable() {

        if (!this.isEnabled) return;

        this.storageManager.onDisable();
    }

    @Override
    public void reload() {
        this.loadFiles();
        this.inventoriesLoader.reload();
    }

    private void loadFiles() {
        this.configuration.load(); // Load config.yml
        this.messageLoader.load(); // Load messages.yml
        this.economyManager.loadEconomies(); // Load economies.yml
    }

    @Override
    public PlatformScheduler getScheduler() {
        return this.platformScheduler;
    }

    @Override
    public StorageManager getStorageManager() {
        return this.storageManager;
    }

    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    @Override
    public AuctionManager getAuctionManager() {
        return this.auctionManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    @Override
    public InventoriesLoader getInventoriesLoader() {
        return this.inventoriesLoader;
    }

    @Override
    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }

    private void addListener(Listener listener) {
        this.getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public boolean resourceExist(String resourcePath) {
        if (resourcePath != null && !resourcePath.equals("")) {
            resourcePath = resourcePath.replace('\\', '/');
            InputStream in = this.getResource(resourcePath);
            return in != null;
        }
        return false;
    }

    @Override
    public void saveResource(String resourcePath, String toPath, boolean replace) {
        if (resourcePath != null && !resourcePath.equals("")) {
            resourcePath = resourcePath.replace('\\', '/');
            InputStream in = this.getResource(resourcePath);
            if (in == null) {
                throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + this.getFile());
            } else {
                File outFile = new File(getDataFolder(), toPath);
                int lastIndex = toPath.lastIndexOf(47);
                File outDir = new File(getDataFolder(), toPath.substring(0, Math.max(lastIndex, 0)));
                if (!outDir.exists()) {
                    outDir.mkdirs();
                }

                try {
                    if (outFile.exists() && !replace) {
                        getLogger().log(Level.WARNING, "Could not save " + outFile.getName() + " to " + outFile + " because " + outFile.getName() + " already exists.");
                    } else {
                        OutputStream out = Files.newOutputStream(outFile.toPath());
                        byte[] buf = new byte[1024];

                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }

                        out.close();
                        in.close();
                    }
                } catch (IOException exception) {
                    getLogger().log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, exception);
                }

            }
        } else throw new IllegalArgumentException("ResourcePath cannot be null or empty");
    }

    @Override
    public void saveOrUpdateConfiguration(String resourcePath, String toPath, boolean deep) {
        File file = new File(getDataFolder(), toPath);
        if (!file.exists()) {
            saveResource(resourcePath, toPath, false);
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        try {

            InputStream inputStream = this.getResource(resourcePath);

            if (inputStream == null) {
                this.getLogger().severe("Cannot find file " + resourcePath);
                return;
            }

            Reader defConfigStream = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);

            Set<String> defaultKeys = defConfig.getKeys(deep);

            boolean configUpdated = false;
            for (String key : defaultKeys) {
                if (!config.contains(key)) {
                    this.getLogger().severe("I can’t find " + key + " in the file " + file.getName());
                    configUpdated = true;
                }
            }

            config.setDefaults(defConfig);
            config.options().copyDefaults(true);

            if (configUpdated) {
                this.getLogger().info("Update file " + toPath);
                config.save(file);
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}