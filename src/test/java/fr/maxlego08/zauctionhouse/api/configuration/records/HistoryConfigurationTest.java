package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : 500 entrees, pas d'expiration")
    void of_defaults() {
        HistoryConfiguration history = HistoryConfiguration.of(plugin, new YamlConfiguration());

        assertEquals(500, history.maxEntries());
        assertEquals(0, history.expireAfterDays());
    }

    @Test
    @DisplayName("valeurs explicites lues, y compris negatives (aucune validation)")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("history.max-entries", 25);
        yaml.set("history.expire-after-days", -3);

        HistoryConfiguration history = HistoryConfiguration.of(plugin, yaml);

        assertEquals(25, history.maxEntries());
        assertEquals(-3, history.expireAfterDays());
    }
}
