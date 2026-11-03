package fr.maxlego08.zauctionhouse.migration.v3;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class V3MigrationConfigTest {

    private static final File DATA_FOLDER = new File("plugins/zAuctionHouseV4");

    private static YamlConfiguration section(Object... keyValues) {
        YamlConfiguration section = new YamlConfiguration();
        for (int index = 0; index < keyValues.length; index += 2) {
            section.set((String) keyValues[index], keyValues[index + 1]);
        }
        return section;
    }

    @Nested
    @DisplayName("fabriques statiques")
    class Factories {

        @Test
        @DisplayName("sqlite() ne renseigne que le chemin et le prefixe")
        void sqlite_setsPathAndPrefixOnly() {
            V3MigrationConfig config = V3MigrationConfig.sqlite("plugins/v3/database.db", "zah_");

            assertEquals(V3MigrationConfig.SourceType.SQLITE, config.getSourceType());
            assertEquals("plugins/v3/database.db", config.getSqlitePath());
            assertEquals("zah_", config.getTablePrefix());
            assertNull(config.getHost());
            assertEquals(0, config.getPort());
            assertNull(config.getDatabase());
            assertNull(config.getUsername());
            assertNull(config.getPassword());
            assertNull(config.getJsonFolder());
            assertEquals("SQLite: plugins/v3/database.db", config.toString());
        }

        @Test
        @DisplayName("mysql() renseigne les parametres de connexion et le prefixe")
        void mysql_setsConnectionParameters() {
            V3MigrationConfig config = V3MigrationConfig.mysql("db.local", 3307, "auction", "user", "secret", "v3_");

            assertEquals(V3MigrationConfig.SourceType.MYSQL, config.getSourceType());
            assertEquals("db.local", config.getHost());
            assertEquals(3307, config.getPort());
            assertEquals("auction", config.getDatabase());
            assertEquals("user", config.getUsername());
            assertEquals("secret", config.getPassword());
            assertEquals("v3_", config.getTablePrefix());
            assertNull(config.getSqlitePath());
            assertNull(config.getJsonFolder());
            assertEquals("MySQL/MariaDB: db.local:3307/auction", config.toString());
        }

        @Test
        @DisplayName("json() ne renseigne que le dossier")
        void json_setsFolderOnly() {
            File folder = new File("plugins/zAuctionHouseV3");
            V3MigrationConfig config = V3MigrationConfig.json(folder);

            assertEquals(V3MigrationConfig.SourceType.JSON, config.getSourceType());
            assertEquals(folder, config.getJsonFolder());
            assertNull(config.getTablePrefix());
            assertNull(config.getSqlitePath());
            assertNull(config.getHost());
            assertEquals("JSON: " + folder.getPath(), config.toString());
        }
    }

    @Nested
    @DisplayName("fromConfig avec les defauts V3 historiques")
    class FromConfigDefaults {

        @Test
        @DisplayName("une section vide donne du SQLite avec le chemin et le prefixe historiques")
        void emptySection_defaultsToSqlite() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section(), DATA_FOLDER);

            assertEquals(V3MigrationConfig.SourceType.SQLITE, config.getSourceType());
            assertEquals("plugins/zAuctionHouse/database.db", config.getSqlitePath());
            assertEquals("zauctionhouse_", config.getTablePrefix());
        }

        @Test
        @DisplayName("sqlite-path et table-prefix explicites sont respectes")
        void sqlite_explicitValues() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(
                    section("source-type", "sqlite", "sqlite-path", "D:/v3/data.db", "table-prefix", "old_"), DATA_FOLDER);

            assertEquals(V3MigrationConfig.SourceType.SQLITE, config.getSourceType());
            assertEquals("D:/v3/data.db", config.getSqlitePath());
            assertEquals("old_", config.getTablePrefix());
        }

        @ParameterizedTest(name = "source-type={0}")
        @ValueSource(strings = {"MYSQL", "mysql", "MARIADB", "MariaDB"})
        @DisplayName("MySQL/MariaDB sans parametres prend localhost:3306/zauctionhouse root et mot de passe vide")
        void sql_defaults(String type) {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section("source-type", type), DATA_FOLDER);

            assertEquals(V3MigrationConfig.SourceType.valueOf(type.toUpperCase()), config.getSourceType());
            assertEquals("localhost", config.getHost());
            assertEquals(3306, config.getPort());
            assertEquals("zauctionhouse", config.getDatabase());
            assertEquals("root", config.getUsername());
            assertEquals("", config.getPassword());
            assertEquals("zauctionhouse_", config.getTablePrefix());
            assertNull(config.getSqlitePath());
            assertNull(config.getJsonFolder());
        }

        @Test
        @DisplayName("MySQL avec tous les parametres explicites")
        void mysql_explicitValues() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section(
                    "source-type", "MYSQL", "host", "10.0.0.5", "port", 3310, "database", "ah", "user", "zah", "password", "pw", "table-prefix", "p_"), DATA_FOLDER);

            assertEquals("10.0.0.5", config.getHost());
            assertEquals(3310, config.getPort());
            assertEquals("ah", config.getDatabase());
            assertEquals("zah", config.getUsername());
            assertEquals("pw", config.getPassword());
            assertEquals("p_", config.getTablePrefix());
            assertEquals("MySQL/MariaDB: 10.0.0.5:3310/ah", config.toString());
        }

        @Test
        @DisplayName("JSON sans dossier prend le dossier historique, et ignore le prefixe de table")
        void json_defaults() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section("source-type", "json", "table-prefix", "ignored_"), DATA_FOLDER);

            assertEquals(V3MigrationConfig.SourceType.JSON, config.getSourceType());
            assertEquals(new File("plugins/zAuctionHouse"), config.getJsonFolder());
            assertNull(config.getTablePrefix());
            assertNull(config.getSqlitePath());
        }

        @Test
        @DisplayName("json-folder explicite est respecte")
        void json_explicitFolder() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section("source-type", "JSON", "json-folder", "backup/v3"), DATA_FOLDER);

            assertEquals(new File("backup/v3"), config.getJsonFolder());
        }

        @Test
        @DisplayName("un source-type inconnu est rejete par une IllegalArgumentException")
        void unknownSourceType_throws() {
            YamlConfiguration section = section("source-type", "postgres");

            assertThrows(IllegalArgumentException.class, () -> V3MigrationConfig.fromConfig(section, DATA_FOLDER));
        }
    }

    @Nested
    @DisplayName("fromConfig avec les defauts du provider")
    class FromConfigProviderDefaults {

        private final V3MigrationProvider provider = new V3MigrationProvider();

        @Test
        @DisplayName("SQLite sans valeurs prend les defauts du provider V3")
        void sqlite_providerDefaults() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section(), DATA_FOLDER, provider);

            assertEquals(V3MigrationConfig.SourceType.SQLITE, config.getSourceType());
            assertEquals(provider.getDefaultSqlitePath(), config.getSqlitePath());
            assertEquals("plugins/zAuctionHouseV3/database.db", config.getSqlitePath());
            assertEquals(provider.getDefaultTablePrefix(), config.getTablePrefix());
        }

        @Test
        @DisplayName("JSON sans dossier prend le dossier par defaut du provider")
        void json_providerDefaults() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section("source-type", "JSON"), DATA_FOLDER, provider);

            assertEquals(new File(provider.getDefaultJsonFolder()), config.getJsonFolder());
            assertEquals(new File("plugins/zAuctionHouseV3"), config.getJsonFolder());
        }

        @Test
        @DisplayName("MySQL prend le prefixe par defaut du provider quand table-prefix manque")
        void mysql_providerPrefix() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(section("source-type", "MYSQL", "host", "h"), DATA_FOLDER, provider);

            assertEquals("zauctionhouse_", config.getTablePrefix());
            assertEquals("h", config.getHost());
        }

        @Test
        @DisplayName("les valeurs explicites l'emportent sur les defauts du provider")
        void explicitValues_winOverProvider() {
            V3MigrationConfig config = V3MigrationConfig.fromConfig(
                    section("sqlite-path", "custom.db", "table-prefix", "c_"), DATA_FOLDER, provider);

            assertEquals("custom.db", config.getSqlitePath());
            assertEquals("c_", config.getTablePrefix());
        }
    }
}
