package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    private MaintenanceConfiguration build(Object sweepInterval, Object batchSize, Object confirmationTimeout, Object confirmationInterval) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("maintenance.expiration-sweep-interval-seconds", sweepInterval);
        configuration.set("maintenance.expiration-sweep-batch-size", batchSize);
        configuration.set("maintenance.confirmation-timeout-seconds", confirmationTimeout);
        configuration.set("maintenance.confirmation-sweep-interval-seconds", confirmationInterval);
        return MaintenanceConfiguration.of(plugin, configuration);
    }

    @Test
    @DisplayName("defaults() et une configuration vide sont identiques")
    void defaults_matchEmptyConfiguration() {
        MaintenanceConfiguration defaults = MaintenanceConfiguration.defaults();

        assertEquals(60L, defaults.expirationSweepIntervalSeconds());
        assertEquals(50, defaults.expirationSweepBatchSize());
        assertEquals(60L, defaults.confirmationTimeoutSeconds());
        assertEquals(10L, defaults.confirmationSweepIntervalSeconds());
        assertEquals(defaults, MaintenanceConfiguration.of(plugin, new YamlConfiguration()));
        assertTrue(defaults.isExpirationSweepEnabled());
        assertTrue(defaults.isConfirmationSweepEnabled());
    }

    @Test
    @DisplayName("valeurs explicites valides conservees")
    void of_validValues_kept() {
        MaintenanceConfiguration configuration = build(120, 25, 90, 5);

        assertEquals(120L, configuration.expirationSweepIntervalSeconds());
        assertEquals(25, configuration.expirationSweepBatchSize());
        assertEquals(90L, configuration.confirmationTimeoutSeconds());
        assertEquals(5L, configuration.confirmationSweepIntervalSeconds());
    }

    @ParameterizedTest(name = "batch-size {0} => 50")
    @ValueSource(ints = {0, -1, -50})
    void of_nonPositiveBatchSize_fallsBack(int batchSize) {
        assertEquals(MaintenanceConfiguration.DEFAULT_EXPIRATION_SWEEP_BATCH_SIZE, build(60, batchSize, 60, 10).expirationSweepBatchSize());
    }

    @ParameterizedTest(name = "confirmation-sweep-interval {0} => 10")
    @ValueSource(ints = {0, -5})
    void of_nonPositiveConfirmationInterval_fallsBack(int interval) {
        assertEquals(MaintenanceConfiguration.DEFAULT_CONFIRMATION_SWEEP_INTERVAL_SECONDS, build(60, 50, 60, interval).confirmationSweepIntervalSeconds());
    }

    @ParameterizedTest(name = "confirmation-timeout {0} => {1}")
    @CsvSource({
            "1, 60",
            "30, 60",
            "59, 60",
            "60, 60",
            "61, 61",
            "600, 600",
    })
    void of_confirmationTimeout_floorAt60(long configured, long expected) {
        assertEquals(expected, build(60, 50, configured, 10).confirmationTimeoutSeconds());
    }

    @Test
    @DisplayName("confirmation-timeout 0 ou negatif : balayage des confirmations desactive, pas de plancher")
    void of_zeroConfirmationTimeout_disablesSweep() {
        assertEquals(0L, build(60, 50, 0, 10).confirmationTimeoutSeconds());
        assertFalse(build(60, 50, 0, 10).isConfirmationSweepEnabled());
        assertEquals(-1L, build(60, 50, -1, 10).confirmationTimeoutSeconds());
        assertFalse(build(60, 50, -1, 10).isConfirmationSweepEnabled());
    }

    @Test
    @DisplayName("expiration-sweep-interval 0 ou negatif : balayage des expirations desactive")
    void of_zeroSweepInterval_disablesSweep() {
        assertFalse(build(0, 50, 60, 10).isExpirationSweepEnabled());
        assertFalse(build(-10, 50, 60, 10).isExpirationSweepEnabled());
        assertTrue(build(1, 50, 60, 10).isExpirationSweepEnabled());
    }
}
