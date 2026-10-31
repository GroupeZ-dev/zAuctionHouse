package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.menu.api.button.DefaultButtonValue;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Chargement des fichiers de configuration REELS du plugin (src/main/resources) pour les tests
 * de chargeurs : les loaders zMenu sont ainsi exerces sur les sections YAML livrees.
 */
public final class TestResources {

    private TestResources() {
    }

    /**
     * Charge une ressource du classpath (par exemple {@code inventories/auction.yml}) en
     * {@link YamlConfiguration}.
     */
    public static YamlConfiguration yaml(String classpathResource) {
        String resource = classpathResource.startsWith("/") ? classpathResource : "/" + classpathResource;
        try (InputStream stream = TestResources.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalArgumentException("Resource not found on the classpath: " + resource);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to read " + resource, exception);
        }
    }

    /**
     * Charge une configuration YAML depuis une chaine : pratique pour les cas limites qui ne
     * figurent pas dans les fichiers livres.
     */
    public static YamlConfiguration yamlOf(String content) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(content);
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid YAML test content", exception);
        }
        return configuration;
    }

    /**
     * Valeurs par defaut d'un bouton zMenu telles que le loader les recoit (inventaire de 54
     * slots, aucune matrice). Le fichier n'est lu par aucun loader du plugin, il sert
     * uniquement aux messages d'erreur.
     */
    public static DefaultButtonValue defaultButtonValue(File file) {
        return new DefaultButtonValue(54, new HashMap<>(), file);
    }

    public static DefaultButtonValue defaultButtonValue() {
        return defaultButtonValue(new File("inventories/test.yml"));
    }

    /**
     * Chemin d'un bouton tel que zMenu le transmet aux loaders : {@code items.<cle>.} AVEC le
     * point final (voir par exemple TitleAnimationLoader dans zmenu-api).
     */
    public static String buttonPath(String key) {
        return "items." + key + ".";
    }
}
