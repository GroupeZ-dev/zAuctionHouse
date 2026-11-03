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

class AutoClaimConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : desactive, 20 ticks, notifications actives, raison par defaut")
    void of_defaults() {
        AutoClaimConfiguration autoClaim = AutoClaimConfiguration.of(plugin, new YamlConfiguration());

        assertFalse(autoClaim.enabled());
        assertEquals(20L, autoClaim.delayTicks());
        assertTrue(autoClaim.notifyPlayer());
        assertTrue(autoClaim.notifyPending());
        assertEquals(40L, autoClaim.notifyDelayTicks());
        assertEquals("Claimed pending auction money", autoClaim.depositReason());
    }

    @Test
    @DisplayName("valeurs explicites lues")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("auto-claim.enable", true);
        yaml.set("auto-claim.delay-ticks", 5);
        yaml.set("auto-claim.notify-player", false);
        yaml.set("auto-claim.notify-pending", false);
        yaml.set("auto-claim.notify-delay-ticks", 100);
        yaml.set("auto-claim.deposit-reason", "Vente HDV");

        AutoClaimConfiguration autoClaim = AutoClaimConfiguration.of(plugin, yaml);

        assertTrue(autoClaim.enabled());
        assertEquals(5L, autoClaim.delayTicks());
        assertFalse(autoClaim.notifyPlayer());
        assertFalse(autoClaim.notifyPending());
        assertEquals(100L, autoClaim.notifyDelayTicks());
        assertEquals("Vente HDV", autoClaim.depositReason());
    }
}
