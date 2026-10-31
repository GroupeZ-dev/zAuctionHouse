package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.sarah.DatabaseConfiguration;
import fr.maxlego08.sarah.database.DatabaseType;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.lenient;

/**
 * Lecture de la configuration base : {@link GlobalDatabaseConfiguration} seule, puis la
 * methode privee {@code ZStorageManager.getDatabaseConfiguration()} qui assemble la
 * {@link DatabaseConfiguration} Sarah.
 * <p>
 * Le fichier global {@code database-configuration.yml} est cherche dans le repertoire courant de
 * la JVM : les tests supposent qu'il n'existe pas (branche "configuration du plugin seule"). La
 * branche "fichier global present" n'est pas couverte ici car elle exigerait d'ecrire dans le
 * repertoire courant du build.
 */
class GlobalDatabaseConfigurationTest {

    @TempDir
    File dataFolder;

    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        assumeFalse(new File("database-configuration.yml").exists(), "un fichier global database-configuration.yml est present dans le repertoire courant : branche non testee ici");
        this.config = new YamlConfiguration();
        this.config.set("storage-type", "SQLITE");
        this.config.set("database-configuration.table-prefix", "zah_");
        this.config.set("database-configuration.host", "db.example.org");
        this.config.set("database-configuration.port", 3307);
        this.config.set("database-configuration.user", "zah_user");
        this.config.set("database-configuration.password", "s3cret");
        this.config.set("database-configuration.database", "auction");
        this.config.set("database-configuration.debug", true);
    }

    @Test
    @DisplayName("chaque cle de database-configuration est lue depuis la configuration du plugin")
    void getters_readEveryKeyFromPluginConfiguration() {
        GlobalDatabaseConfiguration configuration = new GlobalDatabaseConfiguration(this.config);

        assertEquals("zah_", configuration.getTablePrefix());
        assertEquals("db.example.org", configuration.getHost());
        assertEquals(3307, configuration.getPort());
        assertEquals("zah_user", configuration.getUser());
        assertEquals("s3cret", configuration.getPassword());
        assertEquals("auction", configuration.getDatabase());
        assertTrue(configuration.isDebug());
    }

    @Test
    @DisplayName("debug absent => false")
    void isDebug_missingKey_false() {
        this.config.set("database-configuration.debug", null);
        assertFalse(new GlobalDatabaseConfiguration(this.config).isDebug());
    }

    @Test
    @DisplayName("max-lifetime absent => null (defaut de la librairie conserve)")
    void getMaxLifetimeMillis_missing_returnsNull() {
        assertNull(new GlobalDatabaseConfiguration(this.config).getMaxLifetimeMillis());
    }

    @ParameterizedTest(name = "max-lifetime = {0} secondes => null")
    @ValueSource(longs = {-1L, -2L, -3600L})
    @DisplayName("max-lifetime negatif => null")
    void getMaxLifetimeMillis_negative_returnsNull(long seconds) {
        this.config.set("database-configuration.max-lifetime", seconds);
        assertNull(new GlobalDatabaseConfiguration(this.config).getMaxLifetimeMillis());
    }

    @ParameterizedTest(name = "max-lifetime = {0} secondes => {1} ms")
    @CsvSource({"0, 0", "1, 1000", "1800, 1800000", "7200, 7200000"})
    @DisplayName("max-lifetime positif ou nul est converti en millisecondes")
    void getMaxLifetimeMillis_convertsSecondsToMillis(long seconds, long expectedMillis) {
        this.config.set("database-configuration.max-lifetime", seconds);
        assertEquals(expectedMillis, new GlobalDatabaseConfiguration(this.config).getMaxLifetimeMillis());
    }

    // ------------------------------------------------------------------------------------------
    // ZStorageManager.getDatabaseConfiguration() (prive) : assemblage de la DatabaseConfiguration
    // ------------------------------------------------------------------------------------------

    private DatabaseConfiguration buildDatabaseConfiguration() throws Exception {
        AuctionPlugin plugin = MockPlugins.plugin(this.dataFolder);
        lenient().when(plugin.getConfig()).thenReturn(this.config);

        Method method = ZStorageManager.class.getDeclaredMethod("getDatabaseConfiguration");
        method.setAccessible(true);
        return (DatabaseConfiguration) method.invoke(new ZStorageManager(plugin));
    }

    @Test
    @DisplayName("getDatabaseConfiguration recopie chaque champ et le type de stockage")
    void getDatabaseConfiguration_copiesEveryField() throws Exception {
        this.config.set("storage-type", "mysql"); // la casse est normalisee

        DatabaseConfiguration configuration = buildDatabaseConfiguration();

        assertEquals(DatabaseType.MYSQL, configuration.getDatabaseType());
        assertEquals("zah_", configuration.getTablePrefix());
        assertEquals("db.example.org", configuration.getHost());
        assertEquals(3307, configuration.getPort());
        assertEquals("zah_user", configuration.getUser());
        assertEquals("s3cret", configuration.getPassword());
        assertEquals("auction", configuration.getDatabase());
        assertTrue(configuration.isDebug());
        assertEquals("zah_items", configuration.replacePrefix("%prefix%items"));
    }

    @Test
    @DisplayName("storage-type absent => SQLITE par defaut")
    void getDatabaseConfiguration_missingStorageType_defaultsToSqlite() throws Exception {
        this.config.set("storage-type", null);
        assertEquals(DatabaseType.SQLITE, buildDatabaseConfiguration().getDatabaseType());
    }

    @Test
    @DisplayName("storage-type inconnu => IllegalArgumentException (pas de repli silencieux)")
    void getDatabaseConfiguration_unknownStorageType_throws() {
        this.config.set("storage-type", "ORACLE");
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, this::buildDatabaseConfiguration);
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    @DisplayName("max-lifetime = -1 => la configuration garde la valeur par defaut de Sarah")
    void getDatabaseConfiguration_maxLifetimeMinusOne_keepsLibraryDefault() throws Exception {
        this.config.set("database-configuration.max-lifetime", -1L);

        DatabaseConfiguration configuration = buildDatabaseConfiguration();

        DatabaseConfiguration reference = new DatabaseConfiguration("zah_", "zah_user", "s3cret", 3307, "db.example.org", "auction", true, DatabaseType.SQLITE);
        assertEquals(reference.getMaxLifetime(), configuration.getMaxLifetime());
    }

    @Test
    @DisplayName("max-lifetime = 0 => withMaxLifetime(0) (retrait des connexions desactive)")
    void getDatabaseConfiguration_maxLifetimeZero_isZero() throws Exception {
        this.config.set("database-configuration.max-lifetime", 0L);
        assertEquals(0L, buildDatabaseConfiguration().getMaxLifetime());
    }

    @Test
    @DisplayName("max-lifetime = 1800 => 1 800 000 ms")
    void getDatabaseConfiguration_maxLifetime1800_is1800000Millis() throws Exception {
        this.config.set("database-configuration.max-lifetime", 1800L);
        assertEquals(1_800_000L, buildDatabaseConfiguration().getMaxLifetime());
    }
}
