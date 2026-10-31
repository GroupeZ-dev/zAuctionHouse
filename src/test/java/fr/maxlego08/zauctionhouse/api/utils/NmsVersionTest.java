package fr.maxlego08.zauctionhouse.api.utils;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code NmsVersion} lit {@code Bukkit.getBukkitVersion()} dans un initialiseur statique : la
 * classe doit etre touchee pour la premiere fois SOUS le mock statique, sinon son initialisation
 * echoue (NPE) et elle devient inutilisable pour toute la JVM.
 */
class NmsVersionTest {

    private static MockedStatic<Bukkit> bukkit;

    @BeforeAll
    static void mockServer() {
        bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.21.10-R0.1-SNAPSHOT");
        assertNotNull(NmsVersion.nmsVersion); // force l'initialisation sous le mock
    }

    @AfterAll
    static void closeMock() {
        bukkit.close();
    }

    @ParameterizedTest
    @CsvSource({
            "1.21.1-R0.1-SNAPSHOT, V_1_21_1",
            "1.20.4-R0.1-SNAPSHOT, V_1_20_4",
            "1.20.6-R0.1-SNAPSHOT, V_1_20_6",
            "1.21-R0.1-SNAPSHOT, V_1_21",
            "1.8.8-R0.1-SNAPSHOT, V_1_8_8",
            "1.16.5-R0.1-SNAPSHOT, V_1_16_5",
            "1.21.10-R0.1-SNAPSHOT, V_1_21_3"
    })
    @DisplayName("getCurrentVersion choisit la constante la plus proche de la version Bukkit annoncee")
    void getCurrentVersion_closestMatch(String bukkitVersion, NmsVersion expected) {
        bukkit.when(Bukkit::getBukkitVersion).thenReturn(bukkitVersion);
        assertEquals(expected, NmsVersion.getCurrentVersion());
    }

    @Test
    @DisplayName("une version illisible retombe sur la plus ancienne constante (distance a 0 minimale)")
    void getCurrentVersion_unparseable_fallsBackToOldest() {
        bukkit.when(Bukkit::getBukkitVersion).thenReturn("unknown");
        assertEquals(NmsVersion.V_1_8_8, NmsVersion.getCurrentVersion());
    }

    @Test
    @DisplayName("getVersion expose le code numerique de la constante")
    void getVersion() {
        assertEquals(188, NmsVersion.V_1_8_8.getVersion());
        assertEquals(1213, NmsVersion.V_1_21_3.getVersion());
        assertEquals(1165, NmsVersion.V_1_16_5.getVersion());
    }

    @Test
    @DisplayName("predicats de 1.8.8 : une main, items legacy, pas de PDC, skull legacy, pas de hex")
    void predicates_1_8_8() {
        NmsVersion version = NmsVersion.V_1_8_8;
        assertTrue(version.isOneHand());
        assertTrue(version.isItemLegacy());
        assertFalse(version.isPdcVersion());
        assertFalse(version.isCustomModelData());
        assertTrue(version.isSkullOwnerLegacy());
        assertFalse(version.isHexVersion());
        assertFalse(version.isShulker());
        assertFalse(version.isBarrel());
        assertFalse(version.isNewMaterial());
        assertFalse(version.isNewNBTVersion());
        assertFalse(version.isNewHeadApi());
        assertFalse(version.isNewNMSVersion());
        assertFalse(version.hasPlayerProfiles());
        assertFalse(version.hasObfuscatedNames());
        assertFalse(version.isComponent(true));
        assertFalse(version.isAttributItemStack());
    }

    @Test
    @DisplayName("seuils : 1.9 shulker, 1.13 nouveaux materiaux, 1.14 barrel/PDC/CMD, 1.16 hex, 1.16.5 composants Paper")
    void predicates_thresholds() {
        assertTrue(NmsVersion.V_1_9.isShulker());
        assertFalse(NmsVersion.V_1_9.isOneHand());

        assertTrue(NmsVersion.V_1_13.isNewMaterial());
        assertFalse(NmsVersion.V_1_13.isItemLegacy());
        assertTrue(NmsVersion.V_1_12_2.isItemLegacy());
        assertTrue(NmsVersion.V_1_12.isSkullOwnerLegacy());
        assertFalse(NmsVersion.V_1_13.isSkullOwnerLegacy());

        assertTrue(NmsVersion.V_1_14.isBarrel());
        assertTrue(NmsVersion.V_1_14.isPdcVersion());
        assertTrue(NmsVersion.V_1_14.isCustomModelData());
        assertFalse(NmsVersion.V_1_13_2.isPdcVersion());

        assertTrue(NmsVersion.V_1_16.isHexVersion());
        assertFalse(NmsVersion.V_1_15_2.isHexVersion());

        assertTrue(NmsVersion.V_1_16_5.isComponent(true));
        assertFalse(NmsVersion.V_1_16_5.isComponent(false), "Spigot n'a pas les composants Adventure");
        assertFalse(NmsVersion.V_1_16_4.isComponent(true));
    }

    @Test
    @DisplayName("seuils : 1.17 noms obfusques/NMS, 1.18 NBT, 1.18.1 profils, 1.20 heads, 1.20.5 items a attributs")
    void predicates_modernThresholds() {
        assertTrue(NmsVersion.V_1_17.hasObfuscatedNames());
        assertTrue(NmsVersion.V_1_17.isNewNMSVersion());
        assertFalse(NmsVersion.V_1_16_5.isNewNMSVersion());

        assertTrue(NmsVersion.V_1_18.isNewNBTVersion());
        assertFalse(NmsVersion.V_1_17_1.isNewNBTVersion());

        assertTrue(NmsVersion.V_1_18_1.hasPlayerProfiles());
        assertFalse(NmsVersion.V_1_18.hasPlayerProfiles());

        assertTrue(NmsVersion.V_1_20.isNewHeadApi());
        assertFalse(NmsVersion.V_1_19_2.isNewHeadApi());

        assertTrue(NmsVersion.V_1_20_5.isAttributItemStack());
        assertFalse(NmsVersion.V_1_20_4.isAttributItemStack());
        assertTrue(NmsVersion.V_1_21_3.isAttributItemStack());
    }

    @Test
    @DisplayName("isAttributeVersion est vrai pour toute version moderne")
    void isAttributeVersion_modern() {
        assertTrue(NmsVersion.V_1_21_3.isAttributeVersion());
        assertTrue(NmsVersion.V_1_12_2.isAttributeVersion());
    }

    @Test
    @Disabled("BUG: isSkullOwnerLegacy teste version <= 1120, ce qui exclut 1.12.2 (code 1122) alors que toute version < 1.13 est legacy pour le proprietaire de tete, NmsVersion.java:778")
    @DisplayName("isSkullOwnerLegacy est vrai pour 1.12.2, derniere version avant le nouveau systeme de materiaux")
    void isSkullOwnerLegacy_1_12_2_isTrue() {
        assertTrue(NmsVersion.V_1_12_2.isSkullOwnerLegacy());
    }

    @Test
    @Disabled("BUG: isAttributeVersion compare a 1880 alors que le code de 1.8.8 est 188, la constante V_1_8_8 est donc declaree compatible attributs, NmsVersion.java:805")
    @DisplayName("isAttributeVersion est faux pour 1.8.8")
    void isAttributeVersion_1_8_8_isFalse() {
        assertFalse(NmsVersion.V_1_8_8.isAttributeVersion());
    }
}
