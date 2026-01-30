package fr.maxlego08.zauctionhouse.api.category;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Manages auction house categories, including loading from configuration,
 * matching items to categories, and providing category lookups.
 * <p>
 * Categories are loaded from YAML files in the categories/ directory.
 * Each file can contain one or more category definitions.
 */
public interface CategoryManager {

    /**
     * Loads or reloads all categories from configuration files.
     * This clears existing categories and loads fresh from disk.
     * Categories are loaded from both categories.yml and categories/*.yml files.
     */
    void loadCategories();

    /**
     * Gets all registered categories.
     *
     * @return immutable list of all categories, sorted by priority
     */
    List<Category> getCategories();

    /**
     * Gets a category by its unique identifier.
     *
     * @param id the category identifier
     * @return optional containing the category if found
     */
    Optional<Category> getCategory(String id);

    /**
     * Gets the primary category for an item.
     * Returns the first matching category based on priority order.
     * If no category matches, returns the miscellaneous category.
     *
     * @param itemStack the item to categorize
     * @return the primary category for the item
     */
    Category getCategoryFor(ItemStack itemStack);

    /**
     * Gets all categories that match an item.
     * Returns categories in priority order.
     *
     * @param itemStack the item to categorize
     * @return list of matching categories (never empty - includes misc as fallback)
     */
    List<Category> getCategoriesFor(ItemStack itemStack);

    /**
     * Checks if an item matches a specific category.
     *
     * @param itemStack the item to test
     * @param category  the category to test against
     * @return true if the item matches the category
     */
    boolean matches(ItemStack itemStack, Category category);

    /**
     * Gets the miscellaneous/fallback category.
     * This category matches all items that don't match any other category.
     *
     * @return the miscellaneous category
     */
    Category getMiscCategory();

    /**
     * Checks if the category system is enabled.
     *
     * @return true if categories are enabled
     */
    boolean isEnabled();

    /**
     * Gets the number of registered categories.
     *
     * @return category count
     */
    int getCategoryCount();
}
