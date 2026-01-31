package fr.maxlego08.zauctionhouse.api.rules.loader;

import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Map;

/**
 * Interface for loading rules from configuration maps.
 * Implementations handle specific rule types (material, name, lore, etc.).
 * <p>
 * External plugins can register custom RuleLoaders to add new rule types
 * that can be used in both blacklist/whitelist rules and category matching.
 * <p>
 * Example configuration that a RuleLoader might process:
 * <pre>
 * - type: material
 *   materials:
 *     - DIAMOND_SWORD
 *     - IRON_SWORD
 * </pre>
 */
public interface RuleLoader {

    /**
     * Gets the type identifier for this loader.
     * This is matched against the "type" field in configuration.
     * <p>
     * Examples: "material", "name", "lore", "custom-model-data", "tag"
     *
     * @return the type identifier (lowercase)
     */
    String getType();

    /**
     * Gets alternative type names that this loader also handles.
     * Useful for backwards compatibility or shorthand names.
     * <p>
     * Example: "material-tag" loader might also accept "tag" as an alias.
     *
     * @return list of alternative type names, or empty list if none
     */
    default List<String> getAliases() {
        return List.of();
    }

    /**
     * Loads a rule from the given configuration map.
     * The map contains all fields from the rule configuration block.
     * <p>
     * Implementations should handle missing or invalid values gracefully,
     * returning null if the configuration is invalid or incomplete.
     *
     * @param configuration the configuration map from YAML
     * @return the loaded rule, or null if configuration is invalid
     */
    Rule load(Map<?, ?> configuration);
}
