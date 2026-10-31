package fr.maxlego08.zauctionhouse.api.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginsTest {

    @ParameterizedTest
    @EnumSource(Plugins.class)
    @DisplayName("chaque entree porte un nom de plugin non vide")
    void everyPlugin_hasName(Plugins plugin) {
        assertNotNull(plugin.getName());
        assertFalse(plugin.getName().isBlank());
    }

    @Test
    @DisplayName("les noms sont ceux attendus par le PluginManager (casse exacte)")
    void knownNames() {
        assertEquals("PlaceholderAPI", Plugins.PLACEHOLDER.getName());
        assertEquals("ItemsAdder", Plugins.ITEMSADDER.getName());
        assertEquals("LuckPerms", Plugins.LUCKPERMS.getName());
        assertEquals("eco", Plugins.ECO.getName());
        assertEquals("MythicCrucible", Plugins.CRUCIBLE.getName());
        assertEquals("zMenuPlus", Plugins.ZMENUPLUS.getName());
    }

    @Test
    @DisplayName("les noms sont uniques")
    void names_areUnique() {
        long distinct = Arrays.stream(Plugins.values()).map(Plugins::getName).distinct().count();
        assertEquals(Plugins.values().length, distinct);
    }
}
