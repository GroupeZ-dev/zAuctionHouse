package fr.maxlego08.zauctionhouse.api.filter;

/**
 * Applies search filter operators to plain text values.
 */
public final class SearchTextMatcher {

    private SearchTextMatcher() {
    }

    public static boolean matches(String fieldValue, SearchFilterType type, String queryValue) {
        if (fieldValue == null || queryValue == null) {
            return false;
        }

        return switch (type) {
            case CONTAINS -> fieldValue.contains(queryValue);
            case EQUALS -> fieldValue.equals(queryValue);
            case CONTAINS_IGNORE_CASE -> SearchTextNormalizer.normalize(fieldValue).contains(SearchTextNormalizer.normalize(queryValue));
            case EQUALS_IGNORE_CASE -> SearchTextNormalizer.normalize(fieldValue).equals(SearchTextNormalizer.normalize(queryValue));
        };
    }
}
