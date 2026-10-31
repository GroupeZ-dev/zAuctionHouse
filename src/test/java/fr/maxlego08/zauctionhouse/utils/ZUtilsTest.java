package fr.maxlego08.zauctionhouse.utils;

import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZUtilsTest {

    enum Mode {ALPHA, BETA}

    /**
     * Records : leur constructeur canonique conserve les noms de parametres a l'execution, ce
     * dont depend {@code createInstanceFromMap} (les classes de configuration du plugin sont
     * elles aussi des records).
     */
    public record Boxed(String name, Integer count, Boolean enabled, Double ratio, Long big) {
    }

    public record Primitives(int count, boolean enabled, double ratio, long big, float f) {
    }

    public record Rich(Mode mode, BigDecimal price, UUID id, String[] tags, int[] numbers, String maxCount) {
    }

    private final ZUtils utils = new ZUtils() {
    };
    private final Logger logger = mock(Logger.class);

    private static Constructor<?> constructorOf(Class<?> type) {
        return type.getConstructors()[0];
    }

    @Nested
    @DisplayName("convertToRequiredType")
    class ConvertToRequiredType {

        @Test
        @DisplayName("null vers un type numerique ou booleen rend la valeur zero du type")
        void null_toPrimitiveDefaults() {
            assertEquals(0, utils.convertToRequiredType(logger, null, int.class));
            assertEquals(0, utils.convertToRequiredType(logger, null, Integer.class));
            assertEquals(0.0, utils.convertToRequiredType(logger, null, double.class));
            assertEquals(0L, utils.convertToRequiredType(logger, null, Long.class));
            assertEquals(0f, utils.convertToRequiredType(logger, null, float.class));
            assertEquals(false, utils.convertToRequiredType(logger, null, boolean.class));
            assertNull(utils.convertToRequiredType(logger, null, String.class));
            assertNull(utils.convertToRequiredType(logger, null, Mode.class));
        }

        @Test
        @DisplayName("conversions depuis une chaine vers chaque type supporte")
        void string_toEachType() {
            assertEquals(Mode.BETA, utils.convertToRequiredType(logger, "BETA", Mode.class));
            assertEquals(new BigDecimal("12.50"), utils.convertToRequiredType(logger, "12.50", BigDecimal.class));
            UUID uuid = UUID.randomUUID();
            assertEquals(uuid, utils.convertToRequiredType(logger, uuid.toString(), UUID.class));
            assertEquals(42, utils.convertToRequiredType(logger, "42", int.class));
            assertEquals(42, utils.convertToRequiredType(logger, "42", Integer.class));
            assertEquals(1.5, utils.convertToRequiredType(logger, "1.5", double.class));
            assertEquals(9_000_000_000L, utils.convertToRequiredType(logger, "9000000000", long.class));
            assertEquals(true, utils.convertToRequiredType(logger, "true", Boolean.class));
            assertEquals(false, utils.convertToRequiredType(logger, "nope", boolean.class));
            assertEquals(2.5f, utils.convertToRequiredType(logger, "2.5", Float.class));
        }

        @Test
        @DisplayName("un nombre YAML (Integer/Double) est converti via toString vers le type cible")
        void number_toOtherNumber() {
            assertEquals(new BigDecimal("10"), utils.convertToRequiredType(logger, 10, BigDecimal.class));
            assertEquals(3.0, utils.convertToRequiredType(logger, 3, double.class));
            assertEquals(7L, utils.convertToRequiredType(logger, 7, Long.class));
            assertEquals(7, utils.convertToRequiredType(logger, 7, int.class));
        }

        @Test
        @DisplayName("un type non gere (String, List) est rendu tel quel")
        void unsupportedType_passThrough() {
            assertEquals("abc", utils.convertToRequiredType(logger, "abc", String.class));
            List<String> list = List.of("a");
            assertSame(list, utils.convertToRequiredType(logger, list, List.class));
        }

        @Test
        @DisplayName("echec de conversion Integer : l'exception est journalisee puis relancee")
        void invalidInteger_throws() {
            assertThrows(NumberFormatException.class, () -> utils.convertToRequiredType(logger, "abc", int.class));
        }

        @Test
        @DisplayName("echec de conversion enum/BigDecimal/UUID/Double/Long/Float : journalise et rend la valeur d'origine")
        void invalidOtherTypes_returnOriginal() {
            assertEquals("GAMMA", utils.convertToRequiredType(logger, "GAMMA", Mode.class));
            assertEquals("x", utils.convertToRequiredType(logger, "x", BigDecimal.class));
            assertEquals("not-a-uuid", utils.convertToRequiredType(logger, "not-a-uuid", UUID.class));
            assertEquals("x", utils.convertToRequiredType(logger, "x", double.class));
            assertEquals("x", utils.convertToRequiredType(logger, "x", Long.class));
            assertEquals("x", utils.convertToRequiredType(logger, "x", float.class));
        }
    }

    @Nested
    @DisplayName("createInstanceFromMap")
    class CreateInstanceFromMap {

        @Test
        @DisplayName("construit un record a partir des noms de parametres, avec conversion de type")
        void buildsFromParameterNames() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "Alice");
            map.put("count", "5");
            map.put("enabled", "true");
            map.put("ratio", 2);
            map.put("big", 3);

            Boxed boxed = (Boxed) utils.createInstanceFromMap(logger, constructorOf(Boxed.class), map);

            assertEquals("Alice", boxed.name());
            assertEquals(5, boxed.count());
            assertEquals(true, boxed.enabled());
            assertEquals(2.0, boxed.ratio());
            assertEquals(3L, boxed.big());
        }

        @Test
        @DisplayName("les valeurs manquantes des types Number/Boolean enveloppes valent 0/false, une String manquante vaut null")
        void missingBoxedValues_defaultToZero() {
            Boxed boxed = (Boxed) utils.createInstanceFromMap(logger, constructorOf(Boxed.class), Map.of());

            assertNull(boxed.name());
            assertEquals(0, boxed.count());
            assertEquals(false, boxed.enabled());
            assertEquals(0.0, boxed.ratio());
            assertEquals(0L, boxed.big());
        }

        @Test
        @DisplayName("les types primitifs presents sont convertis depuis des chaines ou des nombres")
        void primitives_present() {
            Map<String, Object> map = Map.of("count", 4, "enabled", "true", "ratio", "0.25", "big", "77", "f", 1);
            Primitives primitives = (Primitives) utils.createInstanceFromMap(logger, constructorOf(Primitives.class), map);
            assertEquals(4, primitives.count());
            assertTrue(primitives.enabled());
            assertEquals(0.25, primitives.ratio());
            assertEquals(77L, primitives.big());
            assertEquals(1f, primitives.f());
        }

        @Test
        @DisplayName("un primitif manquant ne recoit pas de defaut et fait echouer la construction (RuntimeException)")
        void primitives_missing_throws() {
            Map<String, Object> map = Map.of("count", 4, "enabled", true, "ratio", 1.0, "big", 1L);
            assertThrows(RuntimeException.class, () -> utils.createInstanceFromMap(logger, constructorOf(Primitives.class), map));
        }

        @Test
        @DisplayName("enum, BigDecimal, UUID et tableaux (depuis des listes) sont convertis ; la cle kebab-case est acceptee")
        void richTypes_andKebabKey() {
            UUID uuid = UUID.randomUUID();
            Map<String, Object> map = new HashMap<>();
            map.put("mode", "ALPHA");
            map.put("price", 19.99);
            map.put("id", uuid.toString());
            map.put("tags", List.of("a", "b"));
            map.put("numbers", List.of("1", 2, "3"));
            map.put("max-count", "kebab");

            Rich rich = (Rich) utils.createInstanceFromMap(logger, constructorOf(Rich.class), map);

            assertEquals(Mode.ALPHA, rich.mode());
            assertEquals(new BigDecimal("19.99"), rich.price());
            assertEquals(uuid, rich.id());
            assertArrayEquals(new String[]{"a", "b"}, rich.tags());
            assertArrayEquals(new int[]{1, 2, 3}, rich.numbers());
            assertEquals("kebab", rich.maxCount());
        }

        @Test
        @DisplayName("la cle camelCase a priorite sur la cle kebab-case quand les deux existent")
        void camelCaseKey_hasPriority() {
            Map<String, Object> map = new HashMap<>();
            map.put("maxCount", "camel");
            map.put("max-count", "kebab");
            map.put("mode", "BETA");
            Rich rich = (Rich) utils.createInstanceFromMap(logger, constructorOf(Rich.class), map);
            assertEquals("camel", rich.maxCount());
            assertNull(rich.tags());
        }

        @Test
        @DisplayName("une valeur inconvertible (ex. enum invalide) laisse la valeur brute et fait echouer la construction")
        void invalidValue_throwsRuntimeException() {
            Map<String, Object> map = Map.of("mode", "GAMMA");
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> utils.createInstanceFromMap(logger, constructorOf(Rich.class), map));
            assertTrue(exception.getMessage().contains("Failed to create instance"));
        }
    }

    @Test
    @DisplayName("hasPermission delegue au Permissible")
    void hasPermission_delegates() {
        Permissible permissible = mock(Permissible.class);
        when(permissible.hasPermission("zauctionhouse.use")).thenReturn(true);
        assertTrue(utils.hasPermission(permissible, "zauctionhouse.use"));
        assertFalse(utils.hasPermission(permissible, "zauctionhouse.admin"));
    }

    @Test
    @DisplayName("files parcourt recursivement le dossier et ne livre que les fichiers .yml")
    void files_walksYamlFilesOnly(@TempDir File folder) throws Exception {
        File sub = new File(folder, "sub/deeper");
        assertTrue(sub.mkdirs());
        Files.writeString(new File(folder, "a.yml").toPath(), "a: 1");
        Files.writeString(new File(folder, "b.txt").toPath(), "b");
        Files.writeString(new File(sub, "c.yml").toPath(), "c: 3");
        Files.writeString(new File(folder, "sub/d.yaml").toPath(), "d: 4");

        List<String> names = new ArrayList<>();
        utils.files(folder, file -> names.add(file.getName()));

        names.sort(String::compareTo);
        assertEquals(List.of("a.yml", "c.yml"), names);
    }

    @Test
    @DisplayName("files sur un dossier inexistant journalise l'erreur sans lever")
    void files_missingFolder_doesNotThrow(@TempDir File folder) {
        List<String> names = new ArrayList<>();
        utils.files(new File(folder, "missing"), file -> names.add(file.getName()));
        assertTrue(names.isEmpty());
    }
}
