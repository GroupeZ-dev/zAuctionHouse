package fr.maxlego08.zauctionhouse.storage.repository;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link Repositories} : registre des repositories construits par reflexion.
 * <p>
 * Aucune base n'est necessaire : le constructeur des repositories ne fait que stocker la
 * connexion, qui est ici une doublure.
 */
class RepositoriesTest {

    /**
     * Repository SANS constructeur {@code (AuctionPlugin, DatabaseConnection)} : son
     * enregistrement doit echouer proprement (NoSuchMethodException).
     */
    public static final class NoMatchingConstructorRepository extends Repository {
        public NoMatchingConstructorRepository(AuctionPlugin plugin, DatabaseConnection connection, String extra) {
            super(plugin, connection, extra);
        }
    }

    /**
     * Repository dont le constructeur attendu existe mais LEVE : l'echec remonte en
     * InvocationTargetException et doit lui aussi etre journalise sans propager.
     */
    public static final class ExplodingRepository extends Repository {
        public ExplodingRepository(AuctionPlugin plugin, DatabaseConnection connection) {
            super(plugin, connection, "%prefix%exploding");
            throw new IllegalStateException("boom");
        }
    }

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private DatabaseConnection connection;
    private Repositories repositories;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.connection = mock(DatabaseConnection.class);
        this.repositories = new Repositories(this.plugin, this.connection);
    }

    @Test
    @DisplayName("register construit le repository avec le plugin et la connexion fournis")
    void register_buildsRepositoryWithPluginAndConnection() {
        this.repositories.register(PlayerRepository.class);

        PlayerRepository repository = this.repositories.getTable(PlayerRepository.class);

        assertNotNull(repository);
        assertSame(this.plugin, repository.getPlugin());
        assertSame(this.connection, repository.getConnection());
        assertEquals(Tables.PLAYERS, repository.getTableName());
    }

    @Test
    @DisplayName("getTable rend toujours la MEME instance pour une classe enregistree")
    void getTable_returnsSameInstanceEveryTime() {
        this.repositories.register(ItemRepository.class);

        ItemRepository first = this.repositories.getTable(ItemRepository.class);
        ItemRepository second = this.repositories.getTable(ItemRepository.class);

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("chaque classe enregistree a sa propre instance")
    void register_severalClasses_keepsOneInstancePerClass() {
        this.repositories.register(PlayerRepository.class);
        this.repositories.register(ItemRepository.class);

        assertNotNull(this.repositories.getTable(PlayerRepository.class));
        assertNotNull(this.repositories.getTable(ItemRepository.class));
        assertNotSame(this.repositories.getTable(PlayerRepository.class), this.repositories.getTable(ItemRepository.class));
    }

    @Test
    @DisplayName("getTable sur une classe jamais enregistree rend null")
    void getTable_unregisteredClass_returnsNull() {
        assertNull(this.repositories.getTable(ItemRepository.class));
    }

    @Test
    @DisplayName("un second register remplace l'instance precedente")
    void register_twice_replacesInstance() {
        this.repositories.register(PlayerRepository.class);
        PlayerRepository first = this.repositories.getTable(PlayerRepository.class);

        this.repositories.register(PlayerRepository.class);
        PlayerRepository second = this.repositories.getTable(PlayerRepository.class);

        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    @DisplayName("constructeur introuvable => SEVERE journalise, aucune exception, getTable rend null")
    void register_withoutMatchingConstructor_logsSevereWithoutThrowing() {
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            assertDoesNotThrow(() -> this.repositories.register(NoMatchingConstructorRepository.class));

            assertTrue(logs.contains(Level.SEVERE, "Failed to register repository NoMatchingConstructorRepository"), () -> "SEVERE attendu, recu : " + logs.messages(Level.SEVERE));
        }
        assertNull(this.repositories.getTable(NoMatchingConstructorRepository.class));
    }

    @Test
    @DisplayName("constructeur qui leve => SEVERE journalise, aucune exception, getTable rend null")
    void register_constructorThrows_logsSevereWithoutThrowing() {
        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            assertDoesNotThrow(() -> this.repositories.register(ExplodingRepository.class));

            assertEquals(1, logs.count(Level.SEVERE));
            assertTrue(logs.contains(Level.SEVERE, "ExplodingRepository"));
        }
        assertNull(this.repositories.getTable(ExplodingRepository.class));
    }

    @Test
    @DisplayName("un echec d'enregistrement n'empeche pas les autres repositories")
    void register_failureDoesNotAffectOtherRepositories() {
        this.repositories.register(PlayerRepository.class);
        this.repositories.register(NoMatchingConstructorRepository.class);
        this.repositories.register(ItemRepository.class);

        assertNotNull(this.repositories.getTable(PlayerRepository.class));
        assertNotNull(this.repositories.getTable(ItemRepository.class));
        assertNull(this.repositories.getTable(NoMatchingConstructorRepository.class));
    }
}
