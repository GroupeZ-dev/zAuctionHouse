package fr.maxlego08.zauctionhouse.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Helper class for locale detection and country-to-language mapping.
 * <p>
 * This class provides intelligent language detection based on:
 * 1. Manual configuration (forced language)
 * 2. Country code mapping (e.g., Chile -> Spanish)
 * 3. Default locale language fallback
 * </p>
 */
public class LocaleHelper {

    private static final Map<String, String> COUNTRY_TO_LANGUAGE = new HashMap<>();

    static {
        // Spanish-speaking countries
        COUNTRY_TO_LANGUAGE.put("ES", "es"); // Spain
        COUNTRY_TO_LANGUAGE.put("MX", "es"); // Mexico
        COUNTRY_TO_LANGUAGE.put("AR", "es"); // Argentina
        COUNTRY_TO_LANGUAGE.put("CL", "es"); // Chile
        COUNTRY_TO_LANGUAGE.put("CO", "es"); // Colombia
        COUNTRY_TO_LANGUAGE.put("PE", "es"); // Peru
        COUNTRY_TO_LANGUAGE.put("VE", "es"); // Venezuela
        COUNTRY_TO_LANGUAGE.put("EC", "es"); // Ecuador
        COUNTRY_TO_LANGUAGE.put("GT", "es"); // Guatemala
        COUNTRY_TO_LANGUAGE.put("CU", "es"); // Cuba
        COUNTRY_TO_LANGUAGE.put("BO", "es"); // Bolivia
        COUNTRY_TO_LANGUAGE.put("DO", "es"); // Dominican Republic
        COUNTRY_TO_LANGUAGE.put("HN", "es"); // Honduras
        COUNTRY_TO_LANGUAGE.put("PY", "es"); // Paraguay
        COUNTRY_TO_LANGUAGE.put("SV", "es"); // El Salvador
        COUNTRY_TO_LANGUAGE.put("NI", "es"); // Nicaragua
        COUNTRY_TO_LANGUAGE.put("CR", "es"); // Costa Rica
        COUNTRY_TO_LANGUAGE.put("PA", "es"); // Panama
        COUNTRY_TO_LANGUAGE.put("UY", "es"); // Uruguay
        COUNTRY_TO_LANGUAGE.put("PR", "es"); // Puerto Rico
        COUNTRY_TO_LANGUAGE.put("GQ", "es"); // Equatorial Guinea

        // French-speaking countries
        COUNTRY_TO_LANGUAGE.put("FR", "fr"); // France
        COUNTRY_TO_LANGUAGE.put("BE", "fr"); // Belgium (French part)
        COUNTRY_TO_LANGUAGE.put("CH", "fr"); // Switzerland (French part)
        COUNTRY_TO_LANGUAGE.put("CA", "fr"); // Canada (Quebec)
        COUNTRY_TO_LANGUAGE.put("LU", "fr"); // Luxembourg
        COUNTRY_TO_LANGUAGE.put("MC", "fr"); // Monaco
        COUNTRY_TO_LANGUAGE.put("SN", "fr"); // Senegal
        COUNTRY_TO_LANGUAGE.put("CI", "fr"); // Ivory Coast
        COUNTRY_TO_LANGUAGE.put("ML", "fr"); // Mali
        COUNTRY_TO_LANGUAGE.put("BF", "fr"); // Burkina Faso
        COUNTRY_TO_LANGUAGE.put("NE", "fr"); // Niger
        COUNTRY_TO_LANGUAGE.put("TG", "fr"); // Togo
        COUNTRY_TO_LANGUAGE.put("BJ", "fr"); // Benin
        COUNTRY_TO_LANGUAGE.put("GN", "fr"); // Guinea
        COUNTRY_TO_LANGUAGE.put("CD", "fr"); // Democratic Republic of the Congo
        COUNTRY_TO_LANGUAGE.put("CG", "fr"); // Republic of the Congo
        COUNTRY_TO_LANGUAGE.put("GA", "fr"); // Gabon
        COUNTRY_TO_LANGUAGE.put("CM", "fr"); // Cameroon
        COUNTRY_TO_LANGUAGE.put("MG", "fr"); // Madagascar
        COUNTRY_TO_LANGUAGE.put("HT", "fr"); // Haiti
        COUNTRY_TO_LANGUAGE.put("RW", "fr"); // Rwanda
        COUNTRY_TO_LANGUAGE.put("BI", "fr"); // Burundi
        COUNTRY_TO_LANGUAGE.put("DJ", "fr"); // Djibouti
        COUNTRY_TO_LANGUAGE.put("KM", "fr"); // Comoros
        COUNTRY_TO_LANGUAGE.put("MU", "fr"); // Mauritius
        COUNTRY_TO_LANGUAGE.put("SC", "fr"); // Seychelles
        COUNTRY_TO_LANGUAGE.put("RE", "fr"); // Réunion
        COUNTRY_TO_LANGUAGE.put("GP", "fr"); // Guadeloupe
        COUNTRY_TO_LANGUAGE.put("MQ", "fr"); // Martinique
        COUNTRY_TO_LANGUAGE.put("GF", "fr"); // French Guiana
        COUNTRY_TO_LANGUAGE.put("PF", "fr"); // French Polynesia
        COUNTRY_TO_LANGUAGE.put("NC", "fr"); // New Caledonia

        // Italian-speaking countries/regions
        COUNTRY_TO_LANGUAGE.put("IT", "it"); // Italy
        COUNTRY_TO_LANGUAGE.put("SM", "it"); // San Marino
        COUNTRY_TO_LANGUAGE.put("VA", "it"); // Vatican City

        // Portuguese-speaking countries (for future support)
        COUNTRY_TO_LANGUAGE.put("PT", "pt"); // Portugal
        COUNTRY_TO_LANGUAGE.put("BR", "pt"); // Brazil
        COUNTRY_TO_LANGUAGE.put("AO", "pt"); // Angola
        COUNTRY_TO_LANGUAGE.put("MZ", "pt"); // Mozambique
        COUNTRY_TO_LANGUAGE.put("GW", "pt"); // Guinea-Bissau
        COUNTRY_TO_LANGUAGE.put("CV", "pt"); // Cape Verde
        COUNTRY_TO_LANGUAGE.put("ST", "pt"); // São Tomé and Príncipe
        COUNTRY_TO_LANGUAGE.put("TL", "pt"); // East Timor

        // German-speaking countries (for future support)
        COUNTRY_TO_LANGUAGE.put("DE", "de"); // Germany
        COUNTRY_TO_LANGUAGE.put("AT", "de"); // Austria
        COUNTRY_TO_LANGUAGE.put("LI", "de"); // Liechtenstein

        // Chinese-speaking countries (for future support)
        COUNTRY_TO_LANGUAGE.put("CN", "zh"); // China
        COUNTRY_TO_LANGUAGE.put("TW", "zh"); // Taiwan
        COUNTRY_TO_LANGUAGE.put("HK", "zh"); // Hong Kong
        COUNTRY_TO_LANGUAGE.put("MO", "zh"); // Macau
        COUNTRY_TO_LANGUAGE.put("SG", "zh"); // Singapore (Chinese community)

        // Russian-speaking countries (for future support)
        COUNTRY_TO_LANGUAGE.put("RU", "ru"); // Russia
        COUNTRY_TO_LANGUAGE.put("BY", "ru"); // Belarus
        COUNTRY_TO_LANGUAGE.put("KZ", "ru"); // Kazakhstan
        COUNTRY_TO_LANGUAGE.put("KG", "ru"); // Kyrgyzstan

        // Japanese
        COUNTRY_TO_LANGUAGE.put("JP", "ja"); // Japan

        // Korean
        COUNTRY_TO_LANGUAGE.put("KR", "ko"); // South Korea
        COUNTRY_TO_LANGUAGE.put("KP", "ko"); // North Korea

        // Polish
        COUNTRY_TO_LANGUAGE.put("PL", "pl"); // Poland

        // Turkish
        COUNTRY_TO_LANGUAGE.put("TR", "tr"); // Turkey

        // Dutch-speaking countries
        COUNTRY_TO_LANGUAGE.put("NL", "nl"); // Netherlands
        COUNTRY_TO_LANGUAGE.put("SR", "nl"); // Suriname
        
        // Thailand
        COUNTRY_TO_LANGUAGE.put("TH", "th"); // Thailand

    }

