package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : seuils et timeouts par defaut")
    void of_defaults() {
        PerformanceConfiguration configuration = PerformanceConfiguration.of(plugin, new YamlConfiguration());

        assertEquals(10_000, configuration.parallelSortThreshold());
        assertEquals(5_000, configuration.parallelCategoryThreshold());
        assertEquals(-1, configuration.parallelism());
        assertEquals(5_000L, configuration.checkAvailabilityTimeoutMs());
        assertEquals(5_000L, configuration.lockItemTimeoutMs());
        assertEquals(3_000L, configuration.unlockItemTimeoutMs());
        assertEquals(3_000L, configuration.notifyStatusChangeTimeoutMs());
        assertEquals(5_000L, configuration.notifyItemActionTimeoutMs());
    }

    @Test
    @DisplayName("valeurs explicites lues")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("performance.cache.parallel-sort-threshold", 1);
        yaml.set("performance.cache.parallel-category-threshold", 2);
        yaml.set("performance.cache.parallelism", 4);
        yaml.set("performance.cluster-timeout.check-availability-ms", 10);
        yaml.set("performance.cluster-timeout.lock-item-ms", 20);
        yaml.set("performance.cluster-timeout.unlock-item-ms", 30);
        yaml.set("performance.cluster-timeout.notify-status-change-ms", 40);
        yaml.set("performance.cluster-timeout.notify-item-action-ms", 50);

        PerformanceConfiguration configuration = PerformanceConfiguration.of(plugin, yaml);

        assertEquals(1, configuration.parallelSortThreshold());
        assertEquals(2, configuration.parallelCategoryThreshold());
        assertEquals(4, configuration.parallelism());
        assertEquals(4, configuration.getEffectiveParallelism());
        assertEquals(10L, configuration.checkAvailabilityTimeoutMs());
        assertEquals(20L, configuration.lockItemTimeoutMs());
        assertEquals(30L, configuration.unlockItemTimeoutMs());
        assertEquals(40L, configuration.notifyStatusChangeTimeoutMs());
        assertEquals(50L, configuration.notifyItemActionTimeoutMs());
    }

    @ParameterizedTest(name = "parallelism {0} => derive des coeurs disponibles, au moins 2")
    @ValueSource(ints = {0, -1, -8})
    void getEffectiveParallelism_nonPositive_derivedFromCores(int parallelism) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("performance.cache.parallelism", parallelism);

        int effective = PerformanceConfiguration.of(plugin, yaml).getEffectiveParallelism();

        assertTrue(effective >= 2);
        assertEquals(Math.max(2, Runtime.getRuntime().availableProcessors() - 1), effective);
    }

    @Test
    @DisplayName("parallelism 1 : respecte tel quel")
    void getEffectiveParallelism_one_kept() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("performance.cache.parallelism", 1);

        assertEquals(1, PerformanceConfiguration.of(plugin, yaml).getEffectiveParallelism());
    }
}
