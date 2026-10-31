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

class ItemDisplayConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : gabarits de secours, 'and', ',' et fusion active")
    void of_defaults() {
        ItemDisplayConfiguration configuration = ItemDisplayConfiguration.of(plugin, new YamlConfiguration());

        assertEquals("#8ee6e3x%amount% &7<lang:%item-translation-key%>", configuration.langDisplay());
        assertEquals("#8ee6e3x%amount% &7%item-name%", configuration.itemNameDisplay());
        assertEquals("and", configuration.and());
        assertEquals(",", configuration.between());
        assertTrue(configuration.mergeSimilar());
    }

    @Test
    @DisplayName("gabarits blancs : remplaces par les gabarits de secours")
    void of_blankTemplates_fallBack() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item-display.lang", "   ");
        yaml.set("item-display.item-name", "");

        ItemDisplayConfiguration configuration = ItemDisplayConfiguration.of(plugin, yaml);

        assertEquals("#8ee6e3x%amount% &7<lang:%item-translation-key%>", configuration.langDisplay());
        assertEquals("#8ee6e3x%amount% &7%item-name%", configuration.itemNameDisplay());
    }

    @Test
    @DisplayName("valeurs explicites conservees")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item-display.lang", "x%amount% <lang:%item-translation-key%>");
        yaml.set("item-display.item-name", "x%amount% %item-name%");
        yaml.set("item-display.and", " et ");
        yaml.set("item-display.between", "; ");
        yaml.set("item-display.merge-similar-items", false);

        ItemDisplayConfiguration configuration = ItemDisplayConfiguration.of(plugin, yaml);

        assertEquals("x%amount% <lang:%item-translation-key%>", configuration.langDisplay());
        assertEquals("x%amount% %item-name%", configuration.itemNameDisplay());
        assertEquals(" et ", configuration.and());
        assertEquals("; ", configuration.between());
        assertFalse(configuration.mergeSimilar());
    }
}
