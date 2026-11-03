package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    private PermissionConfiguration build() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("permissions.auction", List.of(
                Map.of("permission", "max.5", "limit", 5),
                Map.of("permission", "max.10", "limit", 10),
                Map.of("permission", "max.15", "limit", 15)));
        configuration.set("permissions.bid", List.of(Map.of("permission", "bid.3", "limit", 3)));
        return PermissionConfiguration.of(plugin, configuration);
    }

    @Test
    @DisplayName("chaque type d'item possede sa map, vide si non configure")
    void of_everyItemTypeHasAMap() {
        PermissionConfiguration configuration = build();

        assertEquals(ItemType.values().length, configuration.permissions().size());
        assertEquals(Map.of("max.5", 5, "max.10", 10, "max.15", 15), configuration.permissions().get(ItemType.AUCTION));
        assertEquals(Map.of("bid.3", 3), configuration.permissions().get(ItemType.BID));
        assertTrue(configuration.permissions().get(ItemType.RENT).isEmpty());
    }

    @Test
    @DisplayName("configuration vide : toutes les maps existent et sont vides, limite 0")
    void of_emptyConfiguration() {
        PermissionConfiguration configuration = PermissionConfiguration.of(plugin, new YamlConfiguration());

        for (ItemType itemType : ItemType.values()) {
            assertTrue(configuration.permissions().get(itemType).isEmpty());
            assertEquals(0, configuration.getLimit(itemType, ConfigTestSupport.playerWithPermissions("Anyone", "max.15")));
        }
    }

    @Test
    @DisplayName("getLimit : la plus haute limite detenue l'emporte")
    void getLimit_maxOfHeldPermissions() {
        Player player = ConfigTestSupport.playerWithPermissions("Vip", "max.5", "max.10");

        assertEquals(10, build().getLimit(ItemType.AUCTION, player));
    }

    @Test
    @DisplayName("getLimit : une seule permission")
    void getLimit_singlePermission() {
        assertEquals(15, build().getLimit(ItemType.AUCTION, ConfigTestSupport.playerWithPermissions("Elite", "max.15")));
        assertEquals(3, build().getLimit(ItemType.BID, ConfigTestSupport.playerWithPermissions("Bidder", "bid.3")));
    }

    @Test
    @DisplayName("getLimit : aucune permission detenue => 0")
    void getLimit_noPermission_zero() {
        Player player = ConfigTestSupport.playerWithPermissions("Nobody");

        assertEquals(0, build().getLimit(ItemType.AUCTION, player));
    }

    @Test
    @DisplayName("getLimit : les permissions d'un autre type ne comptent pas")
    void getLimit_otherTypePermissionsIgnored() {
        Player player = ConfigTestSupport.playerWithPermissions("Bidder", "bid.3");

        assertEquals(0, build().getLimit(ItemType.AUCTION, player));
    }
}
