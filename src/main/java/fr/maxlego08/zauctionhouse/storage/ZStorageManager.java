package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.sarah.DatabaseConfiguration;
import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.HikariDatabaseConnection;
import fr.maxlego08.sarah.MigrationManager;
import fr.maxlego08.sarah.SqliteConnection;
import fr.maxlego08.sarah.database.DatabaseType;
import fr.maxlego08.sarah.logger.JULogger;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateAuctionItemMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateLogsMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreatePlayerMigration;
import fr.maxlego08.zauctionhouse.storage.repository.Repositories;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.PlayerRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ZStorageManager implements StorageManager {

    private final AuctionPlugin plugin;
    private Repositories repositories;
    private DatabaseConnection databaseConnection;

    public ZStorageManager(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onEnable() {

        var databaseConfiguration = this.getDatabaseConfiguration();
        var isSqlite = databaseConfiguration.getDatabaseType() == DatabaseType.SQLITE;
        this.databaseConnection = isSqlite ? new SqliteConnection(databaseConfiguration, this.plugin.getDataFolder()) : new HikariDatabaseConnection(databaseConfiguration);

        if (!databaseConnection.isValid()) {

            this.plugin.getLogger().severe("Unable to connect to database !");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return false;
        } else {
            this.plugin.getLogger().info("The database connection is valid ! " + (isSqlite ? "(SQLITE)" : "(" + this.databaseConnection.getDatabaseConfiguration().getHost() + ")"));
        }

        MigrationManager.setMigrationTableName("zauctionhouse_migrations");
        MigrationManager.setDatabaseConfiguration(databaseConfiguration);

        MigrationManager.registerMigration(new CreatePlayerMigration());
        MigrationManager.registerMigration(new CreateLogsMigration());
        MigrationManager.registerMigration(new CreateAuctionItemMigration());

        this.repositories = new Repositories(plugin, this.databaseConnection);
        this.repositories.register(PlayerRepository.class);
        this.repositories.register(AuctionItemRepository.class);

        MigrationManager.execute(this.databaseConnection, JULogger.from(this.plugin.getLogger()));

        return true;
    }

    @Override
    public void onDisable() {
        this.databaseConnection.disconnect();
    }

    @Override
    public void loadItems() {

        var players = with(PlayerRepository.class).select().stream().collect(Collectors.toMap(PlayerDTO::unique_id, PlayerDTO::name));
        this.plugin.getLogger().info("Loaded " + players.size() + " players successfully");

        AuctionLoader auctionLoader = new AuctionLoader(this.plugin, this, players);
        auctionLoader.loadItems();
    }

    @Override
    public <T extends Repository> T with(Class<T> module) {
        return this.repositories.getTable(module);
    }

    protected void async(Runnable runnable) {
        this.plugin.getScheduler().runAsync(wrappedTask -> runnable.run());
    }

    private @NotNull DatabaseConfiguration getDatabaseConfiguration() {

        var config = this.plugin.getConfig();
        var storageType = DatabaseType.valueOf(config.getString("storage-type", "SQLITE").toUpperCase());

        GlobalDatabaseConfiguration globalDatabaseConfiguration = new GlobalDatabaseConfiguration(config);

        String tablePrefix = globalDatabaseConfiguration.getTablePrefix();
        String host = globalDatabaseConfiguration.getHost();
        int port = globalDatabaseConfiguration.getPort();
        String user = globalDatabaseConfiguration.getUser();
        String password = globalDatabaseConfiguration.getPassword();
        String database = globalDatabaseConfiguration.getDatabase();
        boolean debug = globalDatabaseConfiguration.isDebug();

        return new DatabaseConfiguration(tablePrefix, user, password, port, host, database, debug, storageType);
    }

    @Override
    public void upsertPlayer(Player player) {
        async(() -> with(PlayerRepository.class).upsertPlayer(player));
    }

    @Override
    public CompletableFuture<AuctionItem> createAuctionItem(Player seller, BigDecimal price, long expiredAt, ItemStack clonedItemStack, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> with(AuctionItemRepository.class).create(seller, price, expiredAt, clonedItemStack, auctionEconomy), this.plugin.getExecutorService());
    }

    @Override
    public void updateItem(Item item, StorageType storageType) {
        if (item instanceof AuctionItem){
            async(() -> with(AuctionItemRepository.class).updateItem(item, storageType));
        } else this.plugin.getLogger().severe("Not implemented (updateItem)");
    }
}
