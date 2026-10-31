package fr.maxlego08.zauctionhouse.testutil;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Fabrique de doublures pour {@link AuctionPlugin} et ses collaborateurs.
 * <p>
 * Aucun serveur Bukkit n'est demarre : tout ce qui touche a {@code org.bukkit.Bukkit} doit etre
 * soit evite, soit couvert par {@code Mockito.mockStatic(Bukkit.class)} dans le test concerne.
 * Les stubs sont {@code lenient} : un test peut n'utiliser qu'une partie du plugin sans que
 * Mockito ne se plaigne des stubs inutilises.
 */
public final class MockPlugins {

    public static final String SERVER_NAME = "test-server";

    private MockPlugins() {
    }

    /**
     * Cree un plugin mocke pret pour les tests de la couche storage et des services.
     *
     * @param dataFolder dossier de donnees (base SQLite, fichiers de configuration)
     * @return le plugin mocke
     */
    public static AuctionPlugin plugin(File dataFolder) {
        // Les collaborateurs sont construits AVANT les stubs : creer un mock pendant qu'un
        // when(...) est en cours declenche UnfinishedStubbingException chez Mockito.
        Logger logger = Logger.getLogger("zAuctionHouse-test");
        ExecutorService executorService = syncExecutor();
        PlatformScheduler scheduler = immediateScheduler();
        Configuration configuration = configuration();
        YamlConfiguration config = sqliteConfig();
        EconomyManager economyManager = economyManager(economy("vault"));

        AuctionPlugin plugin = mock(AuctionPlugin.class);
        lenient().when(plugin.getLogger()).thenReturn(logger);
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder);
        lenient().when(plugin.getExecutorService()).thenReturn(executorService);
        lenient().when(plugin.getScheduler()).thenReturn(scheduler);
        lenient().when(plugin.getConfiguration()).thenReturn(configuration);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getEconomyManager()).thenReturn(economyManager);
        return plugin;
    }

    /**
     * Configuration principale mockee en "deep stubs" : chaque sous-record est lui-meme une
     * doublure dont les valeurs par defaut sont neutres (0, false, null, "").
     */
    public static Configuration configuration() {
        Configuration configuration = mock(Configuration.class, Mockito.RETURNS_DEEP_STUBS);
        lenient().when(configuration.getServerName()).thenReturn(SERVER_NAME);
        lenient().when(configuration.isEnableDebug()).thenReturn(false);
        lenient().when(configuration.isEnablePerformanceDebug()).thenReturn(false);
        return configuration;
    }

    /**
     * {@code config.yml} minimal, backend SQLite, tel que le lit {@code ZStorageManager}.
     */
    public static YamlConfiguration sqliteConfig() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("storage-type", "SQLITE");
        configuration.set("server-name", SERVER_NAME);
        configuration.set("database-configuration.table-prefix", "zah_test_");
        configuration.set("database-configuration.host", "localhost");
        configuration.set("database-configuration.port", 3306);
        configuration.set("database-configuration.user", "test");
        configuration.set("database-configuration.password", "test");
        configuration.set("database-configuration.database", "test");
        configuration.set("database-configuration.debug", false);
        return configuration;
    }

    public static ExecutorService syncExecutor() {
        return new SyncExecutorService();
    }

    /**
     * Ordonnanceur FoliaLib qui execute IMMEDIATEMENT, sur le thread appelant, toute tache
     * recue (Runnable ou Consumer&lt;WrappedTask&gt;), quel que soit le point d'entree utilise
     * (runNextTick, runAsync, runAtEntity, runLater, ...).
     */
    public static PlatformScheduler immediateScheduler() {
        WrappedTask wrappedTask = mock(WrappedTask.class);
        Answer<Object> runNow = invocation -> {
            for (Object argument : invocation.getArguments()) {
                if (argument instanceof Runnable runnable) {
                    runnable.run();
                } else if (argument instanceof Consumer<?> consumer) {
                    @SuppressWarnings("unchecked")
                    Consumer<WrappedTask> taskConsumer = (Consumer<WrappedTask>) consumer;
                    taskConsumer.accept(wrappedTask);
                }
            }
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (returnType == CompletableFuture.class) return CompletableFuture.completedFuture(null);
            if (returnType == WrappedTask.class) return wrappedTask;
            if (returnType == boolean.class) return true;
            return null;
        };
        return mock(PlatformScheduler.class, runNow);
    }

    /**
     * Economie mockee : le nom est le seul attribut lu par la couche storage.
     */
    public static AuctionEconomy economy(String name) {
        AuctionEconomy economy = mock(AuctionEconomy.class);
        lenient().when(economy.getName()).thenReturn(name);
        lenient().when(economy.getDisplayName()).thenReturn(name);
        lenient().when(economy.getSymbol()).thenReturn("$");
        lenient().when(economy.getFormat()).thenReturn("%price%$");
        lenient().when(economy.get(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(BigDecimal.ZERO));
        lenient().when(economy.has(any(UUID.class), any(BigDecimal.class))).thenReturn(CompletableFuture.completedFuture(true));
        return economy;
    }

    /**
     * Gestionnaire d'economies qui ne connait que les economies passees en parametre.
     */
    public static EconomyManager economyManager(AuctionEconomy... economies) {
        EconomyManager economyManager = mock(EconomyManager.class);
        lenient().when(economyManager.getEconomy(anyString())).thenReturn(Optional.empty());
        for (AuctionEconomy economy : economies) {
            // economy.getName() est lu AVANT le when(...) : c'est un appel sur un autre mock.
            String name = economy.getName();
            Optional<AuctionEconomy> optional = Optional.of(economy);
            lenient().when(economyManager.getEconomy(name)).thenReturn(optional);
        }
        if (economies.length > 0) {
            lenient().when(economyManager.getDefaultEconomy(any())).thenReturn(economies[0]);
        }
        return economyManager;
    }

    /**
     * Joueur mocke : identifiant et nom seulement, aucun inventaire.
     */
    public static Player player(UUID uniqueId, String name) {
        Player player = mock(Player.class);
        lenient().when(player.getUniqueId()).thenReturn(uniqueId);
        lenient().when(player.getName()).thenReturn(name);
        lenient().when(player.isOnline()).thenReturn(true);
        return player;
    }

    public static Player player(String name) {
        return player(UUID.nameUUIDFromBytes(name.getBytes()), name);
    }
}
