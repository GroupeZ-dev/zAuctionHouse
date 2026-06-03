package fr.maxlego08.zauctionhouse.api.filter;

import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchQueryFormatterTest {

    private final SearchFilterConfiguration configuration = new SearchFilterConfiguration(Map.of(
            SearchFilterType.CONTAINS, "~",
            SearchFilterType.EQUALS, "=",
            SearchFilterType.CONTAINS_IGNORE_CASE, "~=",
            SearchFilterType.EQUALS_IGNORE_CASE, "=="
    ));

    @Test
    void formatsBlankQueryAsNone() {
        assertEquals("None", SearchQueryFormatter.format("", configuration));
    }

    @Test
    void formatsAllContainsQuery() {
        assertEquals("Everything contains: sandaly", SearchQueryFormatter.format("all ~= sandaly", configuration));
    }

    @Test
    void formatsFieldExactQuery() {
        assertEquals("Name exact: Sandaly", SearchQueryFormatter.format("name == Sandaly", configuration));
    }

    @Test
    void formatsDefaultQueryAsEverythingContains() {
        assertEquals("Everything contains: diamond", SearchQueryFormatter.format("diamond", configuration));
    }
}
