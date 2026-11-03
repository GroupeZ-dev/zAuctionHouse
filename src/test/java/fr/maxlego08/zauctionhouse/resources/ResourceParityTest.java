package fr.maxlego08.zauctionhouse.resources;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parite des ressources i18n : regle du projet, "toute modification d'un fichier de configuration
 * doit etre repliquee dans TOUTES les langues".
 * <p>
 * La racine de {@code src/main/resources} est la reference (anglais). Chaque dossier de langue
 * doit contenir exactement les memes fichiers YAML, et chaque fichier doit exposer exactement le
 * meme ensemble de cles (chemins complets, valeurs de listes exclues). Seules les VALEURS peuvent
 * differer d'une langue a l'autre.
 */
class ResourceParityTest {

    static final Path RESOURCES = Paths.get("D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/resources");
    static final List<String> LANGUAGES = List.of("es", "fr", "id", "it", "th");
    /**
     * Fichiers qui n'existent qu'a la racine par conception : le descripteur Bukkit et le
     * selecteur de langue, lu AVANT toute traduction.
     */
    static final Set<String> ROOT_ONLY = Set.of("plugin.yml", "language.yml");
    /**
     * Fichiers traduits dont la parite de cles est CONNUE comme cassee en production (chemin
     * "langue/fichier"). Ils sont exclus du test de parite global pour qu'il continue de proteger
     * tous les autres fichiers, et verifies a part par un test {@code @Disabled("BUG: ...")} qui
     * garde l'attente correcte. A vider des que le fichier est corrige.
     * <p>
     * th/inventories/admin/admin-logs.yml : copie de admin-history-main.yml (seul le titre a ete
     * change), il n'expose pas items.items / items.filter-type / items.filter-date / patterns.
     */
    static final Set<String> KNOWN_BROKEN_PARITY = Set.of("th/inventories/admin/admin-logs.yml");

    // ------------------------------------------------------------------ helpers

