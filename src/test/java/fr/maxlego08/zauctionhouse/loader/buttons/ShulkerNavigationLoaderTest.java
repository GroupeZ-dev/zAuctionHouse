package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.buttons.shulker.ShulkerNavigationButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShulkerNavigationLoaderTest extends LoaderTestBase {

    private ShulkerNavigationLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new ShulkerNavigationLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_SHULKER_NAVIGATION")
    void name() {
        assertEquals("ZAUCTIONHOUSE_SHULKER_NAVIGATION", loader.getName());
    }

    @Test
    @DisplayName("shulker-content.yml livre : 'previous' -> precedent, 'next' -> suivant")
    void shippedFile() {
        YamlConfiguration configuration = TestResources.yaml("inventories/shulker-content.yml");

        ShulkerNavigationButton previous = load(loader, configuration, "previous", ShulkerNavigationButton.class);
        ShulkerNavigationButton next = load(loader, configuration, "next", ShulkerNavigationButton.class);

        assertFalse((boolean) field(previous, "isNext"));
        assertTrue((boolean) field(next, "isNext"));
        assertSame(plugin, field(next, "plugin"));
    }

    @Test
    @DisplayName("direction absente -> suivant")
    void missingDirection_isNext() {
        ShulkerNavigationButton button = loadInline(loader, "nav", "slot: 1", ShulkerNavigationButton.class);

        assertTrue((boolean) field(button, "isNext"));
    }

    @ParameterizedTest
    @CsvSource({"next, true", "NEXT, true", "Next, true", "previous, false", "PREVIOUS, false", "back, false", "anything, false"})
    @DisplayName("seul 'next' (insensible a la casse) donne un bouton suivant ; toute autre valeur donne precedent")
    void direction_parsing(String direction, boolean expectedNext) {
        ShulkerNavigationButton button = loadInline(loader, "nav", "direction: " + direction, ShulkerNavigationButton.class);

        assertEquals(expectedNext, (boolean) field(button, "isNext"));
    }
}
