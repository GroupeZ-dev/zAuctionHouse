package fr.maxlego08.zauctionhouse.api.configuration.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryCommandConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    private static YamlConfiguration config(List<? extends Map<String, ?>> commands) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("commands.inventories", commands);
        return configuration;
    }

    @Test
    @DisplayName("section absente : aucune commande")
    void of_missing_empty() {
        assertTrue(InventoryCommandConfiguration.of(plugin, new YamlConfiguration()).isEmpty());
    }

    @Test
    @DisplayName("commande complete : tous les champs lus, l'inventaire accepte la casse libre")
    void of_fullCommand() {
        List<InventoryCommandConfiguration> commands = InventoryCommandConfiguration.of(plugin, config(List.of(Map.of(
                "enable", true,
                "permission", "zah.selling",
                "description", "Open selling items",
                "aliases", List.of("selling", "ventes"),
                "inventory", "selling_items",
                "enablePage", false,
                "pageName", "p"))));

        assertEquals(1, commands.size());
        InventoryCommandConfiguration command = commands.get(0);
        assertEquals("zah.selling", command.permission());
        assertEquals("Open selling items", command.description());
        assertEquals(List.of("selling", "ventes"), command.aliases());
        assertEquals(Inventories.SELLING_ITEMS, command.inventories());
        assertFalse(command.enablePage());
        assertEquals("p", command.pageName());
    }

    @Test
    @DisplayName("valeurs par defaut : permission et description vides, page active, 'page'")
    void of_defaults() {
        List<InventoryCommandConfiguration> commands = InventoryCommandConfiguration.of(plugin, config(List.of(Map.of(
                "enable", true,
                "aliases", List.of("history"),
                "inventory", "HISTORY"))));

        InventoryCommandConfiguration command = commands.get(0);
        assertEquals("", command.permission());
        assertEquals("", command.description());
        assertTrue(command.enablePage());
        assertEquals("page", command.pageName());
        assertEquals(Inventories.HISTORY, command.inventories());
    }

    @Test
    @DisplayName("commandes ignorees : desactivee, enable absent, sans alias, inventaire inconnu ou absent")
    void of_skipsInvalidEntries() {
        List<InventoryCommandConfiguration> commands = InventoryCommandConfiguration.of(plugin, config(List.of(
                Map.of("enable", false, "aliases", List.of("a"), "inventory", "HISTORY"),
                Map.of("aliases", List.of("b"), "inventory", "HISTORY"),
                Map.of("enable", true, "aliases", List.of(), "inventory", "HISTORY"),
                Map.of("enable", true, "aliases", List.of("c"), "inventory", "NOT_AN_INVENTORY"),
                Map.of("enable", true, "aliases", List.of("d")),
                Map.of("enable", true, "aliases", List.of("ok"), "inventory", "EXPIRED_ITEMS"))));

        assertEquals(1, commands.size());
        assertEquals(List.of("ok"), commands.get(0).aliases());
        assertEquals(Inventories.EXPIRED_ITEMS, commands.get(0).inventories());
    }
}
