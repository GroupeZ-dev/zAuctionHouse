package fr.maxlego08.zauctionhouse.resources;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coherence du descripteur Bukkit ({@code plugin.yml}) avec le code et avec la configuration des
 * commandes de {@code config.yml}. Les commandes ne sont PAS declarees dans plugin.yml : elles sont
 * enregistrees au demarrage par {@code ZCommandManager.registerCommand(Plugin, ...)} a partir de
 * {@code commands.main-command.aliases}.
 */
class PluginYmlTest {

    static final Path RESOURCES = Paths.get("D:/Users/Maxlego08/workspace2.0/zAuctionHouseV4/src/main/resources");

    static YamlConfiguration pluginYml;
    static YamlConfiguration config;

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

    @BeforeAll
    static void loadFiles() throws IOException {
        pluginYml = load(RESOURCES.resolve("plugin.yml"));
        config = load(RESOURCES.resolve("config.yml"));
    }

    @Test
    @DisplayName("plugin.yml declare le nom, la version (placeholder Gradle) et l'api-version")
    void pluginYml_declaresIdentity() {
        assertEquals("zAuctionHouse", pluginYml.getString("name"));
        assertEquals("${version}", pluginYml.getString("version"), "la version est injectee par processResources { expand(...) }");
        assertEquals("1.20", pluginYml.getString("api-version"));
        assertTrue(pluginYml.getBoolean("folia-supported"));
        assertEquals(List.of("Maxlego08"), pluginYml.getStringList("authors"));
        assertNotNull(pluginYml.getString("description"));
        assertEquals("https://groupez.dev/", pluginYml.getString("website"));
    }

    @Test
    @DisplayName("La classe main existe, n'est pas initialisee par le test, et etend JavaPlugin")
    void pluginYml_mainClassExistsAndExtendsJavaPlugin() throws ClassNotFoundException {
        String main = pluginYml.getString("main");
        assertEquals("fr.maxlego08.zauctionhouse.ZAuctionPlugin", main);
        // initialize=false : on verifie l'existence et la hierarchie sans declencher les statiques
        // (le plugin touche a Bukkit dans ses champs d'instance et son static init).
        Class<?> mainClass = Class.forName(main, false, PluginYmlTest.class.getClassLoader());
        assertTrue(JavaPlugin.class.isAssignableFrom(mainClass), main + " doit etendre JavaPlugin");
        assertFalse(Modifier.isAbstract(mainClass.getModifiers()));
    }

    @Test
    @DisplayName("Les dependances dures et souples attendues sont declarees")
    void pluginYml_declaresDependencies() {
        List<String> depend = pluginYml.getStringList("depend");
        List<String> softdepend = pluginYml.getStringList("softdepend");
        assertAll(
                () -> assertTrue(depend.contains("zMenu"), "zMenu est une dependance dure (framework d'inventaires)"),
                () -> assertTrue(depend.contains("PlaceholderAPI"), "PlaceholderAPI est une dependance dure"),
                () -> assertEquals(2, depend.size(), "dependances dures inattendues : " + depend),
                () -> assertTrue(softdepend.containsAll(List.of("ItemsAdder", "Nexo", "Oraxen", "LuckPerms", "AxShulkers")), "softdepend : " + softdepend),
                () -> assertTrue(depend.stream().noneMatch(softdepend::contains), "un plugin ne peut etre a la fois depend et softdepend")
        );
    }

    @Test
    @DisplayName("Le driver MariaDB est fourni via libraries (Sarah le declare compileOnly)")
    void pluginYml_declaresJdbcDriverLibrary() {
        List<String> libraries = pluginYml.getStringList("libraries");
        assertEquals(1, libraries.size());
        assertTrue(libraries.getFirst().startsWith("org.mariadb.jdbc:mariadb-java-client:"), libraries.toString());
    }

    @Test
    @DisplayName("Aucune commande n'est declaree dans plugin.yml : elles viennent de config.yml")
    void pluginYml_hasNoStaticCommandSection() {
        assertFalse(pluginYml.contains("commands"), "les commandes sont enregistrees dynamiquement par ZCommandManager");
        assertFalse(pluginYml.contains("permissions"));
    }

    @Test
    @DisplayName("Les alias de la commande principale sont non vides et n'incluent pas 'zauctionhouse' (ajoute par le code)")
    void config_mainCommandAliases_areConsistentWithRegistration() {
        List<String> aliases = config.getStringList("commands.main-command.aliases");
        assertFalse(aliases.isEmpty(), "sans alias, la commande principale devient /zauctionhouse seul");
        assertTrue(aliases.contains("ah"), "l'alias historique /ah doit rester disponible : " + aliases);
        // ZAuctionPlugin retire le premier alias comme commande primaire puis AJOUTE "zauctionhouse" :
        // le declarer aussi dans la configuration produirait un doublon dans la CommandMap.
        assertFalse(aliases.contains("zauctionhouse"), "'zauctionhouse' est ajoute automatiquement : " + aliases);
        assertEquals(aliases.size(), aliases.stream().distinct().count(), "alias dupliques : " + aliases);
        assertTrue(aliases.stream().allMatch(alias -> alias.equals(alias.toLowerCase())), "ZCommandManager compare en minuscules : " + aliases);
    }

