package fr.maxlego08.zauctionhouse.api.filter;

import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de {@link SearchQuery#parse}, {@link SearchField#fromKey} et des operateurs par defaut de
 * {@link SearchFilterType}.
 */
class SearchQueryTest {

    private static SearchFilterConfiguration defaults() {
        Map<SearchFilterType, String> operators = new EnumMap<>(SearchFilterType.class);
        for (SearchFilterType type : SearchFilterType.values()) operators.put(type, type.getDefaultOperator());
        return new SearchFilterConfiguration(operators);
    }

    private static SearchQuery parse(String raw) {
        return SearchQuery.parse(raw, defaults());
    }

    @Nested
    @DisplayName("recherche par defaut")
    class DefaultQueries {

        @Test
        @DisplayName("null et chaine vide donnent une requete par defaut vide")
        void parse_nullOrEmpty_isEmptyDefault() {
            for (String raw : new String[]{null, ""}) {
                SearchQuery query = parse(raw);
                assertTrue(query.isDefault());
                assertNull(query.field());
                assertNull(query.type());
                assertEquals("", query.value());
            }
        }

        @Test
        @DisplayName("un mot simple est une recherche par defaut sur tous les champs")
        void parse_plainWord_isDefault() {
            SearchQuery query = parse("diamond");

            assertTrue(query.isDefault());
            assertNull(query.field());
            assertNull(query.type());
            assertEquals("diamond", query.value());
        }

        @Test
        @DisplayName("un champ inconnu devant un operateur garde la requete brute")
        void parse_unknownField_keepsRawValue() {
            SearchQuery query = parse("price = 10");

            assertTrue(query.isDefault());
            assertEquals("price = 10", query.value());
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"= Notch", "~diamond", "== x", "~= y"})
        @DisplayName("un operateur en tete (index 0) n'est pas un filtre")
        void parse_operatorAtStart_isDefault(String raw) {
            SearchQuery query = parse(raw);

            assertTrue(query.isDefault());
            assertEquals(raw, query.value());
        }

        @Test
        @DisplayName("la requete brute n'est pas nettoyee en recherche par defaut")
        void parse_default_keepsSpaces() {
            assertEquals("  diamond  ", parse("  diamond  ").value());
        }
    }

    @Nested
    @DisplayName("recherche ciblee")
    class FilteredQueries {

        @ParameterizedTest(name = "\"{0}\" -> {1} {2} \"{3}\"")
        @CsvSource({
                "'name ~ diamond', NAME, CONTAINS, diamond",
                "'seller = Notch', SELLER, EQUALS, Notch",
                "'seller == notch', SELLER, EQUALS_IGNORE_CASE, notch",
                "'material ~= sword', MATERIAL, CONTAINS_IGNORE_CASE, sword",
                "'lore = Sharpness V', LORE, EQUALS, Sharpness V"
        })
        @DisplayName("chaque operateur par defaut est reconnu avec son champ")
        void parse_recognisesOperators(String raw, SearchField field, SearchFilterType type, String value) {
            SearchQuery query = parse(raw);

            assertFalse(query.isDefault());
            assertEquals(field, query.field());
            assertEquals(type, query.type());
            assertEquals(value, query.value());
        }

        @Test
        @DisplayName("\"==\" est teste avant \"=\" et \"~=\" avant \"~\"")
        void parse_longestOperatorWins() {
            assertEquals(SearchFilterType.EQUALS_IGNORE_CASE, parse("name == x").type());
            assertEquals("x", parse("name == x").value());
            assertEquals(SearchFilterType.CONTAINS_IGNORE_CASE, parse("name ~= x").type());
            assertEquals("x", parse("name ~= x").value());
        }

        @Test
        @DisplayName("la cle de champ est insensible a la casse")
        void parse_fieldKeyIsCaseInsensitive() {
            assertEquals(SearchField.NAME, parse("NAME = x").field());
            assertEquals(SearchField.SELLER, parse("SeLLeR = x").field());
        }

        @Test
        @DisplayName("les espaces autour du champ, de l'operateur et de la valeur sont supprimes")
        void parse_trimsSpaces() {
            SearchQuery query = parse("   name    =    Epic Sword   ");

            assertEquals(SearchField.NAME, query.field());
            assertEquals(SearchFilterType.EQUALS, query.type());
            assertEquals("Epic Sword", query.value());
        }

        @Test
        @DisplayName("sans espaces, l'operateur est quand meme reconnu")
        void parse_withoutSpaces() {
            SearchQuery query = parse("name=x");

            assertEquals(SearchField.NAME, query.field());
            assertEquals(SearchFilterType.EQUALS, query.type());
            assertEquals("x", query.value());
        }

        @Test
        @DisplayName("seule la premiere occurrence de l'operateur separe champ et valeur")
        void parse_valueMayContainOperator() {
            SearchQuery query = parse("name = a=b");

            assertEquals(SearchField.NAME, query.field());
            assertEquals(SearchFilterType.EQUALS, query.type());
            assertEquals("a=b", query.value());
        }

        @Test
        @DisplayName("un filtre sans valeur donne une valeur vide (la recherche rendra tout)")
        void parse_filterWithoutValue_hasEmptyValue() {
            SearchQuery query = parse("name ~");

            assertEquals(SearchField.NAME, query.field());
            assertEquals(SearchFilterType.CONTAINS, query.type());
            assertEquals("", query.value());
        }

        @Test
        @DisplayName("les operateurs configures remplacent les operateurs par defaut")
        void parse_usesConfiguredOperators() {
            Map<SearchFilterType, String> operators = new EnumMap<>(SearchFilterType.class);
            operators.put(SearchFilterType.CONTAINS, "like");
            operators.put(SearchFilterType.EQUALS, "is");
            operators.put(SearchFilterType.CONTAINS_IGNORE_CASE, "ilike");
            operators.put(SearchFilterType.EQUALS_IGNORE_CASE, "iis");
            SearchFilterConfiguration configuration = new SearchFilterConfiguration(operators);

            SearchQuery like = SearchQuery.parse("name like sword", configuration);
            assertEquals(SearchField.NAME, like.field());
            assertEquals(SearchFilterType.CONTAINS, like.type());
            assertEquals("sword", like.value());

            SearchQuery ilike = SearchQuery.parse("name ilike sword", configuration);
            assertEquals(SearchFilterType.CONTAINS_IGNORE_CASE, ilike.type(), "l'operateur le plus long est teste d'abord");
            assertEquals("sword", ilike.value());

            // l'ancien operateur n'est plus reconnu
            assertTrue(SearchQuery.parse("name ~ sword", configuration).isDefault());
        }
    }

    @Nested
    @DisplayName("SearchField et SearchFilterType")
    class Enums {

        @Test
        @DisplayName("fromKey resout chaque cle sans tenir compte de la casse")
        void fromKey_resolvesKeys() {
            assertEquals(SearchField.NAME, SearchField.fromKey("name"));
            assertEquals(SearchField.MATERIAL, SearchField.fromKey("MATERIAL"));
            assertEquals(SearchField.LORE, SearchField.fromKey("Lore"));
            assertEquals(SearchField.SELLER, SearchField.fromKey("seller"));
            for (SearchField field : SearchField.values()) {
                assertEquals(field, SearchField.fromKey(field.getKey()));
            }
        }

        @Test
        @DisplayName("fromKey rend null pour null, vide ou inconnu")
        void fromKey_unknown_isNull() {
            assertNull(SearchField.fromKey(null));
            assertNull(SearchField.fromKey(""));
            assertNull(SearchField.fromKey("price"));
            assertNull(SearchField.fromKey(" name"));
        }

        @Test
        @DisplayName("les operateurs par defaut sont ~ = ~= ==")
        void defaultOperators() {
            assertEquals("~", SearchFilterType.CONTAINS.getDefaultOperator());
            assertEquals("=", SearchFilterType.EQUALS.getDefaultOperator());
            assertEquals("~=", SearchFilterType.CONTAINS_IGNORE_CASE.getDefaultOperator());
            assertEquals("==", SearchFilterType.EQUALS_IGNORE_CASE.getDefaultOperator());
        }
    }
}
