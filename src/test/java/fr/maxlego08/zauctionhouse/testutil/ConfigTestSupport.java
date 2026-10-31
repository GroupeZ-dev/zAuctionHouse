package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.font.FontImage;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Aides pour les tests de configuration (taxes, economies, config.yml et ses records).
 * <p>
 * Les fichiers de ressources sont lus directement depuis {@code src/main/resources} : les tests
 * Gradle s'executent avec le dossier du projet comme repertoire courant.
 */
public final class ConfigTestSupport {

    public static final Path RESOURCES = Path.of("src", "main", "resources");

    private ConfigTestSupport() {
    }

    /**
     * Fichier de ressource de production, par exemple {@code "config.yml"} ou {@code "fr/config.yml"}.
     */
    public static File resource(String relativePath) {
        return RESOURCES.resolve(relativePath).toFile();
    }

    /**
     * Charge un fichier YAML de production dans une {@link YamlConfiguration}.
     */
    public static YamlConfiguration loadResource(String relativePath) {
        File file = resource(relativePath);
        if (!file.isFile()) {
            throw new IllegalStateException("Ressource introuvable : " + file.getAbsolutePath());
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Plugin mocke minimal : seul {@code getLogger()} est reel. Suffisant pour les records de
     * configuration, qui ne lisent le plugin que pour journaliser.
     */
    public static AuctionPlugin loggingPlugin() {
        Logger logger = Logger.getLogger("zAuctionHouse-config-test");
        AuctionPlugin plugin = mock(AuctionPlugin.class);
        lenient().when(plugin.getLogger()).thenReturn(logger);
        return plugin;
    }

    /**
     * Plugin mocke complet pour {@code MainConfiguration} et {@code ZEconomyManager} :
     * {@code getConfig()} rend la configuration fournie, le chargeur d'inventaires zMenu est
     * une doublure dont {@code loadItemStack} rend toujours le meme {@link MenuItemStack} mocke,
     * la {@link FontImage} rend la chaine inchangee et le registre de regles est une doublure.
     *
     * @param dataFolder dossier de donnees du plugin
     * @param config     contenu de {@code config.yml}
     */
    public static AuctionPlugin configPlugin(File dataFolder, YamlConfiguration config) {
        // Tous les collaborateurs sont construits AVANT les when(...) qui les rendent.
        MenuItemStack menuItemStack = mock(MenuItemStack.class);
        FontImage fontImage = mock(FontImage.class);
        lenient().when(fontImage.replace(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        InventoryManager inventoryManager = mock(InventoryManager.class);
        lenient().when(inventoryManager.loadItemStack(any(YamlConfiguration.class), anyString(), any(File.class))).thenReturn(menuItemStack);
        lenient().when(inventoryManager.loadItemStack(any(File.class), anyString(), anyMap())).thenReturn(menuItemStack);
        lenient().when(inventoryManager.getFontImage()).thenReturn(fontImage);
        InventoriesLoader inventoriesLoader = mock(InventoriesLoader.class);
        lenient().when(inventoriesLoader.getInventoryManager()).thenReturn(inventoryManager);
        RuleLoaderRegistry ruleLoaderRegistry = mock(RuleLoaderRegistry.class);

        AuctionPlugin plugin = MockPlugins.plugin(dataFolder);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getInventoriesLoader()).thenReturn(inventoriesLoader);
        lenient().when(plugin.getRuleLoaderRegistry()).thenReturn(ruleLoaderRegistry);
        return plugin;
    }

    /**
     * Joueur mocke qui ne possede QUE les permissions listees.
     */
    public static Player playerWithPermissions(String name, String... permissions) {
        Set<String> granted = Set.of(permissions);
        Player player = MockPlugins.player(name);
        lenient().when(player.hasPermission(anyString())).thenAnswer(invocation -> granted.contains(invocation.<String>getArgument(0)));
        return player;
    }
}
