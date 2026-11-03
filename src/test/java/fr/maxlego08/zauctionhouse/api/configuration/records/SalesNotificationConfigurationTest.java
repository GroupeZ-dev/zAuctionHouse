package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalesNotificationConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : active, 40 ticks")
    void of_defaults() {
        SalesNotificationConfiguration notification = SalesNotificationConfiguration.of(plugin, new YamlConfiguration());

        assertTrue(notification.enabled());
        assertEquals(40L, notification.delayTicks());
    }

    @Test
    @DisplayName("valeurs explicites lues")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sales-notification.enable", false);
        yaml.set("sales-notification.delay-ticks", 0);

        SalesNotificationConfiguration notification = SalesNotificationConfiguration.of(plugin, yaml);

        assertFalse(notification.enabled());
        assertEquals(0L, notification.delayTicks());
    }
}
