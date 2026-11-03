package fr.maxlego08.zauctionhouse.testutil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Base SQLite zAuctionHouse V3 FABRIQUEE, source des tests de migration V3 -> V4.
 * <p>
 * Le schema reproduit celui que Sarah creait pour la V3 ({@code CreateItemMigration},
 * {@code CreateTransactionMigration}, puis {@code UpdateItemAddServerNameMigration} et
 * {@code UpdateItemAddPriority}) : table {@code <prefix>items} a cle UUID texte et table
 * {@code <prefix>transactions} a cle auto-incrementee. Les colonnes sont toutes NULLables pour
 * pouvoir fabriquer des lignes corrompues (vendeur absent, charge utile vide...).
 * <p>
 * Une connexion JDBC est ouverte PAR OPERATION et refermee aussitot : sous Windows, une connexion
 * SQLite laissee ouverte verrouille le fichier et empeche JUnit de supprimer le dossier temporaire.
 */
public final class V3SourceDatabase {

    public static final String DEFAULT_PREFIX = "zauctionhouse_";

    private final File file;
    private final String prefix;
    private final boolean legacySchema;

    private V3SourceDatabase(File file, String prefix, boolean legacySchema) throws SQLException {
        this.file = file;
        this.prefix = prefix;
        this.legacySchema = legacySchema;
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }
        createSchema();
    }

    /**
     * Schema V3 complet (avec {@code server_name} et {@code priority}), prefixe par defaut.
     */
    public static V3SourceDatabase create(File file) throws SQLException {
        return new V3SourceDatabase(file, DEFAULT_PREFIX, false);
    }

    public static V3SourceDatabase create(File file, String prefix) throws SQLException {
        return new V3SourceDatabase(file, prefix, false);
    }

    /**
     * Schema d'une V3 jamais mise a jour : SANS les colonnes {@code server_name} et {@code priority}
     * ajoutees plus tard par {@code UpdateItemAddServerNameMigration} / {@code UpdateItemAddPriority}.
     */
    public static V3SourceDatabase createLegacy(File file) throws SQLException {
        return new V3SourceDatabase(file, DEFAULT_PREFIX, true);
    }

    /**
     * Chemin tel que {@code V3SqlDataReader} / {@code V3MigrationConfig} l'attendent ({@code jdbc:sqlite:<path>}).
     */
    public String path() {
        return this.file.getAbsolutePath();
    }

    public File file() {
        return this.file;
    }

    public String prefix() {
        return this.prefix;
    }

    public String itemsTable() {
        return this.prefix + "items";
    }

    public String transactionsTable() {
        return this.prefix + "transactions";
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + path());
    }

    private void createSchema() throws SQLException {
        String extraColumns = this.legacySchema ? "" : ", server_name VARCHAR(255), priority INTEGER DEFAULT 0";
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + itemsTable() + " ("
                    + "id VARCHAR(36) PRIMARY KEY, itemstack LONGTEXT, price BIGINT, seller VARCHAR(36), buyer VARCHAR(36), "
                    + "economy VARCHAR(255), auction_type VARCHAR(32), expire_at BIGINT, storage_type VARCHAR(32), sellerName VARCHAR(36)"
                    + extraColumns + ")");
            statement.execute("CREATE TABLE IF NOT EXISTS " + transactionsTable() + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, seller VARCHAR(36), buyer VARCHAR(36), itemstack LONGTEXT, "
                    + "transaction_date BIGINT, price BIGINT, economy VARCHAR(32), is_read BOOLEAN, need_money BOOLEAN)");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Lignes items
    // ------------------------------------------------------------------------------------------

    /**
     * Ligne de la table items V3, avec des valeurs par defaut realistes. Les identifiants sont des
     * CHAINES pour pouvoir fabriquer des UUID invalides.
     */
    public static final class ItemRow {
        public String id = UUID.randomUUID().toString();
        public String itemstack = "payload";
        public long price = 100L;
        public String seller;
        public String buyer;
        public String economy = "VAULT";
        public String auctionType = "DEFAULT";
        public long expireAt = 1_800_000_000_000L;
        public String storageType = "STORAGE";
        public String sellerName;
        public String serverName;
        public int priority = 0;

        public ItemRow id(String id) {
            this.id = id;
            return this;
        }

        public ItemRow id(UUID id) {
            return id(id.toString());
        }

        public ItemRow itemstack(String itemstack) {
            this.itemstack = itemstack;
            return this;
        }

        public ItemRow price(long price) {
            this.price = price;
            return this;
        }

        public ItemRow seller(UUID seller) {
            this.seller = seller == null ? null : seller.toString();
            return this;
        }

        public ItemRow seller(String seller) {
            this.seller = seller;
            return this;
        }

        public ItemRow buyer(UUID buyer) {
            this.buyer = buyer == null ? null : buyer.toString();
            return this;
        }

        public ItemRow buyer(String buyer) {
            this.buyer = buyer;
            return this;
        }

        public ItemRow economy(String economy) {
            this.economy = economy;
            return this;
        }

        public ItemRow auctionType(String auctionType) {
            this.auctionType = auctionType;
            return this;
        }

        public ItemRow expireAt(long expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        public ItemRow storageType(String storageType) {
            this.storageType = storageType;
            return this;
        }

        public ItemRow sellerName(String sellerName) {
            this.sellerName = sellerName;
            return this;
        }

        public ItemRow serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public ItemRow priority(int priority) {
            this.priority = priority;
            return this;
        }
    }

    public static ItemRow item(UUID seller, String sellerName) {
        return new ItemRow().seller(seller).sellerName(sellerName);
    }

    public V3SourceDatabase insert(ItemRow row) throws SQLException {
        String sql = this.legacySchema
                ? "INSERT INTO " + itemsTable() + " (id, itemstack, price, seller, buyer, economy, auction_type, expire_at, storage_type, sellerName) VALUES (?,?,?,?,?,?,?,?,?,?)"
                : "INSERT INTO " + itemsTable() + " (id, itemstack, price, seller, buyer, economy, auction_type, expire_at, storage_type, sellerName, server_name, priority) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.id);
            statement.setString(2, row.itemstack);
            statement.setLong(3, row.price);
            statement.setString(4, row.seller);
            statement.setString(5, row.buyer);
            statement.setString(6, row.economy);
            statement.setString(7, row.auctionType);
            statement.setLong(8, row.expireAt);
            statement.setString(9, row.storageType);
            statement.setString(10, row.sellerName);
            if (!this.legacySchema) {
                statement.setString(11, row.serverName);
                statement.setInt(12, row.priority);
            }
            statement.executeUpdate();
        }
        return this;
    }

    // ------------------------------------------------------------------------------------------
    // Lignes transactions
    // ------------------------------------------------------------------------------------------

    public static final class TransactionRow {
        public String seller;
        public String buyer;
        public String itemstack = "tx-payload";
        public long transactionDate = 1_600_000_000_000L;
        public long price = 50L;
        public String economy = "VAULT";
        public boolean isRead = false;
        public boolean needMoney = true;

        public TransactionRow seller(UUID seller) {
            this.seller = seller == null ? null : seller.toString();
            return this;
        }

        public TransactionRow seller(String seller) {
            this.seller = seller;
            return this;
        }

        public TransactionRow buyer(UUID buyer) {
            this.buyer = buyer == null ? null : buyer.toString();
            return this;
        }

        public TransactionRow buyer(String buyer) {
            this.buyer = buyer;
            return this;
        }

        public TransactionRow itemstack(String itemstack) {
            this.itemstack = itemstack;
            return this;
        }

        public TransactionRow date(long transactionDate) {
            this.transactionDate = transactionDate;
            return this;
        }

        public TransactionRow price(long price) {
            this.price = price;
            return this;
        }

        public TransactionRow economy(String economy) {
            this.economy = economy;
            return this;
        }

        public TransactionRow read(boolean isRead) {
            this.isRead = isRead;
            return this;
        }

        public TransactionRow needMoney(boolean needMoney) {
            this.needMoney = needMoney;
            return this;
        }
    }

    public static TransactionRow transaction(UUID seller, UUID buyer) {
        return new TransactionRow().seller(seller).buyer(buyer);
    }

    /**
     * @return l'identifiant auto-incremente de la ligne inseree
     */
    public int insert(TransactionRow row) throws SQLException {
        String sql = "INSERT INTO " + transactionsTable() + " (seller, buyer, itemstack, transaction_date, price, economy, is_read, need_money) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.seller);
            statement.setString(2, row.buyer);
            statement.setString(3, row.itemstack);
            statement.setLong(4, row.transactionDate);
            statement.setLong(5, row.price);
            statement.setString(6, row.economy);
            statement.setInt(7, row.isRead ? 1 : 0);
            statement.setInt(8, row.needMoney ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // SQL brut
    // ------------------------------------------------------------------------------------------

    public int execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            return statement.executeUpdate();
        }
    }

    public long count(String tableName) throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }
}
