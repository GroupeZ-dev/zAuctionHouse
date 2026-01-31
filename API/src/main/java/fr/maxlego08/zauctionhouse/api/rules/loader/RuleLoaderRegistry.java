package fr.maxlego08.zauctionhouse.api.rules.loader;

import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for rule loaders.
 * Manages registration and lookup of {@link RuleLoader} implementations.
 * <p>
 * External plugins can register custom rule loaders to extend the rule system
 * with new matching types. Registered loaders are used by both the blacklist/whitelist
 * system and the category matching system.
 * <p>
 * Example usage from an external plugin:
 * <pre>
 * RuleLoaderRegistry registry = auctionPlugin.getRuleLoaderRegistry();
 * registry.register(new MyCustomRuleLoader());
 * </pre>
 */
public interface RuleLoaderRegistry {


    /**
     * Registers the default set of rule loaders.
     * This method is typically called by plugins during their onEnable() method.
     * It registers the default set of rule loaders that are used by the auction house.
     * Plugins can register additional rule loaders or override the default ones by calling
     * {@link #register(RuleLoader)} or {@link #unregister(String)}.
     */
    void registerDefaultLoaders();

    /**
     * Registers a rule loader.
     * If a loader with the same type already exists, it will be replaced.
     *
     * @param loader the loader to register
     */
    void register(RuleLoader loader);

    /**
     * Unregisters a rule loader by its type.
     *
     * @param type the type identifier to unregister
     */
    void unregister(String type);

    /**
     * Gets a loader for the specified type.
     *
     * @param type the type identifier
     * @return optional containing the loader if found
     */
    Optional<RuleLoader> getLoader(String type);

    /**
     * Gets all registered loaders.
     *
     * @return unmodifiable list of all loaders
     */
    List<RuleLoader> getLoaders();

    /**
     * Loads a single rule from a configuration map.
     * Determines the appropriate loader based on the "type" field.
     *
     * @param configuration the configuration map (must contain a "type" field)
     * @return the loaded rule, or null if no matching loader or invalid config
     */
    Rule loadRule(Map<?, ?> configuration);

    /**
     * Loads a rule from a configuration map and returns it as a list.
     *
     * @param configuration the configuration map (must contain a "type" field)
     * @return list containing the loaded rule, or empty list if invalid
     */
    List<Rule> loadRules(Map<?, ?> configuration);

    /**
     * Loads rules from a list of configuration maps.
     * Each map in the list is processed as a separate rule block.
     *
     * @param configurations list of configuration maps
     * @return list of loaded rules
     */
    List<Rule> loadRulesFromList(List<Map<?, ?>> configurations);
}
