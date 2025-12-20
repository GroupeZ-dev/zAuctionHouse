package fr.maxlego08.zauctionhouse;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.ConfigurationFile;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermission;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleManager;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.utils.Plugins;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.command.CommandManager;
import fr.maxlego08.zauctionhouse.command.commands.CommandAuction;
import fr.maxlego08.zauctionhouse.configuration.MainConfiguration;
import fr.maxlego08.zauctionhouse.economy.ZEconomyManager;
import fr.maxlego08.zauctionhouse.hooks.permissions.EmptyOfflinePermission;
import fr.maxlego08.zauctionhouse.hooks.permissions.LuckPermsOfflinePermission;
import fr.maxlego08.zauctionhouse.listeners.PlayerListener;
import fr.maxlego08.zauctionhouse.listeners.SellInventoryListener;
import fr.maxlego08.zauctionhouse.loader.MessageLoader;
import fr.maxlego08.zauctionhouse.loader.ZInventoriesLoader;
import fr.maxlego08.zauctionhouse.placeholder.DistantPlaceholder;
import fr.maxlego08.zauctionhouse.placeholder.LocalPlaceholder;
import fr.maxlego08.zauctionhouse.placeholder.placeholders.GlobalPlaceholders;
import fr.maxlego08.zauctionhouse.placeholder.placeholders.PlayerPlaceholders;
import fr.maxlego08.zauctionhouse.rule.ZItemRuleManager;
import fr.maxlego08.zauctionhouse.storage.ZStorageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class ZAuctionPlugin extends JavaPlugin implements AuctionPlugin {

    private final Locale locale = Locale.getDefault();
    private final StorageManager storageManager = new ZStorageManager(this);
    private final Configuration configuration = new MainConfiguration(this);
    private final ConfigurationFile messageLoader = new MessageLoader(this);
    private final CommandManager commandManager = new CommandManager(this);
    private final AuctionManager auctionManager = new ZAuctionManager(this);
    private final EconomyManager economyManager = new ZEconomyManager(this);
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);
    private final Placeholder placeholder = new LocalPlaceholder(this);
    private final ItemRuleManager itemRuleManager = new ZItemRuleManager(this);
    private InventoriesLoader inventoriesLoader;
    private boolean isEnabled = false;
    private PlatformScheduler platformScheduler;
    private AuctionClusterBridge auctionClusterBridge = new LocalAuctionClusterBridge();
    private OfflinePermission offlinePermission = new EmptyOfflinePermission();

    @Override
    public void onEnable() {

        var dataFolder = this.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        this.saveFile("config.yml", true);

        FoliaLib foliaLib = new FoliaLib(this);
        this.platformScheduler = foliaLib.getScheduler();

        if (!this.storageManager.onEnable()) return;

        // On doit créer la class des inventaires avant de charger la configuration, cela permet d'utiliser les interfaces de zmenu partout
        this.inventoriesLoader = new ZInventoriesLoader(this);

        this.loadFiles();

        this.addListener(new PlayerListener(this));
        this.addListener(new SellInventoryListener(this));

        this.commandManager.registerCommand(this, "zauctionhouse", new CommandAuction(this), getConfig().getStringList("commands.main-command.aliases"));

        this.inventoriesLoader.load();

        this.storageManager.loadItems();

        this.registerPlaceholders();
        this.registerHooks();

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

        if (!new File(getDataFolder(), "config.yml").exists()) {
            this.saveFile("config.yml", true);
        }

        this.reloadConfig();
        this.loadFiles();
        this.inventoriesLoader.reload();
    }

    private void loadFiles() {
        this.configuration.load(); // Load config.yml
        this.messageLoader.load(); // Load messages.yml
        this.economyManager.loadEconomies(); // Load economies.yml
        this.itemRuleManager.loadRules(); // Load rules.yml
    }

    private void registerPlaceholders() {
        DistantPlaceholder distantPlaceholder = new DistantPlaceholder(this, this.placeholder);
        distantPlaceholder.register();

        this.registerPlaceholder(PlayerPlaceholders.class);
        this.registerPlaceholder(GlobalPlaceholders.class);
    }

    private void registerHooks() {

        if (isEnable(Plugins.LUCKPERMS)) {
            this.offlinePermission = new LuckPermsOfflinePermission();
            this.getLogger().info("LuckPerms has been enabled successfully!");
        }
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

    @Override
    public ExecutorService getExecutorService() {
        return this.asyncExecutor;
    }

    @Override
    public AuctionClusterBridge getAuctionClusterBridge() {
        return this.auctionClusterBridge;
    }

    @Override
    public void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge) {
        this.auctionClusterBridge = auctionClusterBridge;
    }

    @Override
    public ItemRuleManager getItemRuleManager() {
        return this.itemRuleManager;
    }

    @Override
    public OfflinePermission getOfflinePermission() {
        return this.offlinePermission;
    }

    @Override
    public void setOfflinePermission(OfflinePermission offlinePermission) {
        this.offlinePermission = offlinePermission;
    }

    @Override
    public Placeholder getPlaceholder() {
        return this.placeholder;
    }

    private void addListener(Listener listener) {
        this.getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public boolean resourceExist(String resourcePath) {
        if (resourcePath != null && !resourcePath.isEmpty()) {
            resourcePath = resourcePath.replace('\\', '/');
            InputStream in = this.getResource(resourcePath);
            return in != null;
        }
        return false;
    }

    @Override
    public void saveResource(String resourcePath, String toPath, boolean replace) {
        if (resourcePath != null && !resourcePath.isEmpty()) {
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

    @Override
    public void saveFile(String resourcePath, boolean saveOrUpdate) {
        this.saveFile(resourcePath, resourcePath, saveOrUpdate);
    }

    @Override
    public void saveFile(String resourcePath, String toPath, boolean saveOrUpdate) {
        var langResourcePath = locale.getLanguage() + "/" + resourcePath;
        var finalPath = resourcePath;
        if (this.resourceExist(langResourcePath)) {
            finalPath = langResourcePath;
        }

        if (saveOrUpdate) this.saveOrUpdateConfiguration(finalPath, toPath, false);
        else this.saveResource(finalPath, toPath, false);
    }

    private <T extends PlaceholderRegister> T registerPlaceholder(Class<T> placeholderClass) {
        try {
            T placeholderRegister = placeholderClass.getConstructor().newInstance();
            placeholderRegister.register(this.placeholder, this);
            return placeholderRegister;
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return null;
    }

    public boolean isEnable(Plugins pluginName) {
        Plugin plugin = getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    public boolean isActive(Plugins pluginName) {
        Plugin plugin = getPlugin(pluginName);
        return plugin != null;
    }

    protected Plugin getPlugin(Plugins plugin) {
        return Bukkit.getPluginManager().getPlugin(plugin.getName());
    }
}