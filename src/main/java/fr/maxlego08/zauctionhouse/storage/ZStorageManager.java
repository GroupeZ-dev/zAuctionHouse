package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.sarah.*;
import fr.maxlego08.sarah.database.DatabaseType;
import fr.maxlego08.sarah.logger.JULogger;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.storage.migrations.*;
import fr.maxlego08.zauctionhouse.storage.repository.Repositories;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.LogRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.PlayerRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ZStorageManager implements StorageManager {

    private final AuctionPlugin plugin;
    private AuctionLoader auctionLoader;
    private Repositories repositories;
    private DatabaseConnection databaseConnection;

    public ZStorageManager(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onEnable() {

        var sarahLogger = JULogger.from(this.plugin.getLogger());
        var databaseConfiguration = this.getDatabaseConfiguration();
        var isSqlite = databaseConfiguration.getDatabaseType() == DatabaseType.SQLITE;
        this.databaseConnection = isSqlite ? new SqliteConnection(databaseConfiguration, this.plugin.getDataFolder(), sarahLogger) : new HikariDatabaseConnection(databaseConfiguration, sarahLogger);

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

        MigrationManager.registerMigration(new CreateItemMigration());
        MigrationManager.registerMigration(new CreateAuctionItemMigration());

        MigrationManager.registerMigration(new CreateTransactionsMigration());
        MigrationManager.registerMigration(new CreateLogsMigration());

        this.repositories = new Repositories(plugin, this.databaseConnection);
        this.repositories.register(PlayerRepository.class);
        this.repositories.register(ItemRepository.class);
        this.repositories.register(AuctionItemRepository.class);
        this.repositories.register(LogRepository.class);

        MigrationManager.execute(this.databaseConnection, sarahLogger);

        return true;
    }

    @Override
    public void onDisable() {
        this.databaseConnection.disconnect();
    }

    @Override
    public void loadItems() {
        this.auctionLoader = new AuctionLoader(plugin, this);
        this.auctionLoader.loadItems();
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
    public void upsertPlayer(UUID uniqueId, String name) {
        async(() -> with(PlayerRepository.class).upsertPlayer(uniqueId, name));
    }

    @Override
    public CompletableFuture<AuctionItem> createAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
            return with(AuctionItemRepository.class).create(seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<AuctionItem> createAuctionItem(UUID sellerUniqueId, String sellerName, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(sellerUniqueId, ItemType.AUCTION, price, expiredAt, auctionEconomy);
            return with(AuctionItemRepository.class).create(sellerUniqueId, sellerName, itemId, price, expiredAt, itemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<Void> updateItem(Item item, StorageType storageType) {
        return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItem(item, storageType), this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<Void> updateItems(Map<StorageType, List<Item>> itemsByStorageType) {
        return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItems(itemsByStorageType), this.plugin.getExecutorService());
    }

    @Override
    public void log(LogType logType, int itemId, Player player, UUID targetUniqueId, BigDecimal price, String economyName, String additionalData) {
        async(() -> with(LogRepository.class).createLog(logType, itemId, player.getUniqueId(), targetUniqueId, price, economyName, additionalData));
    }

    @Override
    public CompletableFuture<Item> selectItem(int id) {
        return CompletableFuture.supplyAsync(() -> {

            var optional = with(ItemRepository.class).select(id);
            if (optional.isEmpty()) return null;

            var dto = optional.get();

            var sellerName = with(PlayerRepository.class).select(dto.seller_unique_id());

            var optionalAuctionEconomy = this.plugin.getEconomyManager().getEconomy(dto.economy_name());
            if (optionalAuctionEconomy.isEmpty()) {
                this.plugin.getLogger().severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                return null;
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var auctionItems = with(AuctionItemRepository.class).select(List.of(String.valueOf(dto.id())));
                    var auctionItem = this.auctionLoader.createAuctionItem(dto, sellerName, auctionItems, optionalAuctionEconomy.get());

                    if (dto.buyer_unique_id() != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), with(PlayerRepository.class).select(dto.seller_unique_id()));
                    }

                    return auctionItem;
                }
                case BID -> {
                }
                case RENT -> {
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<UUID> findUniqueId(String playerName) {
        return CompletableFuture.supplyAsync(() -> this.with(PlayerRepository.class).selectByName(playerName));
    }
}
