package fr.maxlego08.zauctionhouse;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import dev.faststats.bukkit.BukkitContext;
import fr.maxlego08.sarah.database.DatabaseType;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.command.CommandManager;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.ConfigurationFile;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentManager;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermission;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import fr.maxlego08.zauctionhouse.api.migration.MigrationRegistry;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleManager;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.api.utils.Plugins;
import fr.maxlego08.zauctionhouse.category.ZCategoryManager;
import fr.maxlego08.zauctionhouse.cluster.LocalAuctionClusterBridge;
import fr.maxlego08.zauctionhouse.command.ZCommandManager;
import fr.maxlego08.zauctionhouse.command.commands.CommandAuction;
import fr.maxlego08.zauctionhouse.configuration.MainConfiguration;
import fr.maxlego08.zauctionhouse.discord.DiscordWebhookService;
import fr.maxlego08.zauctionhouse.economy.ZEconomyManager;
import fr.maxlego08.zauctionhouse.hooks.itemcontent.VanillaShulkerContentProvider;
import fr.maxlego08.zauctionhouse.hooks.itemcontent.ZItemContentManager;
import fr.maxlego08.zauctionhouse.hooks.permissions.EmptyOfflinePermission;
import fr.maxlego08.zauctionhouse.hooks.permissions.LuckPermsOfflinePermission;
import fr.maxlego08.zauctionhouse.listeners.PlayerListener;
import fr.maxlego08.zauctionhouse.loader.MessageLoader;
import fr.maxlego08.zauctionhouse.loader.ZInventoriesLoader;
import fr.maxlego08.zauctionhouse.maintenance.ZMaintenanceScheduler;
import fr.maxlego08.zauctionhouse.migration.ZMigrationRegistry;
import fr.maxlego08.zauctionhouse.migration.v3.V3MigrationProvider;
import fr.maxlego08.zauctionhouse.permissions.PermissionRegistrar;
import fr.maxlego08.zauctionhouse.placeholder.DistantPlaceholder;
import fr.maxlego08.zauctionhouse.placeholder.LocalPlaceholder;
import fr.maxlego08.zauctionhouse.placeholder.placeholders.GlobalPlaceholders;
import fr.maxlego08.zauctionhouse.placeholder.placeholders.OptionPlaceholders;
import fr.maxlego08.zauctionhouse.placeholder.placeholders.PlayerPlaceholders;
import fr.maxlego08.zauctionhouse.services.BroadcastService;
import fr.maxlego08.zauctionhouse.rule.ZItemRuleManager;
import fr.maxlego08.zauctionhouse.rule.ZRuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.search.ChatSearchListener;
import fr.maxlego08.zauctionhouse.storage.ZStorageManager;
import fr.maxlego08.zauctionhouse.utils.LocaleHelper;
import fr.maxlego08.zauctionhouse.utils.Metrics;
import fr.maxlego08.zauctionhouse.utils.VersionChecker;
import fr.maxlego08.zauctionhouse.utils.documentation.DocumentationGenerator;
import fr.maxlego08.zauctionhouse.utils.yaml.YamlUpdater;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class ZAuctionPlugin extends JavaPlugin implements AuctionPlugin {

    private final StorageManager storageManager = new ZStorageManager(this);
    private final Configuration configuration = new MainConfiguration(this);
    private final ConfigurationFile messageLoader = new MessageLoader(this);
    private final ZCommandManager commandManager = new ZCommandManager(this);
    private final AuctionManager auctionManager = new ZAuctionManager(this);
    private final EconomyManager economyManager = new ZEconomyManager(this);
    private final ExecutorService asyncExecutor = createStorageExecutor();
    private final Placeholder placeholder = new LocalPlaceholder(this);
    private final ZRuleLoaderRegistry ruleLoaderRegistry = new ZRuleLoaderRegistry(this);
    private final ItemRuleManager itemRuleManager = new ZItemRuleManager(this, ruleLoaderRegistry);
    private final CategoryManager categoryManager = new ZCategoryManager(this, ruleLoaderRegistry);
    private final YamlUpdater yamlUpdater = new YamlUpdater(this);
    private final MigrationRegistry migrationRegistry = new ZMigrationRegistry(this);
    private final PermissionRegistrar permissionRegistrar = new PermissionRegistrar(this);
    private final ItemContentManager itemContentManager = new ZItemContentManager();
    private final MessageHelper messageHelper = new MessageHelper();
    private final ZMaintenanceScheduler maintenanceScheduler = new ZMaintenanceScheduler(this);
    private LocaleHelper localeHelper;
    private InventoriesLoader inventoriesLoader;
    private ChatSearchListener chatSearchListener;
    private BroadcastService broadcastService;
    private DiscordWebhookService discordWebhookService;
    private VersionChecker versionChecker;
    private boolean isEnabled = false;

    // Arme a la TOUTE PREMIERE ligne de onDisable(), donc AVANT toute fermeture de ressource :
    // les services doivent cesser d'accepter de nouvelles operations avant que quoi que ce
    // soit ne soit ferme (C-022).
    private volatile boolean shuttingDown = false;

    // Le teardown est desormais inconditionnel ; ce drapeau n'existe que pour le rendre
    // idempotent face a un onDisable reentrant (C-118).
    private volatile boolean teardownDone = false;

    private PlatformScheduler platformScheduler;

    // Ecrit une seule fois par le onEnable de l'addon Redis, lu par les trois services depuis
    // des workers : sans volatile, aucune relation happens-before ne garantit que le worker
    // voie autre chose que le LocalAuctionClusterBridge initial (C-117).
    private volatile AuctionClusterBridge auctionClusterBridge = new LocalAuctionClusterBridge();

    // Meme defaut, en pire : setOfflinePermission est publie dans l'API, donc appelable par un
    // plugin tiers depuis n'importe quel thread, et lu par ExpireService sur des chemins
    // asynchrones (C-117).
    private volatile OfflinePermission offlinePermission = new EmptyOfflinePermission();
    private final BukkitContext context = new BukkitContext.Factory(this, "3854068cfb5b22b58f2ae87e2c84062a")
            .metrics(dev.faststats.Metrics.Factory::create)
            .create();

    /**
     * Taille du pool d'IO bloquante. Le passage de 4 a max(4, availableProcessors()) est le
     * prerequis du routage de tous les logs, transactions et upserts vers ce meme pool.
     */
    private static final int STORAGE_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());

    /**
     * Nombre de taches de stockage ABANDONNEES par la politique de rejet depuis le demarrage.
     * La premiere est journalisee avec sa pile -- qui designe le site de soumission -- les
     * suivantes en une seule ligne, pour ne pas noyer la console pendant un arret de serveur.
     */
    private final AtomicLong rejectedStorageTasks = new AtomicLong();

    /**
     * Executor dedie a l'IO bloquante (JDBC). Deux differences avec
     * {@code Executors.newFixedThreadPool(4)} :
     * <ul>
     *   <li>threads nommes et daemon, pour que les dumps de threads soient exploitables ;</li>
     *   <li>politique de rejet explicite, {@link #handleRejectedStorageTask}, au lieu de la
     *       RejectedExecutionException levee par defaut.</li>
     * </ul>
     * La file est volontairement NON BORNEE : un rejet ne peut donc survenir qu'apres
     * {@code shutdown()}. C'est le drainage de onDisable (shutdown + awaitTermination AVANT la
     * fermeture de la connexion), et non la politique de rejet, qui garantit que les ecritures
     * deja soumises aboutissent (C-022).
     *
     * @return l'executeur de stockage
     */
    private ExecutorService createStorageExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "zAuctionHouse-Storage-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(STORAGE_POOL_SIZE, STORAGE_POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), factory, this::handleRejectedStorageTask);
    }

    /**
     * Politique de rejet de l'executeur de stockage.
     * <p>
     * Un handler INCONDITIONNEL {@code (runnable, executor) -> runnable.run()} etait une
     * regression grave : il executait une ecriture JDBC BLOQUANTE sur le thread appelant, or
     * {@code ZStorageManager.async()} y route {@code upsertPlayer}, {@code log()} et
     * {@code createTransaction} -- appeles depuis des listeners et des commandes, donc depuis
     * le THREAD PRINCIPAL. Une base lente y figeait le tick, watchdog compris.
     * <p>
     * Deux gardes, dans cet ordre :
     * <ol>
     *   <li>executeur arrete : semantique exacte de {@code CallerRunsPolicy}, on N'EXECUTE PAS.
     *       Le drainage est en cours et la connexion base est sur le point d'etre fermee ;
     *       executer ici reviendrait a ecrire sur une connexion morte. Contrairement a
     *       {@code CallerRunsPolicy}, l'abandon n'est PAS silencieux : il est journalise en
     *       SEVERE, avec la pile du site de soumission pour la premiere occurrence.</li>
     *   <li>thread de tick appelant : on N'EXECUTE PAS non plus. Perdre une ecriture est un
     *       incident journalise ; bloquer le tick sur du JDBC est un incident serveur.</li>
     * </ol>
     * Hors de ces deux cas -- executeur vivant ET appelant hors tick -- la tache est bien
     * executee dans le thread appelant, comme {@code CallerRunsPolicy}.
     *
     * @param runnable la tache de stockage rejetee
     * @param executor l'executeur qui l'a rejetee
     */
    private void handleRejectedStorageTask(Runnable runnable, ThreadPoolExecutor executor) {

        boolean shutdown = executor.isShutdown();
        boolean tickThread = isServerTickThread();

        if (!shutdown && !tickThread) {
            runnable.run();
            return;
        }

        long dropped = this.rejectedStorageTasks.incrementAndGet();
        String reason = shutdown
                ? "the storage executor is already shut down (the database connection is closing)"
                : "the calling thread is a server tick thread and JDBC must never block a tick";

        var rejection = new RejectedExecutionException("zAuctionHouse storage task rejected: " + reason);

        if (dropped == 1) {
            // La pile designe le site de soumission de l'ecriture perdue : c'est la seule
            // facon de l'identifier.
            getLogger().log(Level.SEVERE, "A storage write was DROPPED because " + reason
                    + ". The stack trace below points at the submission site.", rejection);
        } else {
            getLogger().severe("A storage write was DROPPED because " + reason
                    + " (" + dropped + " dropped since startup).");
        }

        // On LEVE, on ne rend jamais la main en silence.
        //
        // Le contrat d'Executor.execute est d'executer la tache OU de lever : un retour
        // silencieux est la seule issue interdite. CompletableFuture.runAsync(r, executor)
        // cree son futur PUIS appelle execute() ; si execute() rend la main sans avoir
        // programme la tache, ce futur n'est JAMAIS complete, ni normalement ni
        // exceptionnellement. Tout ce qui s'y enchaine attend alors indefiniment -- et la
        // chaine d'achat s'y enchaine sans orTimeout : un achat resterait suspendu, l'item
        // verrouille, le joueur sans reponse.
        //
        // En levant, l'echec devient synchrone et visible au site de soumission. Les services
        // testent deja isShuttingDown() a l'entree, donc ce chemin ne se produit en pratique
        // qu'au drainage de l'arret, ou l'abandon est le comportement voulu.
        throw rejection;
    }

    /**
     * Teste si le thread courant est un thread de tick.
     * <p>
     * {@code Server.isPrimaryThread()} est l'implementation utilisee partout ailleurs dans le
     * plugin ; sous Folia elle rend {@code true} pour TOUT thread de tick, region comprise, ce
     * qui est exactement la garde recherchee. En cas d'indisponibilite du serveur (chargement,
     * arret brutal) on repond {@code true} : la reponse conservatrice est celle qui n'execute
     * RIEN de bloquant sur un thread dont on ignore la nature.
     *
     * @return {@code true} si l'appelant ne doit en aucun cas executer d'IO bloquante
     */
    private boolean isServerTickThread() {
        try {
            var server = Bukkit.getServer();
            return server == null || server.isPrimaryThread();
        } catch (Throwable throwable) {
            return true;
        }
    }

    @Override
    public void onEnable() {

        var dataFolder = this.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        // Load language.yml first (not localized, always from root resources)
        this.saveLanguageFile();
        String configuredLanguage = this.loadLanguageConfiguration();

        // Initialize locale helper with configured language or automatic detection
        this.localeHelper = new LocaleHelper(getLogger(), configuredLanguage);

        // Now save config.yml with the correct language
        this.saveFile("config.yml", true);

        // Phase 1 du deploiement du marqueur de format (C-029) : la lecture accepte les deux
        // formats, l'ecriture reste sans marqueur tant que la cle n'est pas passee a true.
        Base64ItemStack.setWriteFormatMarker(getConfig().getBoolean("write-itemstack-format-marker", false));

        FoliaLib foliaLib = new FoliaLib(this);
        this.platformScheduler = foliaLib.getScheduler();

        // We must create the inventory class before loading the configuration, this allows using the zmenu interfaces everywhere.
        this.inventoriesLoader = new ZInventoriesLoader(this);

        // Register rule loaders BEFORE loading files (categories need them)
        this.ruleLoaderRegistry.registerDefaultLoaders();
        this.registerCustomItemLoaders();

        this.loadFiles();
        this.permissionRegistrar.register();

        this.auctionManager.setupSortedItemsCache();

        if (!this.storageManager.onEnable()) return;

        this.registerDefaultMigrationProviders();

        this.broadcastService = new BroadcastService(this);
        this.discordWebhookService = new DiscordWebhookService(this);

        this.chatSearchListener = new ChatSearchListener(this);
        this.addListener(new PlayerListener(this));
        this.addListener(this.chatSearchListener);

        java.util.List<String> aliases = new java.util.ArrayList<>(getConfig().getStringList("commands.main-command.aliases"));
        String primaryCommand;
        if (!aliases.isEmpty()) {
            primaryCommand = aliases.removeFirst();
            aliases.add("zauctionhouse");
        } else {
            primaryCommand = "zauctionhouse";
        }
        this.commandManager.registerCommand(this, primaryCommand, new CommandAuction(this), aliases);

        this.inventoriesLoader.load();

        // Le chargement devient fail-closed : on l'entoure explicitement plutot que de laisser
        // l'exception traverser onEnable, pour poser un message d'exploitation lisible. Sans ce
        // bloc, Bukkit desactive bien le plugin mais l'exploitant n'a aucun moyen de comprendre
        // que le refus de demarrer est DELIBERE (C-001).
        try {
            this.storageManager.loadItems();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Failed to load the auction items from the database. The plugin REFUSES "
                    + "to start with a partially loaded auction house: buyers would be charged full price for "
                    + "listings whose content could not be read.", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.maintenanceScheduler.start();

        this.registerPlaceholders();
        this.registerHooks();

        new Metrics(this, 5326);
        context.ready();

        if (getConfig().getBoolean("enable-version-checker", true)) {
            this.versionChecker = new VersionChecker(this, 1);
            this.versionChecker.useLastVersion();
        }

        var documentation = new DocumentationGenerator(this);
        documentation.generate(this.commandManager.getCommands(), ((LocalPlaceholder) placeholder).getAutoPlaceholders());

        getServer().getServicesManager().register(AuctionPlugin.class, this, this, ServicePriority.Highest);

        isEnabled = true;
        this.getLogger().info("zAuctionHouse has just been loaded successfully!");
    }

    @Override
    public void onDisable() {

        // TOUTE PREMIERE instruction : les services doivent cesser d'accepter de nouvelles
        // operations avant que quoi que ce soit ne soit ferme (C-022).
        this.shuttingDown = true;
        if (this.chatSearchListener != null) this.chatSearchListener.clear();

        // ZAuctionPlugin melangeait deux notions distinctes : le plugin a fini de demarrer
        // (isEnabled, positionne en toute derniere ligne de onEnable) et les ressources JVM
        // sont ouvertes (asyncExecutor, sortedItemsCache, connexion base). Les ressources
        // naissent AVANT le point de sortie anticipee : la garde historique
        // if (!isEnabled) return; sautait donc TOUT le teardown des qu'une exception survenait
        // apres l'ouverture de la base (migration Sarah, YAML d'inventaire invalide,
        // loadItems, hooks), laissant le pool Hikari et le ForkJoinPool vivants jusqu'au kill
        // du serveur (C-118). Seul le desenregistrement du VersionChecker reste conditionne au
        // demarrage complet.
        if (this.teardownDone) return;
        this.teardownDone = true;

        if (this.inventoriesLoader != null) {
            try {
                this.inventoriesLoader.getButtonManager().unregisters(this);
                this.inventoriesLoader.getInventoryManager().deleteInventories(this);
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Failed to unregister auction menus", exception);
            }
        }

        // Arret des balayages periodiques avant toute fermeture de ressource.
        this.maintenanceScheduler.stop();

        // Unregister version checker listener
        if (this.isEnabled && this.versionChecker != null) {
            this.versionChecker.unregister();
        }

        // Shutdown the sorted items cache (closes ForkJoinPool)
        this.auctionManager.shutdown();

        // Drain de l'executeur asynchrone. CET ORDRE EST CRITIQUE : le drain doit rester AVANT
        // la fermeture de la connexion, c'est lui qui garantit que les ecritures deja soumises
        // aboutissent au lieu de mourir sur une connexion fermee (C-022).
        this.asyncExecutor.shutdown();
        try {
            if (!this.asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.asyncExecutor.shutdownNow();
                if (!this.asyncExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    getLogger().warning("ExecutorService did not terminate properly");
                }
            }
        } catch (InterruptedException exception) {
            this.asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Fermeture de la connexion base, protegee par un test de nullite : storageManager
        // .onEnable() peut avoir echoue avant d'affecter databaseConnection, et onDisable est
        // alors invoque de facon REENTRANTE depuis l'interieur de onEnable (ZStorageManager
        // appelle disablePlugin alors que Bukkit a deja positionne isEnabled a true).
        try {
            if (this.storageManager.getDatabaseConnection() != null) {
                this.storageManager.onDisable();
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Failed to close the database connection", exception);
        }

        // SimpleContext.shutdown() commence par un test de son propre drapeau ready : c'est un
        // no-op tant que context.ready() n'a pas ete atteint. Aucun risque a le rendre
        // inconditionnel.
        this.context.shutdown();
    }

    @Override
    public void reload() {

        if (!new File(getDataFolder(), "config.yml").exists()) {
            this.saveFile("config.yml", true);
        }

        this.reloadConfig();

        // Le drapeau d'ecriture du marqueur de format peut avoir change dans config.yml.
        Base64ItemStack.setWriteFormatMarker(getConfig().getBoolean("write-itemstack-format-marker", false));

        // Re-initialize locale helper with configured language from language.yml
        String configuredLanguage = this.loadLanguageConfiguration();
        this.localeHelper = new LocaleHelper(getLogger(), configuredLanguage);

        this.loadFiles();
        this.permissionRegistrar.register();
        this.inventoriesLoader.reload();

        // Update economy references for all items after reload
        this.auctionManager.updateItemEconomies();

        // Les intervalles de maintenance ont pu changer dans config.yml.
        this.maintenanceScheduler.start();
    }

    private void loadFiles() {
        this.configuration.load(); // Load config.yml
        this.messageLoader.load(); // Load messages.yml
        this.economyManager.loadEconomies(); // Load economies.yml
        this.itemRuleManager.loadRules(); // Load rules.yml
        this.categoryManager.loadCategories(); // Load categories.yml

        if (this.discordWebhookService != null) {
            this.discordWebhookService.loadConfiguration(); // Load discord.yml
        }
    }

    private void registerPlaceholders() {
        DistantPlaceholder distantPlaceholder = new DistantPlaceholder(this, this.placeholder);
        distantPlaceholder.register();

        this.registerPlaceholder(PlayerPlaceholders.class);
        this.registerPlaceholder(GlobalPlaceholders.class);
        this.registerPlaceholder(OptionPlaceholders.class);
    }

    private void registerHooks() {

        if (isEnable(Plugins.LUCKPERMS)) {
            this.offlinePermission = new LuckPermsOfflinePermission();
            this.getLogger().info("LuckPerms has been enabled successfully!");
        }

        this.registerItemContentProviders();
    }

    private void registerItemContentProviders() {
        // Default vanilla shulker box provider (priority 100)
        this.itemContentManager.registerProvider(new VanillaShulkerContentProvider());

        if (isEnable(Plugins.AXSHULKERS)) {
            this.registerOptionalItemContentProvider("fr.maxlego08.zauctionhouse.hooks.axshulkers.AxShulkersContentProvider", "AxShulkers");
        }
    }

    private void registerOptionalItemContentProvider(String className, String name) {
        try {
            var clazz = Class.forName(className);
            var provider = (fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentProvider) clazz.getDeclaredConstructor().newInstance();
            this.itemContentManager.registerProvider(provider);
            this.getLogger().info(name + " item content provider registered.");
        } catch (Exception exception) {
            this.getLogger().log(Level.WARNING, "Failed to register " + name + " item content provider.", exception);
        }
    }

    private void registerCustomItemLoaders() {
        if (isEnable(Plugins.ITEMSADDER)) {
            this.ruleLoaderRegistry.registerItemsAdderLoader();
            this.getLogger().info("ItemsAdder rule loader registered.");
        }

        if (isEnable(Plugins.ORAXEN)) {
            this.ruleLoaderRegistry.registerOraxenLoader();
            this.getLogger().info("Oraxen rule loader registered.");
        }

        if (isEnable(Plugins.NEXO)) {
            this.ruleLoaderRegistry.registerNexoLoader();
            this.getLogger().info("Nexo rule loader registered.");
        }

        if (isEnable(Plugins.MMOITEMS)) {
            this.ruleLoaderRegistry.registerMMOItemsLoader();
            this.getLogger().info("MMOItems rule loader registered.");
        }

        if (isEnable(Plugins.EXECUTABLE_ITEMS)) {
            this.ruleLoaderRegistry.registerExecutableItemsLoader();
            this.getLogger().info("ExecutableItems rule loader registered.");
        }

        if (isEnable(Plugins.SLIMEFUN)) {
            this.ruleLoaderRegistry.registerSlimefunLoader();
            this.getLogger().info("Slimefun rule loader registered.");
        }

        if (isEnable(Plugins.HEADDATABASE)) {
            this.ruleLoaderRegistry.registerHeadDatabaseLoader();
            this.getLogger().info("HeadDatabase rule loader registered.");
        }

        if (isEnable(Plugins.NOVA)) {
            this.ruleLoaderRegistry.registerNovaLoader();
            this.getLogger().info("Nova rule loader registered.");
        }

        if (isEnable(Plugins.DENIZEN)) {
            this.ruleLoaderRegistry.registerDenizenLoader();
            this.getLogger().info("Denizen rule loader registered.");
        }

        if (isEnable(Plugins.CRAFTENGINE)) {
            this.ruleLoaderRegistry.registerCraftEngineLoader();
            this.getLogger().info("CraftEngine rule loader registered.");
        }

        if (isEnable(Plugins.EXECUTABLE_BLOCKS)) {
            this.ruleLoaderRegistry.registerExecutableBlocksLoader();
            this.getLogger().info("ExecutableBlocks rule loader registered.");
        }
    }

    private void registerDefaultMigrationProviders() {
        this.migrationRegistry.register(new V3MigrationProvider());
        this.registerOptionalMigrationProvider("fr.maxlego08.zauctionhouse.hooks.zelauction.ZelAuctionMigrationProvider", "ZelAuction");
        this.registerOptionalMigrationProvider("fr.maxlego08.zauctionhouse.hooks.donutauction.DonutAuctionMigrationProvider", "DonutAuction");
        this.registerOptionalMigrationProvider("fr.maxlego08.zauctionhouse.hooks.crazyauctions.CrazyAuctionsMigrationProvider", "CrazyAuctions");
    }

    /**
     * Registers a migration provider using reflection.
     * Used for optional hooks that may not be included in the build.
     *
     * @param className   The fully qualified class name
     * @param displayName The display name for logging
     */
    private void registerOptionalMigrationProvider(String className, String displayName) {
        try {
            Class<?> clazz = Class.forName(className);
            MigrationProvider provider = (MigrationProvider) clazz.getDeclaredConstructor().newInstance();
            this.migrationRegistry.register(provider);
            // this.getLogger().info(displayName + " migration provider registered.");
        } catch (ClassNotFoundException ignored) {
            // Hook not included in build, skip silently
        } catch (Exception exception) {
            this.getLogger().warning("Failed to register " + displayName + " migration provider: " + exception.getMessage());
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

    @Override
    public CommandManager getCommandManager() {
        return commandManager;
    }

    @Override
    public void sendMessage(org.bukkit.command.CommandSender sender, Message message, Object... args) {
        messageHelper.send(this, sender, message, args);
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
    public boolean isShuttingDown() {
        return this.shuttingDown;
    }

    /**
     * Ordonnanceur des taches de maintenance periodiques (rearmement des statuts de
     * confirmation, balayage des annonces arrivees a terme).
     *
     * @return l'ordonnanceur de maintenance, jamais {@code null}
     */
    public ZMaintenanceScheduler getMaintenanceScheduler() {
        return this.maintenanceScheduler;
    }

    @Override
    public AuctionClusterBridge getAuctionClusterBridge() {
        return this.auctionClusterBridge;
    }

    @Override
    public void setAuctionClusterBridge(AuctionClusterBridge auctionClusterBridge) {

        // SQLITE + bridge distribue = configuration IMPOSSIBLE. Chaque serveur a son PROPRE
        // fichier de base : les identifiants d'items se recoupent d'un serveur a l'autre, les
        // verrous du cluster portent sur des objets differents, et toute la protection
        // anti-duplication opere dans le vide. Les items SERONT dupliques (C-010).
        //
        // On NE fait PAS disablePlugin(zAuctionHouse) : couper l'hotel des ventes entier
        // priverait les joueurs d'items deja en base, alors que retomber en mono-serveur est
        // SUR ; et desactiver un plugin depuis le onEnable d'un AUTRE plugin, pendant que
        // Bukkit enumere ses plugins, est fragile.
        if (auctionClusterBridge != null && auctionClusterBridge.isDistributed() && isSqliteStorage()) {
            getLogger().severe("=========================================================================");
            getLogger().severe("A DISTRIBUTED cluster bridge (" + auctionClusterBridge.getClass().getSimpleName() + ") tried to install");
            getLogger().severe("itself while storage-type is SQLITE. Each server has its OWN database file:");
            getLogger().severe("item ids collide across servers and the cluster locks protect nothing.");
            getLogger().severe("The bridge is REFUSED. This server stays in single-server mode.");
            getLogger().severe("Fix: set storage-type to MYSQL/MARIADB with a SHARED database,");
            getLogger().severe("or remove the cluster addon.");
            getLogger().severe("=========================================================================");
            return;
        }

        var previous = this.auctionClusterBridge;
        this.auctionClusterBridge = auctionClusterBridge;

        // Le mode REELLEMENT en vigueur doit etre lisible dans la console de chaque noeud :
        // c'est le seul moyen de detecter un repli silencieux en mono-serveur (C-036).
        getLogger().info("Cluster bridge: " + (previous == null ? "none" : previous.getClass().getSimpleName())
                + " -> " + (auctionClusterBridge == null ? "none" : auctionClusterBridge.getClass().getSimpleName())
                + " (distributed=" + (auctionClusterBridge != null && auctionClusterBridge.isDistributed()) + ")");
    }

    /**
     * Indique si le stockage actif est un fichier SQLite local.
     *
     * @return {@code true} si la connexion est ouverte sur SQLITE
     */
    private boolean isSqliteStorage() {
        var connection = this.storageManager.getDatabaseConnection();
        return connection != null && connection.getDatabaseConfiguration().getDatabaseType() == DatabaseType.SQLITE;
    }

    @Override
    public ItemRuleManager getItemRuleManager() {
        return this.itemRuleManager;
    }

    @Override
    public CategoryManager getCategoryManager() {
        return this.categoryManager;
    }

    @Override
    public RuleLoaderRegistry getRuleLoaderRegistry() {
        return this.ruleLoaderRegistry;
    }

    @Override
    public MigrationRegistry getMigrationRegistry() {
        return this.migrationRegistry;
    }

    @Override
    public ItemContentManager getItemContentManager() {
        return this.itemContentManager;
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

    public ChatSearchListener getChatSearchListener() {
        return this.chatSearchListener;
    }

    public BroadcastService getBroadcastService() {
        return this.broadcastService;
    }

    public DiscordWebhookService getDiscordWebhookService() {
        return this.discordWebhookService;
    }

    /**
     * Gets the YAML updater that preserves comments when updating configuration files.
     *
     * @return The YamlUpdater instance
     */
    public YamlUpdater getYamlUpdater() {
        return this.yamlUpdater;
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

                if (outFile.exists() && !replace) {
                    getLogger().log(Level.WARNING, "Could not save " + outFile.getName() + " to " + outFile + " because " + outFile.getName() + " already exists.");
                } else {
                    try (OutputStream out = Files.newOutputStream(outFile.toPath()); in) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    } catch (IOException exception) {
                        getLogger().log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, exception);
                    }
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

        // Use the new YamlUpdater that preserves comments
        this.yamlUpdater.update(resourcePath, toPath);
    }

    /**
     * Saves the language.yml file from resources.
     * This file is NOT localized and is always loaded from the root resources.
     */
    private void saveLanguageFile() {
        File languageFile = new File(getDataFolder(), "language.yml");
        if (!languageFile.exists()) {
            this.saveResource("language.yml", "language.yml", false);
        } else {
            // Update with new keys while preserving user settings
            this.yamlUpdater.update("language.yml", "language.yml");
        }
    }

    /**
     * Loads the language configuration from language.yml.
     *
     * @return The configured language code, or null for auto-detection
     */
    private String loadLanguageConfiguration() {
        File languageFile = new File(getDataFolder(), "language.yml");
        if (!languageFile.exists()) {
            return null;
        }

        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(languageFile);
        String language = config.getString("language", "auto");

        if (language == null || language.equalsIgnoreCase("auto")) {
            return null; // Will trigger auto-detection in LocaleHelper
        }

        return language.toLowerCase();
    }

    @Override
    public void saveFile(String resourcePath, boolean saveOrUpdate) {
        this.saveFile(resourcePath, resourcePath, saveOrUpdate);
    }

    @Override
    public void saveFile(String resourcePath, String toPath, boolean saveOrUpdate) {
        var langResourcePath = localeHelper.getLanguage() + "/" + resourcePath;
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

    private static class MessageHelper extends fr.maxlego08.zauctionhouse.utils.MessageUtils {
        void send(AuctionPlugin plugin, org.bukkit.command.CommandSender sender, Message message, Object... args) {
            this.message(plugin, sender, message, args);
        }
    }
}