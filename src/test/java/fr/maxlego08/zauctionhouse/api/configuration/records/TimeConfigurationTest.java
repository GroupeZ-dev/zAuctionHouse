package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeConfigurationTest {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    private TimeConfiguration defaults() {
        return TimeConfiguration.of(plugin, new YamlConfiguration());
    }

    /**
     * Gabarits en toutes lettres, pour exercer les labels singulier/pluriel et le nettoyage des
     * segments a zero fait par {@code format}.
     */
    private TimeConfiguration verbose() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("time.time-day", "%d %day% %02d %hour% %02d %minute%");
        configuration.set("time.time-hour", "%d %hour% %02d %minute% %02d %second%");
        configuration.set("time.time-minute", "%02d %minute% %02d %second%");
        configuration.set("time.time-second", "%02d %second%");
        return TimeConfiguration.of(plugin, configuration);
    }

    @Nested
    @DisplayName("chargement")
    class Loading {

        @Test
        @DisplayName("cles absentes : valeurs par defaut")
        void of_missingKeys_defaults() {
            TimeConfiguration time = defaults();

            assertEquals("second", time.second());
            assertEquals("seconds", time.seconds());
            assertEquals("minute", time.minute());
            assertEquals("minutes", time.minutes());
            assertEquals("hour", time.hour());
            assertEquals("hours", time.hours());
            assertEquals("day", time.day());
            assertEquals("days", time.days());
            assertEquals("%02dᴅ %02dʜ %02dᴍ", time.timeDay());
            assertEquals("%02dʜ %02dᴍ %02ds", time.timeHour());
            assertEquals("%02dᴍ %02ds", time.timeMinute());
            assertEquals("%02ds", time.timeSecond());
        }

        @Test
        @DisplayName("cle vide : valeur par defaut (et non chaine vide)")
        void of_emptyValue_fallsBackToDefault() {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("time.second", "");
            configuration.set("time.days", "jours");

            TimeConfiguration time = TimeConfiguration.of(plugin, configuration);

            assertEquals("second", time.second());
            assertEquals("jours", time.days());
        }
    }

    @Nested
    @DisplayName("gabarits par defaut")
    class DefaultTemplates {

        @ParameterizedTest(name = "{0} ms => \"{1}\"")
        @CsvSource({
                "0, 00s",
                "999, 00s",
                "5000, 05s",
                "59999, 59s",
                "60000, 01ᴍ 00s",
                "90000, 01ᴍ 30s",
                "3599999, 59ᴍ 59s",
                "3600000, 01ʜ 00ᴍ 00s",
                "3661000, 01ʜ 01ᴍ 01s",
                "86399000, 23ʜ 59ᴍ 59s",
                "86400000, 01ᴅ 00ʜ 00ᴍ",
                "90061000, 01ᴅ 01ʜ 01ᴍ",
                "864000000, 10ᴅ 00ʜ 00ᴍ",
        })
        void getStringTime_picksTheRightTemplate(long millis, String expected) {
            assertEquals(expected, defaults().getStringTime(millis));
        }

        @Test
        @DisplayName("chaque format long est accessible directement")
        void individualFormats() {
            TimeConfiguration time = defaults();

            assertEquals("07s", time.getFormatLongSecondes(7 * SECOND));
            assertEquals("02ᴍ 05s", time.getFormatLongMinutes(2 * MINUTE + 5 * SECOND));
            assertEquals("03ʜ 02ᴍ 01s", time.getFormatLongHours(3 * HOUR + 2 * MINUTE + SECOND));
            assertEquals("02ᴅ 03ʜ 04ᴍ", time.getFormatLongDays(2 * DAY + 3 * HOUR + 4 * MINUTE + 5 * SECOND));
        }
    }

    @Nested
    @DisplayName("gabarits avec labels")
    class VerboseTemplates {

        @Test
        @DisplayName("singulier a 0 et 1, pluriel au-dela")
        void labels_singularAndPlural() {
            TimeConfiguration time = verbose();

            assertEquals("01 second", time.getStringTime(SECOND));
            assertEquals("02 seconds", time.getStringTime(2 * SECOND));
            assertEquals("01 minute 01 second", time.getStringTime(MINUTE + SECOND));
            assertEquals("02 minutes 30 seconds", time.getStringTime(2 * MINUTE + 30 * SECOND));
            assertEquals("1 hour 01 minute 01 second", time.getStringTime(HOUR + MINUTE + SECOND));
            assertEquals("2 hours 10 minutes 10 seconds", time.getStringTime(2 * HOUR + 10 * MINUTE + 10 * SECOND));
            assertEquals("1 day 01 hour 01 minute", time.getStringTime(DAY + HOUR + MINUTE));
            assertEquals("3 days 02 hours 02 minutes", time.getStringTime(3 * DAY + 2 * HOUR + 2 * MINUTE));
        }

        @Test
        @DisplayName("les segments ' 00 <unite>' sont retires")
        void zeroSegments_removed() {
            TimeConfiguration time = verbose();

            assertEquals("01 minute", time.getStringTime(MINUTE));
            assertEquals("1 hour", time.getStringTime(HOUR));
            assertEquals("2 days", time.getStringTime(2 * DAY));
            assertEquals("1 day 05 minutes", time.getStringTime(DAY + 5 * MINUTE));
            assertEquals("00 second", time.getStringTime(0), "le premier segment n'a pas d'espace devant : il est conserve");
        }

        @Test
        @DisplayName("format() ne retire que les segments connus et supprime les espaces de bord")
        void format_trimsAndRemovesKnownSegments() {
            TimeConfiguration time = verbose();

            assertEquals("05 s", time.format("  05 s "));
            assertEquals("1 hour", time.format("1 hour 00 minute 00 second"));
            assertEquals("1 hour 00 sec", time.format("1 hour 00 sec"), "'sec' n'est pas le label configure");
            assertEquals("1 houres", time.format("1 hour 00 secondes"), "le remplacement est textuel : ' 00 second' est retire au milieu de 'secondes'");
        }
    }
}