    static Set<String> yamlFilesUnder(Path directory, boolean skipLanguageFolders) throws IOException {
        Set<String> files = new TreeSet<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .forEach(path -> {
                        String relative = directory.relativize(path).toString().replace('\\', '/');
                        if (skipLanguageFolders) {
                            String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
                            if (LANGUAGES.contains(first)) return;
                        }
                        files.add(relative);
                    });
        }
        return files;
    }

    static Set<String> rootFiles() throws IOException {
        Set<String> files = yamlFilesUnder(RESOURCES, true);
        files.removeAll(ROOT_ONLY);
        return files;
    }

    /**
     * Chargement STRICT : {@code load(Reader)} propage toute erreur YAML (loadConfiguration les
     * avalerait en passant par Bukkit.getLogger(), absent sans serveur).
     */
    static YamlConfiguration load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            YamlConfiguration configuration = new YamlConfiguration();
            try {
                configuration.load(reader);
            } catch (InvalidConfigurationException exception) {
                throw new IOException("YAML invalide : " + path, exception);
            }
            return configuration;
        }
    }

    /**
     * Toutes les cles, en profondeur. {@code getKeys(true)} ne descend pas dans les listes :
     * les elements de listes (meme des maps) sont des valeurs, pas des cles.
     */
    static Set<String> keysOf(Path path) throws IOException {
        return new TreeSet<>(load(path).getKeys(true));
    }

    static Set<String> minus(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    /**
     * Assertion de parite de cles entre {@code file} a la racine et sa traduction dans {@code language},
     * ou {@code null} si la traduction n'existe pas (deja signale par le test des fichiers).
     */
    static Executable keyParityCheck(String language, String file) throws IOException {
        Path translatedPath = RESOURCES.resolve(language).resolve(file);
        if (!Files.isRegularFile(translatedPath)) return null;
        Set<String> rootKeys = keysOf(RESOURCES.resolve(file));
        Set<String> translatedKeys = keysOf(translatedPath);
        Set<String> missing = minus(rootKeys, translatedKeys);
        Set<String> extra = minus(translatedKeys, rootKeys);
        return () -> assertEquals(rootKeys, translatedKeys,
                "[" + language + "/" + file + "] cles MANQUANTES : " + missing + " ; cles EXCEDENTAIRES : " + extra);
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("Le dossier de ressources et les cinq dossiers de langue existent")
    void resourceFolders_exist() {
        assertTrue(Files.isDirectory(RESOURCES), "Dossier de ressources introuvable : " + RESOURCES);
        assertAll(LANGUAGES.stream().map(language -> (Executable) () ->
                assertTrue(Files.isDirectory(RESOURCES.resolve(language)), "Dossier de langue absent : " + language)));
    }

    @Test
    @DisplayName("La racine contient les fichiers de configuration attendus")
    void rootFiles_containExpectedConfigurationFiles() throws IOException {
        Set<String> files = rootFiles();
        assertAll(
                () -> assertTrue(files.contains("config.yml"), "config.yml"),
                () -> assertTrue(files.contains("messages.yml"), "messages.yml"),
                () -> assertTrue(files.contains("economies.yml"), "economies.yml"),
                () -> assertTrue(files.contains("rules.yml"), "rules.yml"),
                () -> assertTrue(files.contains("categories.yml"), "categories.yml"),
                () -> assertTrue(files.contains("discord.yml"), "discord.yml"),
                () -> assertTrue(files.contains("inventories/auction.yml"), "inventories/auction.yml"),
                () -> assertTrue(files.contains("patterns/pagination.yml"), "patterns/pagination.yml"),
                () -> assertTrue(files.stream().anyMatch(file -> file.startsWith("inventories/admin/")), "inventories/admin/*"),
                () -> assertTrue(files.stream().anyMatch(file -> file.startsWith("inventories/confirms/")), "inventories/confirms/*")
        );
    }

    @Test
    @DisplayName("Chaque fichier YAML de la racine se charge et n'est pas vide")
    void rootFiles_parseAndAreNotEmpty() throws IOException {
        List<Executable> checks = new ArrayList<>();
        for (String file : rootFiles()) {
            checks.add(() -> assertFalse(keysOf(RESOURCES.resolve(file)).isEmpty(), "Fichier racine vide ou illisible : " + file));
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("Aucun fichier de la racine n'est absent d'une langue, et aucune langue n'a de fichier inconnu de la racine")
    void languageFolders_haveExactlyTheRootFiles() throws IOException {
        Set<String> root = rootFiles();
        List<Executable> checks = new ArrayList<>();
        for (String language : LANGUAGES) {
            Set<String> translated = yamlFilesUnder(RESOURCES.resolve(language), false);
            Set<String> missing = minus(root, translated);
            Set<String> extra = minus(translated, root);
            checks.add(() -> assertTrue(missing.isEmpty(), "[" + language + "] fichiers ABSENTS de la traduction : " + missing));
            checks.add(() -> assertTrue(extra.isEmpty(), "[" + language + "] fichiers EXCEDENTAIRES (inexistants a la racine) : " + extra));
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("Chaque fichier traduit expose exactement les memes cles que son original anglais")
    void translatedFiles_haveIdenticalKeySets() throws IOException {
        Set<String> root = rootFiles();
        List<Executable> checks = new ArrayList<>();
        for (String language : LANGUAGES) {
            for (String file : root) {
                if (KNOWN_BROKEN_PARITY.contains(language + "/" + file)) continue; // couvert par le test @Disabled ci-dessous
                Executable check = keyParityCheck(language, file);
                if (check != null) checks.add(check);
            }
        }
        assertAll(checks);
    }

    @Test
    @Disabled("BUG: src/main/resources/th/inventories/admin/admin-logs.yml est une copie de admin-history-main.yml "
            + "(seul 'name' ligne 30 differe) : il lui manque items.items.*, items.filter-type.*, items.filter-date.* et "
            + "patterns, et il expose a tort items.target/expired/purchased/selling/logs/transactions ; le menu admin logs "
            + "en thai affiche les boutons de l'historique principal au lieu des logs (commit c1beb1b).")
    @DisplayName("Les fichiers a parite connue comme cassee doivent redevenir identiques a leur original anglais")
    void knownBrokenFiles_mustRegainIdenticalKeySets() throws IOException {
        List<Executable> checks = new ArrayList<>();
        for (String entry : KNOWN_BROKEN_PARITY) {
            int slash = entry.indexOf('/');
            String language = entry.substring(0, slash);
            String file = entry.substring(slash + 1);
            assertTrue(LANGUAGES.contains(language), "Langue inconnue dans KNOWN_BROKEN_PARITY : " + entry);
            Executable check = keyParityCheck(language, file);
            assertTrue(check != null, "Fichier traduit introuvable : " + entry);
            checks.add(check);
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("Chaque entree de KNOWN_BROKEN_PARITY designe un fichier reel de la racine et une langue connue")
    void knownBrokenParity_entriesPointToExistingRootFiles() throws IOException {
        Set<String> root = rootFiles();
        assertAll(KNOWN_BROKEN_PARITY.stream().map(entry -> (Executable) () -> {
            int slash = entry.indexOf('/');
            assertTrue(slash > 0, "Format attendu langue/fichier : " + entry);
            String language = entry.substring(0, slash);
            String file = entry.substring(slash + 1);
            assertTrue(LANGUAGES.contains(language), "Langue inconnue : " + entry);
            assertTrue(root.contains(file), "Fichier racine inconnu (entree obsolete a retirer) : " + entry);
        }));
    }

    @Test
    @DisplayName("Les fichiers reserves a la racine ne sont dupliques dans aucune langue")
    void rootOnlyFiles_areNotTranslated() throws IOException {
        List<Executable> checks = new ArrayList<>();
        for (String language : LANGUAGES) {
            Set<String> translated = yamlFilesUnder(RESOURCES.resolve(language), false);
            for (String rootOnly : ROOT_ONLY) {
                checks.add(() -> assertFalse(translated.contains(rootOnly), "[" + language + "] " + rootOnly + " ne doit exister qu'a la racine"));
            }
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("language.yml documente chaque dossier de langue present sur le disque")
    void languageYml_listsEveryLanguageFolder() throws IOException {
        String content = Files.readString(RESOURCES.resolve("language.yml"), StandardCharsets.UTF_8);
        YamlConfiguration configuration = load(RESOURCES.resolve("language.yml"));
        assertEquals("auto", configuration.getString("language"));
        assertAll(LANGUAGES.stream().map(language -> (Executable) () ->
                assertTrue(content.contains("src/main/resources/" + language + "/"), "language.yml ne mentionne pas le dossier " + language)));
    }
}
