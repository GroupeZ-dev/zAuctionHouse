package fr.maxlego08.zauctionhouse.api.filter;

/**
 * Defines the item fields that can be targeted by a search query.
 */
public enum SearchField {

    ALL("all"),
    NAME("name"),
    MATERIAL("material"),
    LORE("lore"),
    SELLER("seller");

    private final String key;

    SearchField(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /**
     * Finds the search field for a given key string (case-insensitive).
     *
     * @param key the key to look up
     * @return the matching search field, or null if not found
     */
    public static SearchField fromKey(String key) {
        if (key == null) return null;
        for (SearchField field : values()) {
            if (field.key.equalsIgnoreCase(key)) {
                return field;
            }
        }
        return null;
    }
}
