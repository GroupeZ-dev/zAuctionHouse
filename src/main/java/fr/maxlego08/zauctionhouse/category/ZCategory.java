package fr.maxlego08.zauctionhouse.category;

import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.category.CategoryIcon;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link Category}.
 * Represents a category loaded from YAML configuration.
 */
public class ZCategory implements Category {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final int priority;
    private final List<Rule> rules;
    private final boolean miscellaneous;
    private final CategoryIcon icon;

    /**
     * Creates a new category.
     *
     * @param id            unique identifier
     * @param displayName   display name for menus
     * @param description   description lines
     * @param priority      sorting priority (lower = higher priority)
     * @param rules         matching rules
     * @param miscellaneous whether this is the fallback category
     * @param icon          icon configuration
     */
    public ZCategory(String id, String displayName, List<String> description, int priority,
                     List<Rule> rules, boolean miscellaneous, CategoryIcon icon) {
        this.id = Objects.requireNonNull(id, "Category id cannot be null");
        this.displayName = displayName != null ? displayName : id;
        this.description = description != null ? List.copyOf(description) : List.of();
        this.priority = priority;
        this.rules = rules != null ? List.copyOf(rules) : List.of();
        this.miscellaneous = miscellaneous;
        this.icon = icon != null ? icon : CategoryIcon.defaultIcon();
    }

    /**
     * Creates a miscellaneous/fallback category.
     *
     * @param id          unique identifier
     * @param displayName display name
     * @param icon        icon configuration
     * @return new miscellaneous category
     */
    public static ZCategory miscellaneous(String id, String displayName, CategoryIcon icon) {
        return new ZCategory(id, displayName, List.of(), Integer.MAX_VALUE, List.of(), true, icon);
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
    public List<String> getDescription() {
        return description;
    }

    @Override
    public int getPriority() {
        return priority;
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
    public boolean matches(ItemStack itemStack) {
        if (itemStack == null) return false;

        // Miscellaneous category matches everything
        if (miscellaneous) return true;

        // Regular category: match if ANY rule matches (OR logic)
        for (Rule rule : rules) {
            if (rule.matches(itemStack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CategoryIcon getIcon() {
        return icon;
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
                ", priority=" + priority +
                ", rulesCount=" + rules.size() +
                ", miscellaneous=" + miscellaneous +
                '}';
    }
}
