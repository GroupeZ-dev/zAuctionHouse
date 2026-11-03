package fr.maxlego08.zauctionhouse.api.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Parsing des arguments de commande. {@link Arguments} est abstraite et ses methodes protegees :
 * le test vit dans le meme package et utilise une sous-classe minimale qui expose {@code args}
 * et {@code parentCount}.
 */
class ArgumentsTest {

    /**
     * Sous-classe minimale : aucun comportement ajoute, seulement un acces aux champs.
     */
    static final class TestArguments extends Arguments {
        TestArguments(int parentCount, String... args) {
            this.args = args;
            this.parentCount = parentCount;
        }
    }

    static TestArguments args(String... args) {
        return new TestArguments(0, args);
    }

    @Nested
    @DisplayName("argAsString")
    class StringArguments {

        @Test
        @DisplayName("rend l'argument a l'index demande")
        void argAsString_validIndex_returnsValue() {
            assertEquals("hello", args("hello", "world").argAsString(0));
            assertEquals("world", args("hello", "world").argAsString(1));
        }

        @Test
        @DisplayName("rend null hors bornes (trop grand ou negatif)")
        void argAsString_outOfBounds_returnsNull() {
            assertNull(args("hello").argAsString(1));
            assertNull(args("hello").argAsString(-1));
            assertNull(args().argAsString(0));
        }

        @Test
        @DisplayName("decale l'index du nombre de parents (les sous-commandes consomment des arguments)")
        void argAsString_parentCount_shiftsIndex() {
            TestArguments arguments = new TestArguments(1, "sell", "100", "2");
            assertEquals("100", arguments.argAsString(0));
            assertEquals("2", arguments.argAsString(1));
            assertNull(arguments.argAsString(2));
        }

        @Test
        @DisplayName("rend la valeur par defaut quand l'argument manque ou que l'index est -1")
        void argAsString_withDefault() {
            assertEquals("x", args("x").argAsString(0, "def"));
            assertEquals("def", args("x").argAsString(1, "def"));
            assertEquals("def", args("x").argAsString(-1, "def"), "-1 est l'index d'un argument non configure (VCommandArgument)");
            assertNull(args().argAsString(0, null));
        }

        @Test
        @DisplayName("un tableau d'arguments null ne leve pas : null ou defaut")
        void argAsString_nullArray_isSafe() {
            TestArguments arguments = new TestArguments(0, (String[]) null);
            assertNull(arguments.argAsString(0));
            assertEquals("def", arguments.argAsString(0, "def"));
        }
    }

    @Nested
    @DisplayName("argAsBoolean")
    class BooleanArguments {

        @ParameterizedTest(name = "\"{0}\" -> true")
        @ValueSource(strings = {"true", "TRUE", "True"})
        void argAsBoolean_trueSpellings(String value) {
            assertTrue(args(value).argAsBoolean(0));
        }

        @ParameterizedTest(name = "\"{0}\" -> false")
        @ValueSource(strings = {"false", "yes", "1", "on", ""})
        void argAsBoolean_anythingElseIsFalse(String value) {
            assertFalse(args(value).argAsBoolean(0));
        }

        @Test
        @DisplayName("un argument absent vaut false sans lever")
        void argAsBoolean_missing_isFalse() {
            assertFalse(args().argAsBoolean(0));
        }

        @Test
        @DisplayName("avec valeur par defaut : l'argument present est parse")
        void argAsBoolean_withDefault_present() {
            assertTrue(args("true").argAsBoolean(0, false));
            assertFalse(args("false").argAsBoolean(0, true));
        }

        @Test
        @Disabled("BUG: Boolean.valueOf(null) rend false sans lever, la valeur par defaut n'est donc jamais utilisee quand l'argument manque, contrairement a la Javadoc (Arguments.java:63 argAsBoolean(int, boolean))")
        @DisplayName("avec valeur par defaut : l'argument absent rend la valeur par defaut")
        void argAsBoolean_withDefault_missing_returnsDefault() {
            assertTrue(args().argAsBoolean(0, true));
            assertTrue(args("x").argAsBoolean(5, true));
        }
    }

    @Nested
    @DisplayName("argAsInteger / argAsLong")
    class IntegerArguments {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({"42, 42", "-7, -7", "0, 0", "+3, 3"})
        void argAsInteger_valid(String value, int expected) {
            assertEquals(expected, args(value).argAsInteger(0));
            assertEquals(expected, args(value).argAsInteger(0, 999));
        }

        @ParameterizedTest(name = "\"{0}\" leve NumberFormatException")
        @ValueSource(strings = {"abc", "1.5", "1,5", "", " 1", "99999999999"})
        void argAsInteger_invalid_throws(String value) {
            assertThrows(NumberFormatException.class, () -> args(value).argAsInteger(0));
        }

        @Test
        @DisplayName("un argument absent leve NumberFormatException (Integer.valueOf(null))")
        void argAsInteger_missing_throws() {
            assertThrows(NumberFormatException.class, () -> args().argAsInteger(0));
        }

        @Test
        @DisplayName("avec valeur par defaut : invalide ou absent rend la valeur par defaut")
        void argAsInteger_withDefault() {
            assertEquals(5, args("abc").argAsInteger(0, 5));
            assertEquals(5, args().argAsInteger(0, 5));
            assertEquals(5, args("1.5").argAsInteger(0, 5));
            assertEquals(-1, args("x").argAsInteger(-1, -1));
        }

