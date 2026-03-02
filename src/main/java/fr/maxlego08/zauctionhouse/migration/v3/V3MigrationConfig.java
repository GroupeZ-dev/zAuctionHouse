package fr.maxlego08.zauctionhouse.migration.v3;

import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;

/**
 * Configuration for V3 to V4 migration.
 */
public class V3MigrationConfig {

    private final SourceType sourceType;
    // SQL settings
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String tablePrefix;
    // SQLite settings
    private final String sqlitePath;
    // JSON settings
    private final File jsonFolder;

    private V3MigrationConfig(SourceType sourceType, String host, int port, String database,
                              String username, String password, String tablePrefix,
                              String sqlitePath, File jsonFolder) {
        this.sourceType = sourceType;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        this.sqlitePath = sqlitePath;
        this.jsonFolder = jsonFolder;
    }

    /**
     * Creates a MySQL/MariaDB migration config.
     */
    public static V3MigrationConfig mysql(String host, int port, String database,
                                          String username, String password, String tablePrefix) {
        return new V3MigrationConfig(SourceType.MYSQL, host, port, database, username, password,
                tablePrefix, null, null);
    }

    /**
     * Creates a SQLite migration config.
     */
    public static V3MigrationConfig sqlite(String sqlitePath, String tablePrefix) {
        return new V3MigrationConfig(SourceType.SQLITE, null, 0, null, null, null,
                tablePrefix, sqlitePath, null);
    }

    /**
     * Creates a JSON migration config.
     */
    public static V3MigrationConfig json(File jsonFolder) {
        return new V3MigrationConfig(SourceType.JSON, null, 0, null, null, null,
                null, null, jsonFolder);
    }

    /**
     * Loads migration config from a configuration section using default V3 values.
     */
    public static V3MigrationConfig fromConfig(ConfigurationSection section, File pluginDataFolder) {
        return fromConfig(section, "zauctionhouse_", "plugins/zAuctionHouse/database.db", "plugins/zAuctionHouse");
    }

    /**
     * Loads migration config from a configuration section with a specific migration provider for defaults.
     */
    public static V3MigrationConfig fromConfig(ConfigurationSection section, File pluginDataFolder, MigrationProvider provider) {
        return fromConfig(section, provider.getDefaultTablePrefix(), provider.getDefaultSqlitePath(), provider.getDefaultJsonFolder());
    }

    /**
     * Loads migration config from a configuration section with explicit defaults.
     */
    private static V3MigrationConfig fromConfig(ConfigurationSection section, String defaultTablePrefix,
                                                String defaultSqlitePath, String defaultJsonFolder) {
        String type = section.getString("source-type", "SQLITE").toUpperCase();
        SourceType sourceType = SourceType.valueOf(type);
        String tablePrefix = section.getString("table-prefix", defaultTablePrefix);

        return switch (sourceType) {
            case MYSQL, MARIADB -> new V3MigrationConfig(
                    sourceType,
                    section.getString("host", "localhost"),
                    section.getInt("port", 3306),
                    section.getString("database", "zauctionhouse"),
                    section.getString("user", "root"),
                    section.getString("password", ""),
                    tablePrefix,
                    null,
                    null
            );
            case SQLITE -> new V3MigrationConfig(
                    sourceType,
                    null, 0, null, null, null,
                    tablePrefix,
                    section.getString("sqlite-path", defaultSqlitePath),
                    null
            );
            case JSON -> new V3MigrationConfig(
                    sourceType,
                    null, 0, null, null, null,
                    null,
                    null,
                    new File(section.getString("json-folder", defaultJsonFolder))
            );
        };
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public String getSqlitePath() {
        return sqlitePath;
    }

    public File getJsonFolder() {
        return jsonFolder;
    }

    @Override
    public String toString() {
        return switch (sourceType) {
            case MYSQL, MARIADB -> "MySQL/MariaDB: " + host + ":" + port + "/" + database;
            case SQLITE -> "SQLite: " + sqlitePath;
            case JSON -> "JSON: " + jsonFolder.getPath();
        };
    }

    public enum SourceType {
        MYSQL,
        MARIADB,
        SQLITE,
        JSON
    }
}
