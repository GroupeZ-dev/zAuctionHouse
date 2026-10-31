package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoundConfigurationTest {

    private static final String PATH = "action.purchased-item.money-sound.";

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : desactive, MASTER, son villageois, volume et pitch 0")
    void of_defaults() {
        SoundConfiguration sound = SoundConfiguration.of(plugin, new YamlConfiguration(), PATH);

        assertFalse(sound.enable());
        assertEquals(SoundCategory.MASTER, sound.category());
        assertEquals("minecraft:entity.villager.no", sound.sound());
        assertEquals(0f, sound.volume(), 1e-6);
        assertEquals(0f, sound.pitch(), 1e-6);
    }

    @Test
    @DisplayName("valeurs explicites lues")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(PATH + "enable", true);
        yaml.set(PATH + "category", "PLAYERS");
        yaml.set(PATH + "sound", "minecraft:block.anvil.land");
        yaml.set(PATH + "volume", 0.5);
        yaml.set(PATH + "pitch", 1.5);

        SoundConfiguration sound = SoundConfiguration.of(plugin, yaml, PATH);

        assertTrue(sound.enable());
        assertEquals(SoundCategory.PLAYERS, sound.category());
        assertEquals("minecraft:block.anvil.land", sound.sound());
        assertEquals(0.5f, sound.volume(), 1e-6);
        assertEquals(1.5f, sound.pitch(), 1e-6);
    }

    @Test
    @DisplayName("categorie invalide : MASTER")
    void of_invalidCategory_master() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(PATH + "category", "LOUDSPEAKER");

        assertEquals(SoundCategory.MASTER, SoundConfiguration.of(plugin, yaml, PATH).category());
    }

    @Test
    @DisplayName("play : rien quand desactive")
    void play_disabled_doesNothing() {
        Player player = MockPlugins.player("Alice");
        SoundConfiguration sound = new SoundConfiguration(false, SoundCategory.MASTER, "s", 1f, 1f);

        sound.play(player);

        verify(player, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("play : joue le son a la position du joueur avec les parametres configures")
    void play_enabled_playsAtPlayerLocation() {
        Location location = mock(Location.class);
        Player player = MockPlugins.player("Alice");
        when(player.getLocation()).thenReturn(location);
        SoundConfiguration sound = new SoundConfiguration(true, SoundCategory.AMBIENT, "minecraft:block.anvil.land", 0.5f, 1.5f);

        sound.play(player);

        verify(player).playSound(location, "minecraft:block.anvil.land", SoundCategory.AMBIENT, 0.5f, 1.5f);
    }
}
