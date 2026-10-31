package fr.maxlego08.zauctionhouse.utils.yaml;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YamlUpdaterTest {

    private static final String RESOURCE = "config.yml";

    private static final String DEFAULTS = """
            # Main configuration
            # second line of header

            # Enable debug
            enable-debug: false

            # New root key comment
            new-key: 5

            database:
              # Host of the database
              host: localhost
              # Port of the database
              port: 3306
              user: root

            # Command aliases
            aliases:
              - ah
              - hdv

            # Quoted string
            prefix: 'Auction: '
            """;

    @TempDir
    File dataFolder;

    private Plugin plugin;
    private Logger logger;
    private YamlUpdater updater;
    private File target;

    @BeforeEach
    void setUp() {
        this.plugin = mock(Plugin.class);
        this.logger = mock(Logger.class);
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder);
        lenient().when(plugin.getLogger()).thenReturn(logger);
        resource(DEFAULTS);
        this.updater = new YamlUpdater(plugin);
        this.target = new File(dataFolder, RESOURCE);
    }

    private void resource(String content) {
        // Un flux neuf a chaque appel : le flux est consomme par l'updater.
        lenient().when(plugin.getResource(RESOURCE))
                .thenAnswer(invocation -> content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void writeTarget(String content) throws Exception {
        Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
    }

    private String readTarget() throws Exception {
        return Files.readString(target.toPath(), StandardCharsets.UTF_8);
    }

    private YamlConfiguration loadTarget() {
        return YamlConfiguration.loadConfiguration(target);
    }

    @Test
    @DisplayName("fichier cible absent : la ressource est copiee telle quelle et update rend true")
    void update_missingTarget_savesResource() {
        assertTrue(updater.update(RESOURCE, RESOURCE));
        verify(plugin).saveResource(RESOURCE, false);
    }

    @Test
    @DisplayName("ressource introuvable : update rend false et journalise en SEVERE")
    void update_missingResource_returnsFalse() throws Exception {
        writeTarget("enable-debug: true\n");
        resource(null);

        assertFalse(updater.update(RESOURCE, RESOURCE));

        verify(logger).severe("Cannot find resource: " + RESOURCE);
        assertEquals("enable-debug: true\n", readTarget(), "le fichier n'est pas touche");
    }

    @Test
    @DisplayName("aucune cle manquante : update rend false et ne reecrit pas le fichier")
    void update_upToDate_returnsFalse() throws Exception {
        String content = "# my own header\nenable-debug: true # inline\nnew-key: 9\ndatabase:\n  host: db\n  port: 1\n  user: u\naliases:\n  - x\nprefix: 'P'\n";
        writeTarget(content);

        assertFalse(updater.update(RESOURCE, RESOURCE));

        assertEquals(content, readTarget());
        verify(logger, never()).info(anyString());
    }

    @Test
    @DisplayName("cle racine manquante : ajoutee avec son commentaire, les valeurs et commentaires existants sont conserves")
    void update_missingRootKey_addedWithComment() throws Exception {
        writeTarget("""
                # My custom header kept
                enable-debug: true

                database:
                  host: db
                  port: 1
                  user: u
                aliases:
                  - x
                prefix: 'P'
                """);

        assertTrue(updater.update(RESOURCE, RESOURCE));

        String content = readTarget();
        YamlConfiguration configuration = loadTarget();
        assertTrue(content.contains("# My custom header kept"), content);
        assertTrue(content.contains("# New root key comment"), content);
        assertTrue(content.contains("new-key: 5"), content);
        assertEquals(5, configuration.getInt("new-key"));
        assertTrue(configuration.getBoolean("enable-debug"), "la valeur personnalisee n'est pas ecrasee");
        assertEquals("db", configuration.getString("database.host"));
        assertEquals(List.of("x"), configuration.getStringList("aliases"));
        assertEquals("P", configuration.getString("prefix"));
        // La nouvelle cle est inseree apres la cle qui la precede dans le fichier par defaut.
        assertTrue(content.indexOf("new-key: 5") > content.indexOf("enable-debug: true"), content);
        assertTrue(content.indexOf("new-key: 5") < content.indexOf("database:"), content);
    }

    @Test
    @DisplayName("cle imbriquee manquante : inseree dans sa section avec l'indentation et le commentaire")
    void update_missingNestedKey_insertedInSection() throws Exception {
        writeTarget("""
                enable-debug: false
                new-key: 5
                database:
                  host: db
                  user: u
                aliases:
                  - ah
                prefix: 'P'
                """);

        assertTrue(updater.update(RESOURCE, RESOURCE));

        String content = readTarget();
        YamlConfiguration configuration = loadTarget();
        assertEquals(3306, configuration.getInt("database.port"));
        assertEquals("db", configuration.getString("database.host"));
        assertEquals("u", configuration.getString("database.user"));
        assertTrue(content.contains("  port: 3306"), content);
        assertTrue(content.contains("  # Port of the database"), content);
        assertNull(configuration.get("port"), "la cle ne doit pas atterrir a la racine");
    }

    @Test
    @DisplayName("liste manquante : inseree avec ses elements")
    void update_missingList_inserted() throws Exception {
        writeTarget("""
                enable-debug: false
                new-key: 5
                database:
                  host: db
                  port: 3306
                  user: u
                prefix: 'P'
                """);

        assertTrue(updater.update(RESOURCE, RESOURCE));

        YamlConfiguration configuration = loadTarget();
        assertEquals(List.of("ah", "hdv"), configuration.getStringList("aliases"));
        assertEquals("P", configuration.getString("prefix"));
    }

    @Test
    @DisplayName("section entiere manquante : la section et toutes ses cles sont ajoutees")
    void update_missingSection_added() throws Exception {
        writeTarget("""
                enable-debug: false
                new-key: 5
                aliases:
                  - ah
                prefix: 'P'
                """);

        assertTrue(updater.update(RESOURCE, RESOURCE));

        YamlConfiguration configuration = loadTarget();
        assertTrue(configuration.isConfigurationSection("database"));
        assertEquals("localhost", configuration.getString("database.host"));
        assertEquals(3306, configuration.getInt("database.port"));
        assertEquals("root", configuration.getString("database.user"));
    }

    @Test
    @DisplayName("fichier corrompu : sauvegarde en .backup puis remplacement par les valeurs par defaut")
    void update_corruptedTarget_backupAndReplace() throws Exception {
        // Une tabulation en indentation est un caractere interdit par le parseur YAML.
        String corrupted = "enable-debug: true\n\tbad: tab\n";
        writeTarget(corrupted);

        assertTrue(updater.update(RESOURCE, RESOURCE));

        File backup = new File(dataFolder, RESOURCE + ".backup");
        assertTrue(backup.exists());
        assertEquals(corrupted, Files.readString(backup.toPath(), StandardCharsets.UTF_8));
        YamlConfiguration configuration = loadTarget();
        assertEquals("localhost", configuration.getString("database.host"));
        assertEquals(5, configuration.getInt("new-key"));
        verify(logger).warning(anyString());
    }

    @Test
    @DisplayName("fichier non vide sans aucune cle : traite comme corrompu")
    void update_noKeysButContent_backupAndReplace() throws Exception {
        writeTarget("# only a comment\n");

        assertTrue(updater.update(RESOURCE, RESOURCE));

        assertTrue(new File(dataFolder, RESOURCE + ".backup").exists());
        assertEquals(false, loadTarget().getBoolean("enable-debug"));
        assertTrue(loadTarget().contains("database.port"));
    }

    @Test
    @DisplayName("une seconde execution apres mise a jour ne trouve plus rien a faire")
    void update_isIdempotent() throws Exception {
        writeTarget("enable-debug: true\n");

        assertTrue(updater.update(RESOURCE, RESOURCE));
        String afterFirst = readTarget();
        assertFalse(updater.update(RESOURCE, RESOURCE));
        assertEquals(afterFirst, readTarget());

        YamlConfiguration configuration = loadTarget();
        assertTrue(configuration.getBoolean("enable-debug"));
        assertEquals(5, configuration.getInt("new-key"));
        assertEquals("localhost", configuration.getString("database.host"));
        assertEquals(List.of("ah", "hdv"), configuration.getStringList("aliases"));
        assertEquals("Auction: ", configuration.getString("prefix"));
    }

    @Test
    @DisplayName("un chemin cible different du chemin de ressource est respecte")
    void update_differentTargetPath() {
        assertTrue(updater.update(RESOURCE, "lang/fr/config.yml"));
        verify(plugin).saveResource(RESOURCE, false);
    }
}
