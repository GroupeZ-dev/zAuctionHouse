package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.sarah.DatabaseConfiguration;
import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.HikariDatabaseConnection;
import fr.maxlego08.sarah.MigrationManager;
import fr.maxlego08.sarah.SqliteConnection;
import fr.maxlego08.sarah.database.DatabaseType;
import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.sarah.logger.JULogger;
import fr.maxlego08.sarah.logger.Logger;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateAuctionItemMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateItemMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateItemsStorageTypeIndexMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateLogsCreatedAtIndexMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateLogsMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateMigrationStateMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateOptionsMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreatePlayerMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreatePlayersNameIndexMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateTransactionsMigration;
import fr.maxlego08.zauctionhouse.storage.migrations.CreateTransactionsPlayerIndexMigration;
import fr.maxlego08.zauctionhouse.storage.repository.Repositories;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.OptionRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Socle des tests SQL : une base REELLE, migree avec les migrations de production, et les
 * repositories enregistres exactement comme le fait {@code ZStorageManager.onEnable}.
 * <p>
 * Backend par defaut : SQLite dans un dossier temporaire JUnit (une base neuve par test).
 * Pour executer la meme suite contre un serveur :
 * <pre>
 * gradlew test -Dzah.test.db=MYSQL -Dzah.test.host=127.0.0.1 -Dzah.test.port=3306 \
 *              -Dzah.test.user=root -Dzah.test.password=secret -Dzah.test.database=zah_test
 * </pre>
 * Sur un serveur, les tables prefixees {@link #TABLE_PREFIX} sont supprimees apres chaque test.
 * <p>
 * {@code MigrationManager} est entierement STATIQUE : la liste des migrations est videe puis
 * reconstruite a chaque test, et les tests ne doivent pas s'executer en parallele dans la meme
 * JVM (voir {@code maxParallelForks = 1} dans le build).
 */
public abstract class DatabaseTestBase {

    public static final String TABLE_PREFIX = "zah_test_";
    public static final String MIGRATION_TABLE = "zauctionhousev4_migrations";

    @TempDir
    protected File dataFolder;

    protected AuctionPlugin plugin;
    protected AuctionEconomy economy;
    protected DatabaseConfiguration databaseConfiguration;
    protected DatabaseConnection connection;
    protected Repositories repositories;
    protected Logger sarahLogger;

    @BeforeEach
    void setUpDatabase() throws Exception {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.economy = this.plugin.getEconomyManager().getEconomy("vault").orElseThrow();
        this.sarahLogger = JULogger.from(this.plugin.getLogger());

        this.databaseConfiguration = createConfiguration();
        this.connection = createConnection(this.databaseConfiguration);
        this.connection.connect();
        if (!this.connection.isValid()) {
            throw new IllegalStateException("Unable to open the test database (" + this.databaseConfiguration.getDatabaseType() + ")");
        }

        // Sur un serveur partage, repartir d'une base propre meme si un test precedent a plante.
        if (!isSqlite()) dropAllTables();

        registerAndRunMigrations();

        this.repositories = new Repositories(this.plugin, this.connection);
        this.repositories.register(PlayerRepository.class);
        this.repositories.register(ItemRepository.class);
        this.repositories.register(AuctionItemRepository.class);
        this.repositories.register(LogRepository.class);
        this.repositories.register(TransactionRepository.class);
        this.repositories.register(OptionRepository.class);
        this.repositories.register(MigrationStateRepository.class);

        afterDatabaseReady();
    }

    @AfterEach
    void tearDownDatabase() throws Exception {
        try {
            if (this.connection != null && !isSqlite()) dropAllTables();
        } finally {
            if (this.connection != null) this.connection.disconnect();
            resetMigrationManager();
        }
    }

    /**
     * Remet a zero l'etat STATIQUE de {@code MigrationManager}.
     * <p>
     * Le jar Sarah 1.27 ne vide pas sa liste privee {@code schemas} au debut de
     * {@code execute()} : les schemas enregistres par le test precedent seraient rejoues sur la
     * base neuve, et le second {@code CREATE INDEX} (emis sans IF NOT EXISTS) echouerait.
     * {@code getMigrations()} rend la liste vivante, mais {@code schemas} n'est accessible que par
     * reflexion.
     */
    public static void resetMigrationManager() {
        MigrationManager.getMigrations().clear();
        clearStaticList(MigrationManager.class, "schemas");
        clearStaticList(MigrationManager.class, "migrations");
    }

    private static void clearStaticList(Class<?> owner, String fieldName) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof List<?> list) list.clear();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to reset MigrationManager." + fieldName, exception);
        }
    }

    /**
     * Point d'extension appele une fois la base migree et les repositories enregistres.
     */
    protected void afterDatabaseReady() throws Exception {
    }

    /**
     * Enregistre et execute les migrations de production, dans l'ORDRE de {@code ZStorageManager}.
     * Reutilisable par les tests d'idempotence (second passage sur la meme base).
     */
    protected void registerAndRunMigrations() {
        MigrationManager.setMigrationTableName(MIGRATION_TABLE);
        MigrationManager.setDatabaseConfiguration(this.databaseConfiguration);
        resetMigrationManager();
        for (Migration migration : productionMigrations()) {
            MigrationManager.registerMigration(migration);
        }
        MigrationManager.execute(this.connection, this.sarahLogger);
    }

    /**
     * La liste des migrations de production, dans l'ordre exact d'enregistrement de
     * {@code ZStorageManager.onEnable}. Une migration oubliee ici serait un test faux.
     */
    protected List<Migration> productionMigrations() {
        return List.of(
                new CreatePlayerMigration(),
                new CreateItemMigration(),
                new CreateAuctionItemMigration(),
                new CreateTransactionsMigration(),
                new CreateLogsMigration(),
                new CreateOptionsMigration(),
                new CreateMigrationStateMigration(),
                new CreateItemsStorageTypeIndexMigration(),
                new CreateTransactionsPlayerIndexMigration(),
                new CreateLogsCreatedAtIndexMigration(),
                new CreatePlayersNameIndexMigration()
        );
    }

    protected DatabaseConfiguration createConfiguration() {
        String type = System.getProperty("zah.test.db", "SQLITE").toUpperCase();
        DatabaseType databaseType = DatabaseType.valueOf(type);
        if (databaseType == DatabaseType.SQLITE) {
            return new DatabaseConfiguration(TABLE_PREFIX, "", "", 0, "", "", Boolean.getBoolean("zah.test.sqldebug"), DatabaseType.SQLITE);
        }
        return new DatabaseConfiguration(TABLE_PREFIX,
                System.getProperty("zah.test.user", "root"),
                System.getProperty("zah.test.password", ""),
                Integer.parseInt(System.getProperty("zah.test.port", "3306")),
                System.getProperty("zah.test.host", "127.0.0.1"),
                System.getProperty("zah.test.database", "zah_test"),
                false, databaseType);
    }

    protected DatabaseConnection createConnection(DatabaseConfiguration configuration) {
        if (configuration.getDatabaseType() == DatabaseType.SQLITE) {
            return new SqliteConnection(configuration, this.dataFolder, this.sarahLogger);
        }
        return new HikariDatabaseConnection(configuration, this.sarahLogger);
    }

    protected boolean isSqlite() {
        return this.databaseConfiguration.getDatabaseType() == DatabaseType.SQLITE;
    }

    // ------------------------------------------------------------------------------------------
    // Acces aux repositories
    // ------------------------------------------------------------------------------------------

    protected <T extends Repository> T repo(Class<T> type) {
        return this.repositories.getTable(type);
    }

    protected PlayerRepository players() {
        return repo(PlayerRepository.class);
    }

    protected ItemRepository items() {
        return repo(ItemRepository.class);
    }

    protected AuctionItemRepository auctionItems() {
        return repo(AuctionItemRepository.class);
    }

    protected TransactionRepository transactions() {
        return repo(TransactionRepository.class);
    }

    protected LogRepository logs() {
        return repo(LogRepository.class);
    }

    protected OptionRepository options() {
        return repo(OptionRepository.class);
    }

    protected MigrationStateRepository migrationState() {
        return repo(MigrationStateRepository.class);
    }

    /**
     * Insere un joueur et rend son UUID (deterministe a partir du nom).
     */
    protected UUID newPlayer(String name) {
        UUID uniqueId = UUID.nameUUIDFromBytes(name.getBytes());
        players().upsertPlayer(uniqueId, name);
        return uniqueId;
    }

    // ------------------------------------------------------------------------------------------
    // Acces SQL brut : pour VERIFIER l'etat reel des lignes, independamment des repositories
    // ------------------------------------------------------------------------------------------

    /**
     * Remplace {@code %prefix%} par le prefixe de test : accepte les constantes de {@code Tables}.
     */
    protected String table(String tableName) {
        return tableName.replace("%prefix%", TABLE_PREFIX);
    }

    /**
     * Ouvre une NOUVELLE connexion JDBC : Sarah en ouvre une par requete et la ferme aussitot.
     * L'appelant DOIT la fermer (try-with-resources), sinon le fichier SQLite reste verrouille
     * sous Windows et le dossier temporaire JUnit ne peut plus etre supprime.
     */
    protected Connection jdbc() {
        return this.connection.getConnection();
    }

    protected int execute(String sql, Object... parameters) throws SQLException {
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(table(sql))) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    protected long count(String tableName) throws SQLException {
        return queryLong("SELECT COUNT(*) FROM " + tableName);
    }

    protected long count(String tableName, String whereClause, Object... parameters) throws SQLException {
        return queryLong("SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause, parameters);
    }

    protected long queryLong(String sql, Object... parameters) throws SQLException {
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(table(sql))) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    protected String queryString(String sql, Object... parameters) throws SQLException {
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(table(sql))) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    protected Object queryObject(String sql, Object... parameters) throws SQLException {
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(table(sql))) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getObject(1) : null;
            }
        }
    }

    protected List<Object[]> queryRows(String sql, Object... parameters) throws SQLException {
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(table(sql))) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                int columns = resultSet.getMetaData().getColumnCount();
                List<Object[]> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Object[] row = new Object[columns];
                    for (int index = 0; index < columns; index++) row[index] = resultSet.getObject(index + 1);
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    /**
     * Noms de colonnes d'une table, en minuscules, dans l'ordre de declaration.
     */
    protected List<String> columnsOf(String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection jdbc = jdbc(); ResultSet resultSet = jdbc.getMetaData().getColumns(null, null, table(tableName), null)) {
            while (resultSet.next()) columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
        }
        return columns;
    }

    protected boolean tableExists(String tableName) throws SQLException {
        try (Connection jdbc = jdbc(); ResultSet resultSet = jdbc.getMetaData().getTables(null, null, table(tableName), null)) {
            return resultSet.next();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private void dropAllTables() throws SQLException {
        String[] tables = {"auction_items", "transactions", "logs", "options", "items", "players", "migration_state"};
        try (Connection jdbc = jdbc(); Statement statement = jdbc.createStatement()) {
            for (String suffix : tables) {
                statement.execute("DROP TABLE IF EXISTS " + TABLE_PREFIX + suffix);
            }
            statement.execute("DROP TABLE IF EXISTS " + MIGRATION_TABLE);
        }
    }
}
