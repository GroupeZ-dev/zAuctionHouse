package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : 100 ms, permission de bypass par defaut, aucune commande specifique")
    void of_defaults() {
        CooldownConfiguration cooldown = CooldownConfiguration.of(plugin, new YamlConfiguration());

        assertEquals(CooldownConfiguration.DEFAULT_COOLDOWN, cooldown.defaultCooldown());
        assertEquals(100L, cooldown.defaultCooldown());
        assertEquals(CooldownConfiguration.DEFAULT_BYPASS_PERMISSION, cooldown.bypassPermission());
        assertTrue(cooldown.perCommandCooldowns().isEmpty());
        assertEquals(100L, cooldown.getCooldownForCommand("sell"));
    }

    @Test
    @DisplayName("les cooldowns par commande sont lus en minuscules")
    void of_perCommand_lowercased() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("commands.cooldown.default-cooldown", 250);
        configuration.set("commands.cooldown.bypass-permission", "zah.nocooldown");
        configuration.set("commands.cooldown.per-command.Sell", 2000);
        configuration.set("commands.cooldown.per-command.claim", 0);

        CooldownConfiguration cooldown = CooldownConfiguration.of(plugin, configuration);

        assertEquals(250L, cooldown.defaultCooldown());
        assertEquals("zah.nocooldown", cooldown.bypassPermission());
        assertEquals(Map.of("sell", 2000L, "claim", 0L), cooldown.perCommandCooldowns());
    }

    @Test
    @DisplayName("getCooldownForCommand : insensible a la casse, defaut pour les commandes inconnues")
    void getCooldownForCommand() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("commands.cooldown.default-cooldown", 250);
        configuration.set("commands.cooldown.per-command.sell", 2000);
        configuration.set("commands.cooldown.per-command.claim", 0);
        CooldownConfiguration cooldown = CooldownConfiguration.of(plugin, configuration);

        assertEquals(2000L, cooldown.getCooldownForCommand("sell"));
        assertEquals(2000L, cooldown.getCooldownForCommand("SELL"));
        assertEquals(0L, cooldown.getCooldownForCommand("Claim"), "un cooldown explicite a 0 est respecte");
        assertEquals(250L, cooldown.getCooldownForCommand("page"));
    }

    @Test
    @DisplayName("cooldown global a 0 sans commande specifique : accepte (avec avertissement)")
    void of_zeroDefault_kept() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("commands.cooldown.default-cooldown", 0);

        CooldownConfiguration cooldown = CooldownConfiguration.of(plugin, configuration);

        assertEquals(0L, cooldown.defaultCooldown());
        assertEquals(0L, cooldown.getCooldownForCommand("anything"));
    }
}
