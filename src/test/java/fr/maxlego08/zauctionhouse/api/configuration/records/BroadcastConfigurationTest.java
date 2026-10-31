package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : desactive, exclusions actives, maps vides")
    void of_defaults() {
        BroadcastConfiguration broadcast = BroadcastConfiguration.of(plugin, new YamlConfiguration());

        assertFalse(broadcast.sellEnabled());
        assertFalse(broadcast.purchaseEnabled());
        assertTrue(broadcast.excludeSeller());
        assertTrue(broadcast.excludeBuyer());
        assertTrue(broadcast.categoryMessagesSell().isEmpty());
        assertTrue(broadcast.categoryMessagesPurchase().isEmpty());
    }

    @Test
    @DisplayName("valeurs explicites et messages par categorie")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("broadcast.sell.enable", true);
        yaml.set("broadcast.sell.exclude-seller", false);
        yaml.set("broadcast.purchase.enable", true);
        yaml.set("broadcast.purchase.exclude-buyer", false);
        yaml.set("broadcast.category-messages.sell.weapons", "<red>%player% sells a weapon");
        yaml.set("broadcast.category-messages.sell.armor", "<blue>%player% sells armor");
        yaml.set("broadcast.category-messages.purchase.weapons", "<red>%player% buys a weapon");

        BroadcastConfiguration broadcast = BroadcastConfiguration.of(plugin, yaml);

        assertTrue(broadcast.sellEnabled());
        assertFalse(broadcast.excludeSeller());
        assertTrue(broadcast.purchaseEnabled());
        assertFalse(broadcast.excludeBuyer());
        assertEquals(Map.of("weapons", "<red>%player% sells a weapon", "armor", "<blue>%player% sells armor"), broadcast.categoryMessagesSell());
        assertEquals(Map.of("weapons", "<red>%player% buys a weapon"), broadcast.categoryMessagesPurchase());
    }
}
