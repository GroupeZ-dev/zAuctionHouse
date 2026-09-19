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
import fr.maxlego08.zauctionhouse.api.storage.StaleItemException;
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
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ZStorageManager extends ItemLoaderUtils implements StorageManager {

    /**
     * Fenetre de grace avant qu'une reservation de vente soit consideree comme orpheline. Tres
     * au-dessus de la duree reelle d'une reservation (quelques ms) et de tout ecart d'horloge
     * raisonnable entre les noeuds d'un cluster : un noeud qui demarre ne peut donc jamais voler
     * la vente EN COURS d'un autre noeud.
     */
    private static final long ORPHAN_RESERVATION_GRACE_MS = 10L * 60L * 1000L;

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

        MigrationManager.setMigrationTableName("zauctionhousev4_migrations");
        MigrationManager.setDatabaseConfiguration(databaseConfiguration);

        MigrationManager.registerMigration(new CreatePlayerMigration());

        MigrationManager.registerMigration(new CreateItemMigration());
        MigrationManager.registerMigration(new CreateAuctionItemMigration());

        MigrationManager.registerMigration(new CreateTransactionsMigration());
        MigrationManager.registerMigration(new CreateLogsMigration());
        MigrationManager.registerMigration(new CreateOptionsMigration());

        // Sentinelle d'idempotence des migrations de donnees (C-034), enregistree APRES les
        // tables historiques pour ne pas perturber l'ordre deja applique sur les serveurs.
        MigrationManager.registerMigration(new CreateMigrationStateMigration());

        // Index, enregistres EN DERNIER pour ne pas perturber l'ordre historique des migrations
        // deja appliquees. UNE CLASSE PAR INDEX : Sarah emet CREATE INDEX sans IF NOT EXISTS et
        // enregistre la migration PAR SCHEMA, donc un echec au milieu d'un groupe condamnerait
        // definitivement les index suivants (C-066).
        MigrationManager.registerMigration(new CreateItemsStorageTypeIndexMigration());
        MigrationManager.registerMigration(new CreateTransactionsPlayerIndexMigration());
        MigrationManager.registerMigration(new CreateLogsCreatedAtIndexMigration());
        MigrationManager.registerMigration(new CreatePlayersNameIndexMigration());

        this.repositories = new Repositories(plugin, this.databaseConnection);
        this.repositories.register(PlayerRepository.class);
        this.repositories.register(ItemRepository.class);
        this.repositories.register(AuctionItemRepository.class);
        this.repositories.register(LogRepository.class);
        this.repositories.register(TransactionRepository.class);
        this.repositories.register(OptionRepository.class);
        // Sentinelle d'idempotence des migrations de donnees (C-034), enregistree EN DERNIER.
        this.repositories.register(MigrationStateRepository.class);

        MigrationManager.execute(this.databaseConnection, sarahLogger);

        // APRES les migrations (la colonne pending_publish doit exister) et AVANT loadItems() :
        // une reservation de vente laissee par un crash doit etre tranchee - rendue reclamable ou
        // supprimee, selon que le vendeur s'etait ou non deja dessaisi de son lot - avant que le
        // catalogue soit charge en memoire.
        recoverOrphanReservations();

        return true;
    }

    /**
     * Tranche le sort des reservations de vente qu'un crash a laissees non publiees.
     * <p>
     * Les deux abandons possibles d'une vente appellent des traitements OPPOSES, et
     * {@code pending_publish} est la seule chose qui les distingue :
     * <ul>
     *   <li>la vente etait ENGAGEE ({@code = 2}) : les items avaient deja quitte l'inventaire du
     *   vendeur, la ligne redevient reclamable, sinon le lot serait detruit ;</li>
     *   <li>la vente n'etait que RESERVEE ({@code = 1}) : le vendeur avait ENCORE son lot en main,
     *   la ligne est supprimee. La lui rendre lui donnerait un SECOND exemplaire.</li>
     * </ul>
     * Les deux cas sont journalises SEPAREMENT : le premier est un rattrapage normal (warning),
     * le second detruit des donnees en base et doit rester tracable (severe).
     * <p>
     * Ne peut JAMAIS empecher le demarrage : {@code ItemRepository.recoverOrphanReservations}
     * leve une {@link IllegalStateException} en cas d'echec SQL, et une tache de rattrapage
     * n'est pas une raison de refuser de demarrer.
     */
    private void recoverOrphanReservations() {
        try {
            var sweep = with(ItemRepository.class).recoverOrphanReservations(System.currentTimeMillis() - ORPHAN_RESERVATION_GRACE_MS);

            if (sweep.recovered() > 0) {
                this.plugin.getLogger().warning("[ZAH] " + sweep.recovered() + " listing reservation(s) were committed"
                        + " (the seller had already handed over his items) but never published, most likely a server"
                        + " crash during a sale. They have been made claimable by their seller in the 'expired items'"
                        + " section.");
            }

            if (!sweep.purged().isEmpty()) {
                this.plugin.getLogger().severe("[ZAH] " + sweep.purged().size() + " listing reservation(s) " + sweep.purged()
                        + " died BEFORE the seller handed over his items. They have been DELETED with their contents:"
                        + " the seller still holds those stacks in his inventory, handing them back would have"
                        + " DUPLICATED them.");
            }
        } catch (Throwable throwable) {
            this.plugin.getLogger().severe("[ZAH] Unable to recover orphan listing reservations: " + throwable);
        }
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

    @Override
    public DatabaseConnection getDatabaseConnection() {
        return this.databaseConnection;
    }

    /**
     * Poste une ecriture differee sur l'executor du plugin.
     * <p>
     * Avant : {@code plugin.getScheduler().runAsync(...)}, un ordonnanceur que rien ne draine a
     * l'arret alors que {@link #onDisable()} ferme la connexion base juste apres. La ligne
     * {@code transactions} PENDING du vendeur - la dette d'un achat deja encaisse - etait donc
     * perdue a chaque /stop sous charge (C-027). L'executor du plugin est le SEUL pool draine
     * avant la fermeture de la connexion.
     * <p>
     * Le future rendu ne sert aujourd'hui qu'a journaliser l'echec ; le chainer dans le future
     * d'achat releve du chantier 3.
     *
     * @param runnable the blocking write to run off the main thread
     * @return a future completing when the write finished, never failing
     */
    protected CompletableFuture<Void> async(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, this.plugin.getExecutorService()).exceptionally(throwable -> {
            this.plugin.getLogger().log(Level.SEVERE, "Deferred storage write failed", throwable);
            return null;
        });
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
    @SuppressWarnings("deprecation") // AuctionItemRepository.create sans charges utiles pre-encodees.
    public CompletableFuture<AuctionItem> createAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(seller, ItemType.AUCTION, price, expiredAt, auctionEconomy);
            return with(AuctionItemRepository.class).create(seller, itemId, price, expiredAt, itemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
    }

    @Override
    @SuppressWarnings("deprecation") // AuctionItemRepository.create sans charges utiles pre-encodees.
    public CompletableFuture<AuctionItem> createAuctionItem(UUID sellerUniqueId, String sellerName, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(sellerUniqueId, ItemType.AUCTION, price, expiredAt, auctionEconomy);
            return with(AuctionItemRepository.class).create(sellerUniqueId, sellerName, itemId, price, expiredAt, itemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
    }

    /**
     * Reserve une annonce : la ligne parente est creee en DELETED avec {@code pending_publish = 1}
     * et les contenus sont inseres, mais l'annonce n'est visible d'AUCUN serveur tant que
     * {@link #publishAuctionItem(AuctionItem)} n'a pas eu lieu.
     * <p>
     * C'est ce qui ferme la duplication de la vente : si la verification d'inventaire echoue
     * apres la reservation, l'annonce n'a jamais existe pour personne et le joueur garde son lot.
     *
     * @param seller            the selling player
     * @param price             listing price
     * @param expiredAt         expiration timestamp in milliseconds
     * @param itemStacks        the listing content
     * @param encodedItemStacks the SAME content, already encoded and validated
     * @param auctionEconomy    economy used by the listing
     * @return the reserved, NOT yet published, auction item
     */
    @Override
    public CompletableFuture<AuctionItem> reserveAuctionItem(Player seller, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, List<String> encodedItemStacks, AuctionEconomy auctionEconomy) {
        return CompletableFuture.supplyAsync(() -> {
            int itemId = with(ItemRepository.class).create(seller.getUniqueId(), ItemType.AUCTION, price, expiredAt, auctionEconomy, StorageType.DELETED);
            // insertSync rend 0 quand le pilote ne remonte aucune cle generee : la vente doit
            // echouer proprement plutot que d'inserer des contenus orphelins sur l'item 0.
            if (itemId <= 0) throw new IllegalStateException("Unable to reserve a listing row (generated id = " + itemId + ")");
            return with(AuctionItemRepository.class).create(seller.getUniqueId(), seller.getName(), itemId, price, expiredAt, itemStacks, encodedItemStacks, auctionEconomy);
        }, this.plugin.getExecutorService());
    }

    /**
     * Rend visible une annonce ENGAGEE (DELETED vers LISTED, en compare-and-set).
     * <p>
     * Ne publie qu'une reservation dont le jalon {@code pending_publish = 2} a ete pose, donc dont
     * les items ont deja quitte l'inventaire du vendeur.
     *
     * @param auctionItem the committed reservation
     * @return 1 = publiee, 0 = la reservation n'existe plus ou n'est pas engagee
     */
    @Override
    public CompletableFuture<Integer> publishAuctionItem(AuctionItem auctionItem) {
        return CompletableFuture.supplyAsync(() -> with(ItemRepository.class).publishListing(auctionItem.getId()), this.plugin.getExecutorService());
    }

    /**
     * Supprime une reservation NON ENGAGEE qui n'a pas pu aboutir, contenus compris.
     * <p>
     * Ne detruit QUE les lignes portant {@code pending_publish = 1}, c'est-a-dire celles dont le
     * vendeur a encore le lot dans son inventaire. Une reservation engagee - le vendeur s'en est
     * dessaisi - n'est jamais supprimee ici : l'appelant doit d'abord reculer le jalon
     * ({@code markReservationUncommitted}) APRES avoir rendu le lot, sans quoi la ligne survit et
     * reste reclamable.
     * <p>
     * Un compte rendu different de 1 est journalise mais n'echoue pas : la ligne est alors soit
     * deja partie, soit engagee, et dans les deux cas la detruire serait pire.
     *
     * @param auctionItem the reservation to drop
     * @return future completing when the reservation is gone
     */
    @Override
    public CompletableFuture<Void> cancelReservation(AuctionItem auctionItem) {
        return CompletableFuture.runAsync(() -> {
            int deleted = with(ItemRepository.class).deleteReservation(auctionItem.getId());
            if (deleted != 1) {
                this.plugin.getLogger().warning("[ZAH] Reservation " + auctionItem.getId()
                        + " was already gone (or no longer cancellable) when cancelling.");
            }
        }, this.plugin.getExecutorService());
    }

    @Override
    @SuppressWarnings("deprecation") // ItemRepository.updateItem(Item, StorageType) : ecriture sans garde de transition.
    public CompletableFuture<Void> updateItem(Item item, StorageType storageType) {
        return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItem(item, storageType), this.plugin.getExecutorService());
    }

    /**
     * Ecriture compare-and-set : la ligne ne bouge QUE si elle porte encore {@code from}.
     * <p>
     * Le future echoue avec {@link StaleItemException} quand aucune ligne n'a bouge : l'appelant a
     * perdu la course, il ne doit ni rendre l'item, ni deplacer d'argent, ni restaurer un statut du
     * cycle LISTED - seulement purger sa copie memoire.
     *
     * @param item item to move
     * @param from storage bucket the row is expected to still carry
     * @param to   destination storage bucket
     * @return future completing when the update is persisted
     */
    @Override
    public CompletableFuture<Void> updateItem(Item item, StorageType from, StorageType to) {
        return CompletableFuture.runAsync(() -> {
            int rows = with(ItemRepository.class).updateItem(item, from, to);
            if (rows == 0) throw new StaleItemException(item.getId(), from, to);
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<Void> updateItems(Map<StorageType, List<Item>> itemsByStorageType) {
        return CompletableFuture.runAsync(() -> with(ItemRepository.class).updateItems(itemsByStorageType), this.plugin.getExecutorService());
    }

    /**
     * Batch compare-and-set.
     *
     * @param itemsByStorageType destination bucket to items to move
     * @param from               storage bucket all these rows are expected to still carry
     * @return future completing with the ids that LOST the race (empty when everything moved)
     */
    @Override
    public CompletableFuture<List<Integer>> updateItems(Map<StorageType, List<Item>> itemsByStorageType, StorageType from) {
        return CompletableFuture.supplyAsync(() -> with(ItemRepository.class).updateItems(itemsByStorageType, from), this.plugin.getExecutorService());
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
        // CONTRAT HISTORIQUE CONSERVE : null = "introuvable OU non reconstructible".
        // Les 4 sites d'appel de l'addon Redis (ItemListedListener, ItemBoughtListener,
        // ItemRemovedListener) sont compiles contre un SHA fige de l'API et en dependent.
        // Tout NOUVEL appelant doit utiliser selectItemState (C-109).
        return selectItemState(id).thenApply(result -> result.isFound() ? result.item() : null);
    }

    @Override
    public CompletableFuture<ItemLookupResult> selectItemState(int id) {
        // Executor explicite : sans lui, ce JDBC bloquant (3 requetes) partait sur
        // ForkJoinPool.commonPool, deja sature par les appels Jedis du bridge, et faisait
        // basculer toute la chaine d'achat hors du thread principal, y compris en mono-serveur
        // (C-077 / C-089). C'est aussi la cause des faux "Unable to find the item" de l'addon.
        return CompletableFuture.supplyAsync(() -> {

            var optional = with(ItemRepository.class).select(id);
            if (optional.isEmpty()) return ItemLookupResult.gone();

            var dto = optional.get();

            var sellerName = with(PlayerRepository.class).select(dto.seller_unique_id());

            var optionalAuctionEconomy = this.plugin.getEconomyManager().getEconomy(dto.economy_name());
            if (optionalAuctionEconomy.isEmpty()) {
                this.plugin.getLogger().severe("Impossible to find the economy " + dto.economy_name()
                        + " for auction item id " + dto.id() + ". The item is NOT gone, it is UNREADABLE: "
                        + "check economies.yml before assuming the listing disappeared.");
                return ItemLookupResult.unavailable();
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var auctionItems = with(AuctionItemRepository.class).select(List.of(String.valueOf(dto.id())));
                    var auctionItem = createAuctionItem(this.plugin, dto, sellerName, auctionItems, optionalAuctionEconomy.get());

                    // createAuctionItem rend desormais null en cas de QUARANTAINE (C-001 / C-011).
                    // Cette garde n'est PAS optionnelle : setBuyer partirait en NPE, et la
                    // revalidation sous verrou d'achat avec elle. UNAVAILABLE et non GONE : la
                    // ligne existe, l'appelant ne doit surtout pas purger sa copie memoire.
                    if (auctionItem == null) return ItemLookupResult.unavailable();

                    if (dto.buyer_unique_id() != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), with(PlayerRepository.class).select(dto.buyer_unique_id()));
                    }

                    return ItemLookupResult.found(auctionItem);
                }
                case BID, RENT -> {
                    this.plugin.getLogger().severe("Item type " + dto.item_type() + " is not implemented (item id " + dto.id() + ")");
                    return ItemLookupResult.unavailable();
                }
            }
            return ItemLookupResult.unavailable();
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<UUID> findUniqueId(String playerName) {
        // Second et dernier supplyAsync orphelin du fichier (C-077).
        return CompletableFuture.supplyAsync(() -> this.with(PlayerRepository.class).selectByName(playerName), this.plugin.getExecutorService());
    }

    @Override
    public String getPlayerName(UUID uuid) {
        return this.with(PlayerRepository.class).select(uuid);
    }

    @Override
    public List<LogDTO> selectSalesHistory(UUID playerUniqueId, long expireAfterMs) {
        return this.plugin.getStorageManager().with(LogRepository.class).selectSalesHistory(playerUniqueId, expireAfterMs);
    }

    @Override
    public List<Item> selectItems(List<Integer> integers) {

        if (integers.isEmpty()) return new ArrayList<>();

        var items = with(ItemRepository.class).select(integers.stream().map(String::valueOf).toList());
        if (items.isEmpty()) return new ArrayList<>();

        var uuids = items.stream().flatMap(e -> java.util.stream.Stream.of(e.seller_unique_id(), e.buyer_unique_id())).filter(Objects::nonNull).map(UUID::toString).distinct().toList();
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

    @Override
    public void markPurchaseLogAsRead(int itemId, UUID sellerUniqueId) {
        async(() -> with(LogRepository.class).markPurchaseLogsAsReadByItem(itemId, sellerUniqueId));
    }
}
