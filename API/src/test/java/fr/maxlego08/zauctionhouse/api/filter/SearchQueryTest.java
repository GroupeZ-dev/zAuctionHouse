package fr.maxlego08.zauctionhouse.api.filter;

import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchQueryTest {

    private final SearchFilterConfiguration configuration = new SearchFilterConfiguration(Map.of(
            SearchFilterType.CONTAINS, "~",
            SearchFilterType.EQUALS, "=",
            SearchFilterType.CONTAINS_IGNORE_CASE, "~=",
            SearchFilterType.EQUALS_IGNORE_CASE, "=="
    ));

    @Test
    void parsesExplicitAllField() {
        SearchQuery query = SearchQuery.parse("all ~= sandaly", configuration);

        assertEquals(SearchField.ALL, query.field());
        assertEquals(SearchFilterType.CONTAINS_IGNORE_CASE, query.type());
        assertEquals("sandaly", query.value());
    }

    @Test
    void parsesNameEqualsIgnoreCase() {
        SearchQuery query = SearchQuery.parse("name == sandaly", configuration);

        assertEquals(SearchField.NAME, query.field());
        assertEquals(SearchFilterType.EQUALS_IGNORE_CASE, query.type());
        assertEquals("sandaly", query.value());
    }

    @Test
    void parsesSellerContainsIgnoreCase() {
        SearchQuery query = SearchQuery.parse("seller ~= Notch", configuration);

        assertEquals(SearchField.SELLER, query.field());
        assertEquals(SearchFilterType.CONTAINS_IGNORE_CASE, query.type());
        assertEquals("Notch", query.value());
    }

    @Test
    void keepsBlankQueryAsDefaultSearch() {
        SearchQuery query = SearchQuery.parse("", configuration);

        assertNull(query.field());
        assertNull(query.type());
        assertEquals("", query.value());
    }
}