        @Test
        @DisplayName("argAsLong accepte des valeurs au-dela de l'entier 32 bits")
        void argAsLong_valid() {
            assertEquals(99999999999L, args("99999999999").argAsLong(0));
            assertEquals(Long.MAX_VALUE, args(String.valueOf(Long.MAX_VALUE)).argAsLong(0, 0));
            assertEquals(-12L, args("-12").argAsLong(0));
        }

        @Test
        @DisplayName("argAsLong invalide ou absent : exception sans defaut, defaut sinon")
        void argAsLong_invalid() {
            assertThrows(NumberFormatException.class, () -> args("abc").argAsLong(0));
            assertThrows(NumberFormatException.class, () -> args().argAsLong(0));
            assertEquals(7L, args("abc").argAsLong(0, 7L));
            assertEquals(7L, args().argAsLong(0, 7L));
        }
    }

    @Nested
    @DisplayName("argAsDouble")
    class DoubleArguments {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource({"2.25, 2.25", "'1,5', 1.5", "10, 10.0", "-0.5, -0.5", "1e3, 1000.0"})
        void argAsDouble_valid_acceptsCommaAsDecimalSeparator(String value, double expected) {
            assertEquals(expected, args(value).argAsDouble(0), 1e-9);
            assertEquals(expected, args(value).argAsDouble(0, 999), 1e-9);
        }

        @Test
        @DisplayName("invalide leve NumberFormatException, absent leve NullPointerException")
        void argAsDouble_invalidOrMissing_throws() {
            assertThrows(NumberFormatException.class, () -> args("abc").argAsDouble(0));
            assertThrows(NullPointerException.class, () -> args().argAsDouble(0));
        }

        @Test
        @DisplayName("avec valeur par defaut : invalide ou absent rend la valeur par defaut")
        void argAsDouble_withDefault() {
            assertEquals(1.5, args("abc").argAsDouble(0, 1.5), 1e-9);
            assertEquals(1.5, args().argAsDouble(0, 1.5), 1e-9);
            assertEquals(1.5, args("x").argAsDouble(-1, 1.5), 1e-9);
        }
    }

    @Nested
    @DisplayName("argAsPlayer / argAsOfflinePlayer / argAsWorld (Bukkit statique mocke)")
    class BukkitArguments {

        @Test
        @DisplayName("argAsPlayer resout le joueur en ligne par son nom")
        void argAsPlayer_known_returnsPlayer() {
            Player alice = mock(Player.class);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Alice")).thenReturn(alice);

                assertSame(alice, args("Alice").argAsPlayer(0));
                assertSame(alice, args("Alice").argAsPlayer(0, null));
            }
        }

        @Test
        @DisplayName("argAsPlayer rend null pour un joueur inconnu, meme avec une valeur par defaut (pas d'exception)")
        void argAsPlayer_unknown_returnsNull() {
            Player fallback = mock(Player.class);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Nobody")).thenReturn(null);

                assertNull(args("Nobody").argAsPlayer(0));
                assertNull(args("Nobody").argAsPlayer(0, fallback), "le defaut ne couvre que les exceptions, pas un joueur introuvable");
            }
        }

        @Test
        @DisplayName("argAsPlayer avec defaut rend le defaut quand Bukkit leve (argument absent)")
        void argAsPlayer_missing_returnsDefaultWhenBukkitThrows() {
            Player fallback = mock(Player.class);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer((String) null)).thenThrow(new IllegalArgumentException("Name cannot be null"));

                assertSame(fallback, args().argAsPlayer(0, fallback));
                assertThrows(IllegalArgumentException.class, () -> args().argAsPlayer(0));
            }
        }

        @Test
        @DisplayName("argAsOfflinePlayer delegue a Bukkit.getOfflinePlayer(String)")
        void argAsOfflinePlayer_delegates() {
            OfflinePlayer offline = mock(OfflinePlayer.class);
            OfflinePlayer fallback = mock(OfflinePlayer.class);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getOfflinePlayer("Bob")).thenReturn(offline);
                bukkit.when(() -> Bukkit.getOfflinePlayer((String) null)).thenThrow(new IllegalArgumentException());

                assertSame(offline, args("Bob").argAsOfflinePlayer(0));
                assertSame(offline, args("Bob").argAsOfflinePlayer(0, fallback));
                assertSame(fallback, args().argAsOfflinePlayer(0, fallback));
                assertThrows(IllegalArgumentException.class, () -> args().argAsOfflinePlayer(0));
            }
        }

        @Test
        @DisplayName("argAsWorld resout le monde par son nom et absorbe les exceptions")
        void argAsWorld_delegatesAndSwallows() {
            World world = mock(World.class);
            World fallback = mock(World.class);
            try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
                bukkit.when(() -> Bukkit.getWorld("nether")).thenReturn(null);
                bukkit.when(() -> Bukkit.getWorld((String) null)).thenThrow(new IllegalArgumentException());

                assertSame(world, args("world").argAsWorld(0));
                assertNull(args("nether").argAsWorld(0));
                assertNull(args().argAsWorld(0), "l'exception est absorbee : null");
                assertSame(world, args("world").argAsWorld(0, fallback));
                assertSame(fallback, args().argAsWorld(0, fallback));
                assertNull(args("nether").argAsWorld(0, fallback), "monde introuvable sans exception : null, pas le defaut");
            }
        }
    }
}
