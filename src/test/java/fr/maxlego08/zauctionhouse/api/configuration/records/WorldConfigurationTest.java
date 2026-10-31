package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : une liste vide par type, aucun monde banni")
    void of_empty() {
        WorldConfiguration configuration = WorldConfiguration.of(plugin, new YamlConfiguration());

        assertEquals(ItemType.values().length, configuration.worlds().size());
        for (ItemType itemType : ItemType.values()) {
            assertTrue(configuration.worlds().get(itemType).isEmpty());
            assertFalse(configuration.isWorldBanned(itemType, "world"));
        }
    }

    @Test
    @DisplayName("les mondes sont lus par type (cle en minuscules)")
    void of_perType() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("banned-worlds.auction", List.of("world_the_end", "world_nether"));
        yaml.set("banned-worlds.bid", List.of("pvp"));

        WorldConfiguration configuration = WorldConfiguration.of(plugin, yaml);

        assertEquals(List.of("world_the_end", "world_nether"), configuration.worlds().get(ItemType.AUCTION));
        assertEquals(List.of("pvp"), configuration.worlds().get(ItemType.BID));
        assertTrue(configuration.worlds().get(ItemType.RENT).isEmpty());
    }

    @Test
    @DisplayName("isWorldBanned : exact, sensible a la casse, par type")
    void isWorldBanned() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("banned-worlds.auction", List.of("world_the_end"));

        WorldConfiguration configuration = WorldConfiguration.of(plugin, yaml);

        assertTrue(configuration.isWorldBanned(ItemType.AUCTION, "world_the_end"));
        assertFalse(configuration.isWorldBanned(ItemType.AUCTION, "World_The_End"));
        assertFalse(configuration.isWorldBanned(ItemType.AUCTION, "world_the_end_2"));
        assertFalse(configuration.isWorldBanned(ItemType.BID, "world_the_end"));
    }
}