    private final Logger logger;
    private final String configuredLanguage;
    private final String detectedLanguage;

    /**
     * Creates a new LocaleHelper with automatic detection.
     *
     * @param logger              The logger for debug output
     * @param configuredLanguage  The language configured in config.yml (can be null or "auto")
     */
    public LocaleHelper(Logger logger, String configuredLanguage) {
        this.logger = logger;
        this.configuredLanguage = configuredLanguage;
        this.detectedLanguage = detectLanguage();
    }

    /**
     * Detects the appropriate language based on configuration and system locale.
     *
     * @return The detected language code (e.g., "fr", "es", "it", "en")
     */
    private String detectLanguage() {
        Locale systemLocale = Locale.getDefault();
        String systemLanguage = systemLocale.getLanguage();
        String systemCountry = systemLocale.getCountry();

        logger.info("System locale detected: " + systemLocale.toLanguageTag());
        logger.info("  - Language code: " + systemLanguage);
        logger.info("  - Country code: " + systemCountry);

        // Priority 1: Check if a language is manually configured
        if (configuredLanguage != null && !configuredLanguage.isEmpty() && !configuredLanguage.equalsIgnoreCase("auto")) {
            logger.info("Using manually configured language: " + configuredLanguage);
            return configuredLanguage.toLowerCase();
        }

        // Priority 2: Try to map country to language
        if (systemCountry != null && !systemCountry.isEmpty()) {
            String mappedLanguage = COUNTRY_TO_LANGUAGE.get(systemCountry.toUpperCase());
            if (mappedLanguage != null) {
                logger.info("Language mapped from country " + systemCountry + " -> " + mappedLanguage);
                return mappedLanguage;
            }
        }

        // Priority 3: Use the system language directly
        logger.info("Using system language: " + systemLanguage);
        return systemLanguage;
    }

    /**
     * Gets the detected/configured language code.
     *
     * @return The language code to use for resource loading
     */
    public String getLanguage() {
        return detectedLanguage;
    }

    /**
     * Gets the language mapped to a specific country code.
     *
     * @param countryCode The ISO 3166-1 alpha-2 country code
     * @return The language code, or null if not mapped
     */
    public static String getLanguageForCountry(String countryCode) {
        return COUNTRY_TO_LANGUAGE.get(countryCode.toUpperCase());
    }

    /**
     * Checks if a country is mapped to a specific language.
     *
     * @param countryCode The ISO 3166-1 alpha-2 country code
     * @return true if the country has a language mapping
     */
    public static boolean hasMapping(String countryCode) {
        return COUNTRY_TO_LANGUAGE.containsKey(countryCode.toUpperCase());
    }

    /**
     * Gets all supported language codes that have country mappings.
     *
     * @return A map of country codes to language codes
     */
    public static Map<String, String> getAllMappings() {
        return new HashMap<>(COUNTRY_TO_LANGUAGE);
    }
}