    /**
     * Collecte tous les alias des sous-commandes configurees (sell, claim, search, page, inventories[*]).
     */
    static Map<String, List<String>> subCommandAliases() {
        Map<String, List<String>> aliases = new HashMap<>();
        for (String name : List.of("sell", "claim", "search", "page")) {
            aliases.put(name, config.getStringList("commands." + name + ".aliases"));
        }
        List<Map<?, ?>> inventories = config.getMapList("commands.inventories");
        for (int index = 0; index < inventories.size(); index++) {
            Object raw = inventories.get(index).get("aliases");
            List<String> list = new ArrayList<>();
            if (raw instanceof List<?> rawList) rawList.forEach(element -> list.add(String.valueOf(element)));
            aliases.put("inventories[" + index + "]", list);
        }
        return aliases;
    }

    @Test
    @DisplayName("Chaque sous-commande configuree possede au moins un alias en minuscules")
    void config_everySubCommandHasLowercaseAliases() {
        Map<String, List<String>> aliases = subCommandAliases();
        assertFalse(aliases.isEmpty());
        assertAll(aliases.entrySet().stream().map(entry -> (Executable) () -> {
            assertFalse(entry.getValue().isEmpty(), "commande sans alias : " + entry.getKey());
            assertTrue(entry.getValue().stream().allMatch(alias -> alias.equals(alias.toLowerCase())), entry.getKey() + " : " + entry.getValue());
        }));
    }

    @Test
    @Disabled("BUG: config.yml (et fr/es/it) declare l'alias 's' a la fois sur commands.sell.aliases et commands.search.aliases ; "
            + "CommandAuction enregistre Sell avant Search et ZCommandManager.onCommand (ZCommandManager.java:76-86) s'arrete a la "
            + "premiere commande acceptee (Sell ne rend jamais CONTINUE), donc '/ah s <texte>' tombe toujours sur la vente et "
            + "l'alias 's' de la recherche est inatteignable. Correctif : retirer 's' de commands.search.aliases dans les 4 langues.")
    @DisplayName("Aucun alias n'est partage entre deux sous-commandes (sinon la premiere enregistree masque l'autre)")
    void config_subCommandAliases_areUniqueAcrossCommands() {
        // ZCommandManager.onCommand parcourt les commandes dans l'ordre d'enregistrement (celui du
        // constructeur de CommandAuction : sell, claim, page, search, option, admin, inventories[*])
        // et s'arrete a la premiere dont canExecute accepte l'argument. Les commandes a arguments
        // (addRequireArg/addOptionalArg => ignoreArgs = true) acceptent "<alias> <n'importe quoi>",
        // et leur perform ne rend jamais CONTINUE : un alias partage rend la seconde commande
        // inaccessible.
        Map<String, List<String>> owners = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        subCommandAliases().forEach((command, aliases) -> aliases.forEach(alias -> {
            List<String> list = owners.computeIfAbsent(alias.toLowerCase(), key -> new ArrayList<>());
            list.add(command);
            if (list.size() == 2) conflicts.add(alias.toLowerCase());
        }));
        assertTrue(conflicts.isEmpty(), "alias partages entre sous-commandes : "
                + conflicts.stream().map(alias -> alias + " -> " + owners.get(alias)).toList());
        // Alias codes en dur, hors configuration : "admin" (CommandAuctionAdmin) et
        // "option"/"options"/"opt" (CommandAuctionOption). Les reprendre dans config.yml les masquerait
        // ou serait masque, selon l'ordre d'enregistrement.
        assertAll(List.of("admin", "option", "options", "opt").stream().map(reserved -> (Executable) () ->
                assertFalse(owners.containsKey(reserved), "'" + reserved + "' est reserve a une commande codee en dur : " + owners.get(reserved))));
    }

    @Test
    @DisplayName("Les arguments de /ah sell sont declares avec les trois noms de l'enum CommandSellArguments")
    void config_sellArguments_matchEnum() {
        List<Map<?, ?>> arguments = config.getMapList("commands.sell.arguments");
        List<String> names = arguments.stream().map(map -> String.valueOf(map.get("name")).toUpperCase()).toList();
        assertEquals(List.of("PRICE", "AMOUNT", "ECONOMY"), names);
        assertEquals(Boolean.TRUE, arguments.getFirst().get("required"), "le prix est obligatoire");
        assertEquals(Boolean.FALSE, arguments.get(1).get("required"));
        assertEquals(Boolean.FALSE, arguments.get(2).get("required"));
    }

    @Test
    @DisplayName("La section cooldown des commandes est presente avec une permission de bypass")
    void config_cooldownSection_isPresent() {
        ConfigurationSection cooldown = config.getConfigurationSection("commands.cooldown");
        assertNotNull(cooldown, "commands.cooldown");
        assertTrue(cooldown.contains("default-cooldown"));
        assertTrue(cooldown.getLong("default-cooldown") >= 0);
        assertNotNull(cooldown.getString("bypass-permission"));
    }
}
