package fr.maxlego08.zauctionhouse.api.category;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;

/**
 * Represents an auction house category that groups items based on matching rules.
 * Categories are defined in YAML configuration files and loaded at runtime.
 * <p>
 * Each category has a unique identifier, a display name, and a set of rules
 * that determine which items belong to it. Items are matched against rules using
 * OR logic by default - an item matches a category if it satisfies at least one rule.
 */
public interface Category {

    /**
     * Gets the unique identifier for this category.
     * This ID is used in configuration files and commands to reference the category.
     *
     * @return the category identifier (e.g., "weapons", "blocks", "custom-items")
     */
    String getId();

    /**
     * Gets the display name shown to players in menus.
     * Supports color codes and placeholders.
     *
     * @return the formatted display name
     */
    String getDisplayName();

    /**
     * Gets all matching rules for this category.
     * An item matches this category if at least one rule matches (OR logic).
     *
     * @return immutable list of rules
     */
    List<Rule> getRules();

    /**
     * Checks whether this category is the miscellaneous/fallback category.
     * The misc category matches all items that don't match any other category.
     *
     * @return true if this is the misc category
     */
    boolean isMiscellaneous();

    /**
     * Tests whether an item matches this category.
     * For regular categories, checks if any rule matches.
     * For the misc category, always returns true.
     *
     * @param context the item context containing pre-computed values
     * @return true if the item belongs to this category
     */
    boolean matches(ItemRuleContext context);
}
