package fr.maxlego08.zauctionhouse.category;

import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link Category}.
 * Represents a category loaded from YAML configuration.
 */
public class ZCategory implements Category {

    private final String id;
    private final String displayName;
    private final List<Rule> rules;
    private final boolean miscellaneous;

    /**
     * Creates a new category.
     *
     * @param id            unique identifier
     * @param displayName   display name for menus
     * @param rules         matching rules
     * @param miscellaneous whether this is the fallback category
     */
    public ZCategory(String id, String displayName, List<Rule> rules, boolean miscellaneous) {
        this.id = Objects.requireNonNull(id, "Category id cannot be null");
        this.displayName = displayName != null ? displayName : id;
        this.rules = rules != null ? List.copyOf(rules) : List.of();
        this.miscellaneous = miscellaneous;
    }

    /**
     * Creates a miscellaneous/fallback category.
     *
     * @param id          unique identifier
     * @param displayName display name
     * @return new miscellaneous category
     */
    public static ZCategory miscellaneous(String id, String displayName) {
        return new ZCategory(id, displayName, List.of(), true);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public List<Rule> getRules() {
        return rules;
    }

    @Override
    public boolean isMiscellaneous() {
        return miscellaneous;
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (context == null) return false;

        // Miscellaneous category matches everything
        if (miscellaneous) return true;

        // Regular category: match if ANY rule matches (OR logic)
        for (Rule rule : rules) {
            if (rule.matches(context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZCategory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ZCategory{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", rulesCount=" + rules.size() +
                ", miscellaneous=" + miscellaneous +
                '}';
    }
}
