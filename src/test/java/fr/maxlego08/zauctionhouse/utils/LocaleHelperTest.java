package fr.maxlego08.zauctionhouse.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LocaleHelperTest {

    private Locale previousDefault;
    private final Logger logger = mock(Logger.class);

    @BeforeEach
    void saveLocale() {
        this.previousDefault = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previousDefault);
    }

    @ParameterizedTest
    @CsvSource({
            "CL, es", "MX, es", "ES, es", "GQ, es",
            "FR, fr", "BE, fr", "CA, fr", "CD, fr", "NC, fr",
            "IT, it", "SM, it", "VA, it",
            "BR, pt", "DE, de", "CN, zh", "RU, ru", "JP, ja", "KR, ko", "PL, pl", "TR, tr", "NL, nl", "TH, th", "ID, id"
    })
    @DisplayName("getLanguageForCountry mappe un echantillon de chaque langue")
    void getLanguageForCountry_knownCountries(String country, String language) {
        assertEquals(language, LocaleHelper.getLanguageForCountry(country));
        assertTrue(LocaleHelper.hasMapping(country));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "cl", "It"})
    @DisplayName("les codes pays sont insensibles a la casse")
    void getLanguageForCountry_caseInsensitive(String country) {
        assertTrue(LocaleHelper.hasMapping(country));
        assertEquals(LocaleHelper.getLanguageForCountry(country.toUpperCase()), LocaleHelper.getLanguageForCountry(country));
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "GB", "XX", ""})
    @DisplayName("un pays inconnu n'a pas de mapping et rend null")
    void getLanguageForCountry_unknown(String country) {
        assertNull(LocaleHelper.getLanguageForCountry(country));
        assertFalse(LocaleHelper.hasMapping(country));
    }

    @Test
    @DisplayName("un code pays null leve NullPointerException")
    void getLanguageForCountry_null_throws() {
        assertThrows(NullPointerException.class, () -> LocaleHelper.getLanguageForCountry(null));
        assertThrows(NullPointerException.class, () -> LocaleHelper.hasMapping(null));
    }

    @Test
    @DisplayName("getAllMappings rend une copie : la modifier n'affecte pas la table interne")
    void getAllMappings_isDefensiveCopy() {
        Map<String, String> mappings = LocaleHelper.getAllMappings();
        assertEquals("fr", mappings.get("FR"));
        assertTrue(mappings.size() >= 80, "size=" + mappings.size());

        mappings.put("US", "en");
        mappings.remove("FR");

        assertFalse(LocaleHelper.hasMapping("US"));
        assertEquals("fr", LocaleHelper.getLanguageForCountry("FR"));
        assertNotSame(mappings, LocaleHelper.getAllMappings());
    }

    @Test
    @DisplayName("une langue configuree explicitement l'emporte sur la locale systeme, en minuscules")
    void constructor_configuredLanguage_wins() {
        Locale.setDefault(new Locale("es", "CL"));
        assertEquals("fr", new LocaleHelper(logger, "fr").getLanguage());
        assertEquals("it", new LocaleHelper(logger, "IT").getLanguage());
        assertEquals("en", new LocaleHelper(logger, "en").getLanguage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "AUTO", "Auto", ""})
    @DisplayName("auto (toute casse) ou vide : detection par le pays de la locale systeme")
    void constructor_auto_usesCountryMapping(String configured) {
        Locale.setDefault(new Locale("es", "CL"));
        assertEquals("es", new LocaleHelper(logger, configured).getLanguage());
    }

    @Test
    @DisplayName("configuration nulle : detection automatique")
    void constructor_nullConfigured_detects() {
        Locale.setDefault(new Locale("en", "CA"));
        assertEquals("fr", new LocaleHelper(logger, null).getLanguage(), "le Canada est mappe sur le francais");
    }

    @Test
    @DisplayName("pays sans mapping : la langue systeme est utilisee telle quelle")
    void constructor_auto_unmappedCountry_usesSystemLanguage() {
        Locale.setDefault(new Locale("en", "US"));
        assertEquals("en", new LocaleHelper(logger, "auto").getLanguage());

        Locale.setDefault(new Locale("sv", "SE"));
        assertEquals("sv", new LocaleHelper(logger, "auto").getLanguage());
    }

    @Test
    @DisplayName("locale sans pays : la langue systeme est utilisee")
    void constructor_auto_noCountry_usesSystemLanguage() {
        Locale.setDefault(new Locale("de"));
        assertEquals("de", new LocaleHelper(logger, "auto").getLanguage());
    }

    @Test
    @DisplayName("une langue configuree inconnue est acceptee telle quelle (le chargement des ressources decidera)")
    void constructor_unknownConfiguredLanguage_isKept() {
        Locale.setDefault(Locale.FRANCE);
        assertEquals("klingon", new LocaleHelper(logger, "Klingon").getLanguage());
    }
}
