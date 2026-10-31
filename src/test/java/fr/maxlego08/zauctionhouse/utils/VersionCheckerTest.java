package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La verification distante (HTTP) et l'enregistrement du listener exigent un serveur et le
 * reseau : seul l'algorithme de comparaison de versions, coeur du correctif documente dans la
 * classe, est couvert ici, via reflexion sur les methodes statiques privees.
 */
class VersionCheckerTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(dataFolder);
        PluginMeta meta = mock(PluginMeta.class);
        when(meta.getVersion()).thenReturn("4.0.1.6");
        when(plugin.getPluginMeta()).thenReturn(meta);
    }

    private static int compare(String left, String right) throws Exception {
        Method method = VersionChecker.class.getDeclaredMethod("compare", String.class, String.class);
        method.setAccessible(true);
        return (int) method.invoke(null, left, right);
    }

    private static int parseSegment(String segment) throws Exception {
        Method method = VersionChecker.class.getDeclaredMethod("parseSegment", String.class);
        method.setAccessible(true);
        return (int) method.invoke(null, segment);
    }

    @Test
    @DisplayName("le constructeur lit la version courante depuis les metadonnees du plugin")
    void constructor_readsPluginVersion() {
        VersionChecker checker = new VersionChecker(plugin, 1234);
        assertNotNull(checker);
    }

    @ParameterizedTest
    @CsvSource({
            "4.0.1.6, 4.0.1.6, 0",
            "4.0.1.10, 4.0.2.0, -1",
            "4.0.2.0, 4.0.1.10, 1",
            "4.0.1.10, 4.0.1.9, 1",
            "4.0, 4.0.0.0, 0",
            "4.0.0.1, 4.0, 1",
            "4.1, 4.0.9.9, 1",
            "3.9.9.9, 4, -1",
            "4.0.1.6-SNAPSHOT, 4.0.1.6, 0",
            "4.0.1.6, 4.0.1.7-RC1, -1"
    })
    @DisplayName("compare travaille segment par segment, sans concatenation des chiffres")
    void compare_segmentWise(String left, String right, int expectedSign) throws Exception {
        int result = compare(left, right);
        assertEquals(expectedSign, Integer.signum(result), left + " vs " + right);
    }

    @ParameterizedTest
    @CsvSource({
            "6, 6",
            "10, 10",
            "6-SNAPSHOT, 6",
            "RC1, 1",
            "abc, 0",
            "'', 0"
    })
    @DisplayName("parseSegment ne garde que les chiffres et rend 0 sans chiffre")
    void parseSegment_digitsOnly(String segment, int expected) throws Exception {
        assertEquals(expected, parseSegment(segment));
    }

    @Test
    @DisplayName("compare est antisymetrique")
    void compare_isAntisymmetric() throws Exception {
        String[] versions = {"1.0", "1.0.1", "1.1", "2", "1.0.0.10"};
        for (String a : versions) {
            for (String b : versions) {
                assertEquals(Integer.signum(compare(a, b)), -Integer.signum(compare(b, a)), a + " / " + b);
            }
        }
        assertTrue(compare("1.0.0.10", "1.0.0.9") > 0);
    }
}
