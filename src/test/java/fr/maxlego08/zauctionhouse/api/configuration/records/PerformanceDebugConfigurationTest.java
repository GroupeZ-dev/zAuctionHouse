package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceDebugConfiguration.FilterMode;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceDebugConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    private PerformanceDebugConfiguration build(String mode, List<String> operations) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (mode != null) configuration.set("performance-debug.filter.mode", mode);
        configuration.set("performance-debug.filter.operations", operations);
        return PerformanceDebugConfiguration.of(plugin, configuration);
    }

    @Test
    @DisplayName("configuration vide : DISABLED, aucune operation, tout est journalise")
    void of_defaults() {
        PerformanceDebugConfiguration configuration = PerformanceDebugConfiguration.of(plugin, new YamlConfiguration());

        assertEquals(FilterMode.DISABLED, configuration.mode());
        assertTrue(configuration.operations().isEmpty());
        assertTrue(configuration.shouldLog("anything"));
    }

    @ParameterizedTest(name = "mode \"{0}\" accepte")
    @ValueSource(strings = {"whitelist", "WHITELIST", "Whitelist"})
    void of_mode_caseInsensitive(String mode) {
        assertEquals(FilterMode.WHITELIST, build(mode, List.of()).mode());
    }

    @Test
    @DisplayName("mode invalide : DISABLED")
    void of_invalidMode_disabled() {
        PerformanceDebugConfiguration configuration = build("GREYLIST", List.of("x"));

        assertEquals(FilterMode.DISABLED, configuration.mode());
        assertTrue(configuration.shouldLog("y"), "DISABLED journalise tout, meme hors liste");
    }

    @Test
    @DisplayName("WHITELIST : seules les operations exactes ou prefixees par un joker passent")
    void shouldLog_whitelist() {
        PerformanceDebugConfiguration configuration = build("WHITELIST", List.of("loadItems.*", "SortedItemsCache.refresh"));

        assertTrue(configuration.shouldLog("loadItems.all"));
        assertTrue(configuration.shouldLog("loadItems."));
        assertTrue(configuration.shouldLog("SortedItemsCache.refresh"));
        assertFalse(configuration.shouldLog("SortedItemsCache.refreshAll"), "sans joker, correspondance exacte seulement");
        assertFalse(configuration.shouldLog("loadItems"), "le prefixe inclut le point");
        assertFalse(configuration.shouldLog("other"));
    }

    @Test
    @DisplayName("BLACKLIST : l'inverse de la whitelist")
    void shouldLog_blacklist() {
        PerformanceDebugConfiguration configuration = build("BLACKLIST", List.of("loadItems.*", "SortedItemsCache.refresh"));

        assertFalse(configuration.shouldLog("loadItems.all"));
        assertFalse(configuration.shouldLog("SortedItemsCache.refresh"));
        assertTrue(configuration.shouldLog("SortedItemsCache.refreshAll"));
        assertTrue(configuration.shouldLog("other"));
    }

    @Test
    @DisplayName("listes vides : WHITELIST ne journalise rien, BLACKLIST tout")
    void shouldLog_emptyOperations() {
        assertFalse(build("WHITELIST", List.of()).shouldLog("x"));
        assertTrue(build("BLACKLIST", List.of()).shouldLog("x"));
    }

    @Test
    @DisplayName("joker seul : tout correspond")
    void shouldLog_starAlone_matchesEverything() {
        assertTrue(build("WHITELIST", List.of("*")).shouldLog("anything"));
        assertFalse(build("BLACKLIST", List.of("*")).shouldLog("anything"));
    }
}
