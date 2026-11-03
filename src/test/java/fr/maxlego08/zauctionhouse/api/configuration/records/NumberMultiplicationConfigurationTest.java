package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberMultiplicationConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    private static YamlConfiguration config(boolean enable, List<? extends Map<String, ?>> formats) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("number-sell-multiplication.enable", enable);
        configuration.set("number-sell-multiplication.formats", formats);
        return configuration;
    }

    private NumberMultiplicationConfiguration standard() {
        return NumberMultiplicationConfiguration.of(plugin, config(true, List.of(
                Map.of("format", "K", "multiplication", 1000),
                Map.of("format", "M", "multiplication", 1_000_000),
                Map.of("format", "B", "multiplication", "1000000000"),
                Map.of("format", "QQ", "multiplication", "1000000000000000000"))));
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertNotNull(actual, "attendu " + expected + " mais obtenu null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "attendu " + expected + " mais obtenu " + actual);
    }

    @Test
    @DisplayName("desactive : la map reste vide meme si des formats sont declares")
    void of_disabled_ignoresFormats() {
        NumberMultiplicationConfiguration configuration = NumberMultiplicationConfiguration.of(plugin,
                config(false, List.of(Map.of("format", "K", "multiplication", 1000))));

        assertFalse(configuration.enable());
        assertTrue(configuration.multiplications().isEmpty());
        assertNumber("1", configuration.parseNumber("1k"));
    }

    @Test
    @DisplayName("active : les formats complets sont charges tels quels")
    void of_enabled_loadsFormats() {
        NumberMultiplicationConfiguration configuration = standard();

        assertTrue(configuration.enable());
        assertEquals(4, configuration.multiplications().size());
        assertNumber("1000", configuration.multiplications().get("K"));
        assertNumber("1000000", configuration.multiplications().get("M"));
        assertNumber("1000000000", configuration.multiplications().get("B"));
        assertNumber("1000000000000000000", configuration.multiplications().get("QQ"));
    }

    @Test
    @DisplayName("active : les entrees incompletes (format ou multiplication absent) sont ignorees")
    @Disabled("BUG: NumberMultiplicationConfiguration.of teste accessor.getString(...) == null mais TypedMapAccessor.getString(String) rend la chaine \"null\" pour une cle absente : un format sans 'multiplication' provoque new BigDecimal(\"null\") -> NumberFormatException et fait echouer tout MainConfiguration.load() ; un format absent est enregistre sous la cle \"null\"")
    void of_enabled_skipsIncompleteEntries() {
        NumberMultiplicationConfiguration configuration = NumberMultiplicationConfiguration.of(plugin, config(true, List.of(
                Map.of("format", "K", "multiplication", 1000),
                Map.of("multiplication", 5),
                Map.of("format", "X"))));

        assertTrue(configuration.enable());
        assertEquals(1, configuration.multiplications().size());
        assertNumber("1000", configuration.multiplications().get("K"));
    }

    @Test
    @DisplayName("configuration absente : desactivee et vide")
    void of_missingSection_disabled() {
        NumberMultiplicationConfiguration configuration = NumberMultiplicationConfiguration.of(plugin, new YamlConfiguration());

        assertFalse(configuration.enable());
        assertTrue(configuration.multiplications().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("entree null ou blanche : zero")
    void parseNumber_blank_isZero(String input) {
        assertNumber("0", standard().parseNumber(input));
    }

    @ParameterizedTest(name = "\"{0}\" => {1}")
    @CsvSource({
            "1k, 1000",
            "1K, 1000",
            "' 1k ', 1000",
            "1.5k, 1500",
            "'1,5k', 1500",
            "2.5m, 2500000",
            "10 m, 10000000",
            "1b, 1000000000",
            "0k, 0",
            "42, 42",
            "3.75, 3.75",
            "1x, 1",
    })
    void parseNumber_validInputs(String input, String expected) {
        assertNumber(expected, standard().parseNumber(input));
    }

    @ParameterizedTest(name = "\"{0}\" est rejete")
    @ValueSource(strings = {"abc", "-5", "1 000", "1.2.3", "k", "1kk", "1.", ".5", "1e3"})
    void parseNumber_invalidInputs_returnNull(String input) {
        assertNull(standard().parseNumber(input));
    }

    @Test
    @DisplayName("suffixe inconnu : le nombre est rendu sans multiplication")
    void parseNumber_unknownSuffix_returnsNumber() {
        assertNumber("7", standard().parseNumber("7z"));
    }

    @Test
    @DisplayName("suffixe sur plusieurs lettres declare dans la configuration (QQ) : il doit etre reconnu")
    @Disabled("BUG: NumberMultiplicationConfiguration.PATTERN n'accepte qu'une lettre de suffixe ([a-z]?) alors que config.yml declare QQ, SS, UN, DD, TR : parseNumber(\"1qq\") rend null (NumberMultiplicationConfiguration.java, constante PATTERN)")
    void parseNumber_multiLetterSuffix_isRecognized() {
        assertNumber("1000000000000000000", standard().parseNumber("1qq"));
    }

    @Test
    @DisplayName("la multiplication conserve la precision BigDecimal")
    void parseNumber_keepsPrecision() {
        assertNumber("1234.5", standard().parseNumber("1.2345k"));
        assertNumber("999999999999000000", standard().parseNumber("999999999999m"));
    }
}
