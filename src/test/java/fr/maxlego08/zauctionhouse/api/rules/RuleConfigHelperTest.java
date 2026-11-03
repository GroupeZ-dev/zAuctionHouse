package fr.maxlego08.zauctionhouse.api.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleConfigHelperTest {

    private static Map<String, Object> mapWithNull(String key) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, null);
        return map;
    }

    @Nested
    @DisplayName("getString")
    class GetString {

        @Test
        @DisplayName("valeur String rendue telle quelle")
        void string_returnedAsIs() {
            assertEquals("material", RuleConfigHelper.getString(Map.of("type", "material"), "type"));
        }

        @Test
        @DisplayName("valeur non-String convertie via String.valueOf")
        void nonString_isStringified() {
            assertEquals("42", RuleConfigHelper.getString(Map.of("k", 42), "k"));
            assertEquals("true", RuleConfigHelper.getString(Map.of("k", true), "k"));
        }

        @Test
        @DisplayName("cle absente ou valeur null => null")
        void missing_returnsNull() {
            assertNull(RuleConfigHelper.getString(Map.of(), "k"));
            assertNull(RuleConfigHelper.getString(mapWithNull("k"), "k"));
        }

        @Test
        @DisplayName("avec defaut : rendu seulement quand la valeur est absente")
        void withDefault() {
            assertEquals("def", RuleConfigHelper.getString(Map.of(), "k", "def"));
            assertEquals("def", RuleConfigHelper.getString(mapWithNull("k"), "k", "def"));
            assertEquals("v", RuleConfigHelper.getString(Map.of("k", "v"), "k", "def"));
        }
    }

    @Nested
    @DisplayName("getBoolean")
    class GetBoolean {

        @Test
        @DisplayName("Boolean natif")
        void nativeBoolean() {
            assertTrue(RuleConfigHelper.getBoolean(Map.of("k", true), "k", false));
            assertFalse(RuleConfigHelper.getBoolean(Map.of("k", false), "k", true));
        }

        @Test
        @DisplayName("chaine : Boolean.parseBoolean ('true' insensible a la casse, tout le reste faux)")
        void stringParsing() {
            assertTrue(RuleConfigHelper.getBoolean(Map.of("k", "true"), "k", false));
            assertTrue(RuleConfigHelper.getBoolean(Map.of("k", "TRUE"), "k", false));
            assertFalse(RuleConfigHelper.getBoolean(Map.of("k", "yes"), "k", true));
            assertFalse(RuleConfigHelper.getBoolean(Map.of("k", "1"), "k", true));
        }

        @Test
        @DisplayName("absent ou null => defaut")
        void missing_returnsDefault() {
            assertTrue(RuleConfigHelper.getBoolean(Map.of(), "k", true));
            assertFalse(RuleConfigHelper.getBoolean(Map.of(), "k", false));
            assertTrue(RuleConfigHelper.getBoolean(mapWithNull("k"), "k", true));
        }
    }

    @Nested
    @DisplayName("getInt")
    class GetInt {

        @Test
        @DisplayName("Number : intValue (double tronque, long reduit)")
        void numbers() {
            assertEquals(42, RuleConfigHelper.getInt(Map.of("k", 42), "k", 0));
            assertEquals(42, RuleConfigHelper.getInt(Map.of("k", 42.9), "k", 0));
            assertEquals(42, RuleConfigHelper.getInt(Map.of("k", 42L), "k", 0));
        }

        @Test
        @DisplayName("chaine numerique parsee ; chaine invalide => defaut")
        void strings() {
            assertEquals(42, RuleConfigHelper.getInt(Map.of("k", "42"), "k", 0));
            assertEquals(-5, RuleConfigHelper.getInt(Map.of("k", "-5"), "k", 0));
            assertEquals(7, RuleConfigHelper.getInt(Map.of("k", "abc"), "k", 7));
            assertEquals(7, RuleConfigHelper.getInt(Map.of("k", "4.2"), "k", 7), "Integer.parseInt refuse les decimales");
        }

        @Test
        @DisplayName("absent ou null => defaut")
        void missing_returnsDefault() {
            assertEquals(7, RuleConfigHelper.getInt(Map.of(), "k", 7));
            assertEquals(7, RuleConfigHelper.getInt(mapWithNull("k"), "k", 7));
        }
    }

    @Nested
    @DisplayName("getStringList")
    class GetStringList {

        @Test
        @DisplayName("liste de chaines rendue dans l'ordre")
        void stringList() {
            assertEquals(List.of("a", "b"), RuleConfigHelper.getStringList(Map.of("k", List.of("a", "b")), "k"));
        }

        @Test
        @DisplayName("elements non-String convertis, elements null ignores")
        void mixedElements() {
            assertEquals(List.of("1", "true", "x"), RuleConfigHelper.getStringList(Map.of("k", Arrays.asList(1, null, true, "x")), "k"));
        }

        @Test
        @DisplayName("absent, null ou pas une liste => liste vide")
        void notAList_returnsEmpty() {
            assertTrue(RuleConfigHelper.getStringList(Map.of(), "k").isEmpty());
            assertTrue(RuleConfigHelper.getStringList(mapWithNull("k"), "k").isEmpty());
            assertTrue(RuleConfigHelper.getStringList(Map.of("k", "a"), "k").isEmpty());
            assertTrue(RuleConfigHelper.getStringList(Map.of("k", Map.of("a", "b")), "k").isEmpty());
        }

        @Test
        @DisplayName("la liste rendue est modifiable (copie)")
        void returnedList_isMutableCopy() {
            List<String> source = List.of("a");
            List<String> result = RuleConfigHelper.getStringList(Map.of("k", source), "k");
            result.add("b");

            assertEquals(List.of("a", "b"), result);
            assertEquals(List.of("a"), source);
        }
    }

    @Nested
    @DisplayName("getIntegerList")
    class GetIntegerList {

        @Test
        @DisplayName("Number et chaines numeriques convertis, chaines invalides et nulls ignores")
        void mixedElements() {
            assertEquals(List.of(1, 2, 3, 4), RuleConfigHelper.getIntegerList(Map.of("k", Arrays.asList(1, "2", 3.7, null, "abc", 4L)), "k"));
        }

        @Test
        @DisplayName("absent ou pas une liste => liste vide")
        void notAList_returnsEmpty() {
            assertTrue(RuleConfigHelper.getIntegerList(Map.of(), "k").isEmpty());
            assertTrue(RuleConfigHelper.getIntegerList(Map.of("k", 1), "k").isEmpty());
            assertTrue(RuleConfigHelper.getIntegerList(Map.of("k", "1,2"), "k").isEmpty());
        }

        @Test
        @DisplayName("liste ne contenant que des invalides => liste vide")
        void onlyInvalid_returnsEmpty() {
            assertTrue(RuleConfigHelper.getIntegerList(Map.of("k", List.of("a", "b")), "k").isEmpty());
        }
    }

    @Nested
    @DisplayName("getMapList")
    class GetMapList {

        @Test
        @DisplayName("ne conserve que les elements Map, dans l'ordre")
        void onlyMaps() {
            Map<String, Object> first = Map.of("type", "a");
            Map<String, Object> second = Map.of("type", "b");

            List<Map<?, ?>> result = RuleConfigHelper.getMapList(Map.of("k", Arrays.asList(first, "junk", null, 3, second)), "k");

            assertEquals(List.of(first, second), result);
        }

        @Test
        @DisplayName("absent ou pas une liste => liste vide")
        void notAList_returnsEmpty() {
            assertTrue(RuleConfigHelper.getMapList(Map.of(), "k").isEmpty());
            assertTrue(RuleConfigHelper.getMapList(Map.of("k", Map.of("type", "a")), "k").isEmpty(), "une Map seule n'est pas une liste de Maps");
        }
    }
}
