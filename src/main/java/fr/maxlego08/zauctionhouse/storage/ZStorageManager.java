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
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.storage.migrations.*;
import fr.maxlego08.zauctionhouse.storage.repository.Repositories;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.*;
import fr.maxlego08.zauctionhouse.utils.ItemLoaderUtils;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ZStorageManager extends ItemLoaderUtils implements StorageManager {

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
            this.plugin.getLogger().info("The database connection is valid !");
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
        this.repositories.register(TransactionRepository.class);

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
    public void log(LogType logType, int itemId, Player player, UUID targetUniqueId, String itemstack, BigDecimal price, String economyName, String additionalData, Date readedAt) {
        async(() -> with(LogRepository.class).createLog(logType, itemId, player.getUniqueId(), targetUniqueId, itemstack, price, economyName, additionalData, readedAt));
    }

    @Override
    public void createTransaction(Item item, UUID playerUniqueId, String economyName, BigDecimal before, BigDecimal after, BigDecimal value, TransactionStatus status) {
        async(() -> with(TransactionRepository.class).create(item, playerUniqueId, economyName, before, after, value, status));
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
                    var auctionItem = createAuctionItem(this.plugin, dto, sellerName, auctionItems, optionalAuctionEconomy.get());

                    if (dto.buyer_unique_id() != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), with(PlayerRepository.class).select(dto.buyer_unique_id()));
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

    @Override
    public String getPlayerName(UUID uuid) {
        return this.with(PlayerRepository.class).select(uuid);
    }

    @Override
    public List<LogDTO> selectSalesHistory(UUID playerUniqueId) {
        return this.plugin.getStorageManager().with(LogRepository.class).selectSalesHistory(playerUniqueId);
    }

    @Override
    public List<Item> selectItems(List<Integer> integers) {

        if (integers.isEmpty()) return new ArrayList<>();

        var items = with(ItemRepository.class).select(integers.stream().map(String::valueOf).toList());
        if (items.isEmpty()) return new ArrayList<>();

        var uuids = items.stream().map(e -> List.of(e.seller_unique_id(), e.buyer_unique_id())).flatMap(List::stream).filter(Objects::nonNull).map(UUID::toString).distinct().toList();
        var playerNames = selectPlayers(uuids);

        var loadItems = new ArrayList<Item>();
        var performanceDebug = new PerformanceDebug(plugin);
        createItems(plugin, playerNames, items, performanceDebug, (a, item) -> loadItems.add(item));
        return loadItems;
    }

    @Override
    public Map<UUID, String> selectPlayers(List<String> uuids) {
        return with(PlayerRepository.class).select(uuids).stream().collect(Collectors.toMap(PlayerDTO::unique_id, PlayerDTO::name));
    }
}
