package fr.maxlego08.zauctionhouse.api.filter;

import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;

import java.util.Locale;

public final class SearchQueryFormatter {

    private SearchQueryFormatter() {
    }

    public static String format(String raw, SearchFilterConfiguration configuration) {
        SearchQuery query = SearchQuery.parse(raw, configuration);
        if (query.value().isBlank()) {
            return "None";
        }

        SearchField field = query.field() == null ? SearchField.ALL : query.field();
        SearchFilterType type = query.type() == null ? SearchFilterType.CONTAINS_IGNORE_CASE : query.type();
        return fieldLabel(field) + " " + modeLabel(type) + ": " + query.value();
    }

    private static String fieldLabel(SearchField field) {
        if (field == SearchField.ALL) {
            return "Everything";
        }
        String key = field.getKey().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private static String modeLabel(SearchFilterType type) {
        return switch (type) {
            case EQUALS, EQUALS_IGNORE_CASE -> "exact";
            case CONTAINS, CONTAINS_IGNORE_CASE -> "contains";
        };
    }
}
