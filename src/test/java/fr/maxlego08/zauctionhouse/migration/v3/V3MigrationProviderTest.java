package fr.maxlego08.zauctionhouse.migration.v3;

import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.migration.MigrationCallback;
import fr.maxlego08.zauctionhouse.api.migration.MigrationResult;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.V3SourceDatabase;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3MigrationProviderTest extends StorageManagerTestBase {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    private V3MigrationProvider provider;
    private V3SourceDatabase source;

    @BeforeEach
    void setUpProvider() throws Exception {
        this.provider = new V3MigrationProvider();
        this.source = V3SourceDatabase.create(new File(this.dataFolder, "v3/database.db"));
    }

    private static YamlConfiguration section(Object... keyValues) {
        YamlConfiguration section = new YamlConfiguration();
        for (int index = 0; index < keyValues.length; index += 2) {
            section.set((String) keyValues[index], keyValues[index + 1]);
        }
        return section;
    }

    private ConfigurationSection sqliteSection() {
        return section("source-type", "SQLITE", "sqlite-path", this.source.path(), "table-prefix", V3SourceDatabase.DEFAULT_PREFIX);
    }

    @Nested
    @DisplayName("identite")
    class Identity {

        @Test
        @DisplayName("identifiant, nom, description, alias, section et defauts")
        void metadata() {
            assertEquals("zauctionhousev3", provider.getId());
            assertEquals("zAuctionHouse V3", provider.getDisplayName());
            assertEquals("zAuctionHouse V3 (previous version)", provider.getDescription());
            assertEquals(List.of("zah", "zahv3", "v3", "zauctionhouse"), provider.getAliases());
            assertEquals("zauctionhouse-v3", provider.getConfigSection());
            assertEquals("plugins/zAuctionHouseV3/database.db", provider.getDefaultSqlitePath());
            assertEquals("plugins/zAuctionHouseV3", provider.getDefaultJsonFolder());
            assertEquals("zauctionhouse_", provider.getDefaultTablePrefix());
        }

        @ParameterizedTest(name = "matches(\"{0}\")")
        @ValueSource(strings = {"zauctionhousev3", "ZAUCTIONHOUSEV3", "zah", "ZAH", "zahv3", "v3", "V3", "zauctionhouse", "zAuctionHouse V3", "ZAUCTIONHOUSE v3"})
        @DisplayName("matches accepte l'identifiant, le nom affiche et les alias sans tenir compte de la casse")
        void matches_accepted(String input) {
            assertTrue(provider.matches(input));
        }

        @ParameterizedTest(name = "matches(\"{0}\") = false")
        @ValueSource(strings = {"", "v4", "crazyauctions", "zauctionhousev3 ", "zah v3"})
        @DisplayName("matches refuse tout le reste")
        void matches_refused(String input) {
            assertFalse(provider.matches(input));
        }

        @Test
        @DisplayName("matches(null) rend false")
        void matches_null() {
            assertFalse(provider.matches(null));
        }
    }

    @Nested
    @DisplayName("validateConfig")
    class ValidateConfig {

        @Test
        @DisplayName("section absente")
        void nullSection() {
            assertEquals("Configuration section is missing", provider.validateConfig(null));
        }

        @Test
        @DisplayName("SQLite : fichier present -> valide")
        void sqlite_existingFile_valid() {
            assertNull(provider.validateConfig(sqliteSection()));
        }

        @Test
        @DisplayName("SQLite : fichier absent -> message qui cite le chemin")
        void sqlite_missingFile_error() {
            String path = new File(dataFolder, "nope.db").getAbsolutePath();

            assertEquals("SQLite database file not found: " + path, provider.validateConfig(section("source-type", "sqlite", "sqlite-path", path)));
        }

        @Test
        @DisplayName("SQLite : sans source-type ni chemin, le defaut du provider est controle")
        void sqlite_defaults() {
            Assumptions.assumeFalse(new File(provider.getDefaultSqlitePath()).exists(), "le chemin V3 par defaut existe dans le dossier courant");

            assertEquals("SQLite database file not found: " + provider.getDefaultSqlitePath(), provider.validateConfig(section()));
        }

        @Test
        @DisplayName("JSON : dossier present -> valide, dossier absent ou fichier -> erreur")
        void json() throws Exception {
            File folder = new File(dataFolder, "json-ok");
            assertTrue(folder.mkdirs());
            File notAFolder = new File(dataFolder, "file.json");
            Files.writeString(notAFolder.toPath(), "[]");
            String missing = new File(dataFolder, "json-missing").getAbsolutePath();

            assertNull(provider.validateConfig(section("source-type", "JSON", "json-folder", folder.getAbsolutePath())));
            assertEquals("JSON folder not found: " + notAFolder.getAbsolutePath(), provider.validateConfig(section("source-type", "json", "json-folder", notAFolder.getAbsolutePath())));
            assertEquals("JSON folder not found: " + missing, provider.validateConfig(section("source-type", "JSON", "json-folder", missing)));
        }

        @ParameterizedTest(name = "source-type={0}")
        @ValueSource(strings = {"MYSQL", "mysql", "MARIADB", "mariadb"})
        @DisplayName("MySQL/MariaDB : l'hote est obligatoire, aucune connexion n'est tentee")
        void sql_hostRequired(String type) {
            assertEquals("Database host is not configured", provider.validateConfig(section("source-type", type)));
            assertEquals("Database host is not configured", provider.validateConfig(section("source-type", type, "host", "")));
            assertNull(provider.validateConfig(section("source-type", type, "host", "db.example.invalid")));
        }

        @Test
        @DisplayName("type inconnu")
        void unknownType() {
            assertEquals("Unknown source type: POSTGRES", provider.validateConfig(section("source-type", "postgres")));
        }
    }

    @Nested
    @DisplayName("migrate")
    class Migrate {

        @Test
        @DisplayName("succes : le resultat API reprend les compteurs du service et la base V4 est remplie")
        void sqlite_success() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("s1").storageType("STORAGE"));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("s2").buyer(BOB).storageType("BUY"));
            source.insert(V3SourceDatabase.transaction(ALICE, BOB).needMoney(true));
            List<String> progress = new CopyOnWriteArrayList<>();
            MigrationCallback callback = progress::add;

            MigrationResult result = provider.migrate(plugin, sqliteSection(), callback).join();

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(2, result.getPlayersImported());
            assertEquals(2, result.getItemsImported());
            assertEquals(1, result.getTransactionsImported());
            assertEquals(0, result.getErrors());
            assertNull(result.getErrorMessage());
            assertTrue(result.getDurationMs() >= 0);

            assertEquals(2, count(Tables.PLAYERS));
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", StorageType.LISTED.name()));
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", StorageType.PURCHASED.name()));
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", StorageType.DELETED.name()));
            assertEquals(2, count(Tables.AUCTION_ITEMS));
            assertEquals(1, count(Tables.TRANSACTIONS));
            assertEquals(1, count(Tables.LOGS, "additional_data = ?", LogRepository.MIGRATED_FROM_V3));
            assertTrue(progress.contains("Connection successful!"));
            assertTrue(progress.contains("Migrated 2 items"));
        }

        @Test
        @DisplayName("le provider n'ecrit pas la sentinelle d'idempotence (responsabilite de la commande)")
        void sqlite_success_noSentinel() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice"));

            assertTrue(provider.migrate(plugin, sqliteSection(), MigrationCallback.empty()).join().isSuccess());

            assertEquals(0, count(MigrationStateRepository.TABLE_NAME));
        }

        @Test
        @DisplayName("echec du service (base V3 vide) : le verdict est propage en failure, journalise en SEVERE")
        void emptySource_failurePropagated() throws Exception {
            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                MigrationResult result = provider.migrate(plugin, sqliteSection(), MigrationCallback.empty()).join();

                assertFalse(result.isSuccess());
                assertEquals("No data found to migrate", result.getErrorMessage());
                assertEquals(0, result.getItemsImported());
                assertTrue(logs.contains(Level.SEVERE, "zAuctionHouse V3 migration FAILED: No data found to migrate"));
            }
            assertEquals(0, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("source injoignable : failure avec le message de connexion")
        void unreachableSource_failure() {
            String path = new File(dataFolder, "missing/dir/database.db").getAbsolutePath();

            MigrationResult result = provider.migrate(plugin, section("source-type", "SQLITE", "sqlite-path", path), MigrationCallback.empty()).join();

            assertFalse(result.isSuccess());
            assertEquals("Failed to connect to V3 data source", result.getErrorMessage());
        }

        @Test
        @DisplayName("succes avec erreurs partielles : success + compteur d'erreurs + WARNING qui interdit de supprimer la V3")
        void partialErrors_successWithWarning() throws Exception {
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("ok"));
            source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack(null));

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                MigrationResult result = provider.migrate(plugin, sqliteSection(), MigrationCallback.empty()).join();

                assertTrue(result.isSuccess());
                assertEquals(1, result.getItemsImported());
                assertEquals(1, result.getErrors());
                assertTrue(logs.contains(Level.WARNING, "migration completed with 1 error(s)"));
                assertFalse(logs.contains(Level.WARNING, "migration completed with 0 error(s)"));
            }
            assertEquals(1, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("source JSON configuree par section")
        void json_success() throws Exception {
            File folder = new File(dataFolder, "v3json");
            assertTrue(folder.mkdirs());
            Files.writeString(new File(folder, "items.json").toPath(), """
                    [{"id":"%s","itemstack":"json-stack","price":5,"seller":"%s","sellerName":"Alice"}]
                    """.formatted(UUID.randomUUID(), ALICE));

            MigrationResult result = provider.migrate(plugin, section("source-type", "JSON", "json-folder", folder.getAbsolutePath()), MigrationCallback.empty()).join();

            assertTrue(result.isSuccess(), result.toString());
            assertEquals(1, result.getItemsImported());
            assertEquals(1, result.getPlayersImported());
            assertEquals("json-stack", queryString("SELECT itemstack FROM " + Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("le prefixe de table de la section est respecte")
        void customPrefix() throws Exception {
            V3SourceDatabase custom = V3SourceDatabase.create(new File(dataFolder, "custom/database.db"), "old_");
            custom.insert(V3SourceDatabase.item(ALICE, "Alice"));

            MigrationResult wrongPrefix = provider.migrate(plugin, section("sqlite-path", custom.path()), MigrationCallback.empty()).join();
            MigrationResult rightPrefix = provider.migrate(plugin, section("sqlite-path", custom.path(), "table-prefix", "old_"), MigrationCallback.empty()).join();

            assertFalse(wrongPrefix.isSuccess(), "le prefixe par defaut ne trouve aucune table");
            assertTrue(rightPrefix.isSuccess());
            assertEquals(1, rightPrefix.getItemsImported());
        }
    }
}
