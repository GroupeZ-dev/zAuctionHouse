package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Loadable;
import fr.maxlego08.zauctionhouse.api.configuration.NonLoadable;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlLoaderTest {

    enum Mode {FAST, SLOW}

    public record Rule(String material, int priority) implements Loadable {
    }

    public record Nested(String host, int port) {
    }

    /**
     * Classe statique : une classe interne porterait un champ synthetique {@code this$0} que le
     * chargeur tenterait de lire comme une section.
     */
    static class SampleConfiguration extends YamlLoader {
        boolean enableDebug;
        int maxItems;
        long cooldownMillis;
        String serverName;
        double taxRate;
        float ratio;
        BigDecimal basePrice;
        Mode mode;
        List<String> aliases;
        List<Rule> rules;
        Nested nested;
        @NonLoadable
        int ignored = 42;

        void load(AuctionPlugin plugin, YamlConfiguration configuration) {
            loadYamlConfirmation(plugin, configuration);
        }
    }

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(dataFolder);
    }

    private static YamlConfiguration yaml(String content) {
        return YamlConfiguration.loadConfiguration(new StringReader(content));
    }

    @Test
    @DisplayName("chaque type de champ est charge depuis sa cle kebab-case")
    void load_allFieldTypes() {
        YamlConfiguration configuration = yaml("""
                enable-debug: true
                max-items: 12
                cooldown-millis: 9000000000
                server-name: lobby-1
                tax-rate: 0.15
                ratio: 2.5
                base-price: '19.99'
                mode: slow
                aliases:
                  - ah
                  - hdv
                rules:
                  - material: DIAMOND
                    priority: 2
                  - material: STONE
                    priority: 1
                nested:
                  host: localhost
                  port: 3306
                ignored: 7
                """);

        SampleConfiguration sample = new SampleConfiguration();
        sample.load(plugin, configuration);

        assertTrue(sample.enableDebug);
        assertEquals(12, sample.maxItems);
        assertEquals(9_000_000_000L, sample.cooldownMillis);
        assertEquals("lobby-1", sample.serverName);
        assertEquals(0.15, sample.taxRate, 1e-9);
        assertEquals(2.5f, sample.ratio, 1e-6f);
        assertEquals(new BigDecimal("19.99"), sample.basePrice);
        assertEquals(Mode.SLOW, sample.mode, "l'enum est lu en majuscules");
        assertEquals(List.of("ah", "hdv"), sample.aliases);
        assertEquals(List.of(new Rule("DIAMOND", 2), new Rule("STONE", 1)), sample.rules);
        assertEquals(new Nested("localhost", 3306), sample.nested);
        assertEquals(42, sample.ignored, "@NonLoadable n'est jamais touche");
    }

    @Test
    @DisplayName("fichier vide : valeurs par defaut Bukkit, BigDecimal a zero, enum et section absents laisses null")
    void load_emptyConfiguration_defaults() {
        SampleConfiguration sample = new SampleConfiguration();
        sample.load(plugin, yaml(""));

        assertFalse(sample.enableDebug);
        assertEquals(0, sample.maxItems);
        assertEquals(0L, sample.cooldownMillis);
        assertNull(sample.serverName);
        assertEquals(0.0, sample.taxRate);
        assertEquals(0f, sample.ratio);
        assertEquals(BigDecimal.ZERO, sample.basePrice);
        assertNull(sample.mode, "Enum.valueOf(\"\") echoue, l'erreur est absorbee");
        assertNotNull(sample.aliases);
        assertTrue(sample.aliases.isEmpty());
        assertNotNull(sample.rules);
        assertTrue(sample.rules.isEmpty());
        assertNull(sample.nested);
        assertEquals(42, sample.ignored);
    }

    @Test
    @DisplayName("une valeur invalide sur un champ n'empeche pas le chargement des autres")
    void load_invalidValue_isIsolated() {
        YamlConfiguration configuration = yaml("""
                mode: unknown
                base-price: not-a-number
                max-items: 3
                server-name: ok
                """);

        SampleConfiguration sample = new SampleConfiguration();
        sample.load(plugin, configuration);

        assertNull(sample.mode);
        assertNull(sample.basePrice);
        assertEquals(3, sample.maxItems);
        assertEquals("ok", sample.serverName);
    }

    @Test
    @DisplayName("la section imbriquee accepte les cles kebab-case des composantes du record")
    void load_nestedSection_kebabKeys() {
        YamlConfiguration configuration = yaml("""
                nested:
                  host: db
                  port: '5432'
                """);
        SampleConfiguration sample = new SampleConfiguration();
        sample.load(plugin, configuration);
        assertEquals(new Nested("db", 5432), sample.nested, "le port est converti depuis une chaine");
    }

    @Test
    @DisplayName("une section imbriquee incomplete (primitif manquant) est journalisee et laisse le champ null")
    void load_nestedSection_missingPrimitive_leavesNull() {
        YamlConfiguration configuration = yaml("""
                nested:
                  host: db
                """);
        SampleConfiguration sample = new SampleConfiguration();
        sample.load(plugin, configuration);
        assertNull(sample.nested);
    }
}
