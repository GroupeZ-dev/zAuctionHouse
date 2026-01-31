package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.hooks.itemsadder.ItemsAdderRuleLoader;
import fr.maxlego08.zauctionhouse.hooks.nexo.NexoRuleLoader;
import fr.maxlego08.zauctionhouse.hooks.oraxen.OraxenRuleLoader;
import fr.maxlego08.zauctionhouse.rule.loaders.*;

import java.util.*;

/**
 * Implementation of {@link RuleLoaderRegistry}.
 * Manages registration and lookup of rule loaders.
 */
public class ZRuleLoaderRegistry implements RuleLoaderRegistry {

    private final Map<String, RuleLoader> loaders = new HashMap<>();

    public ZRuleLoaderRegistry() {
    }

    @Override
    public void registerDefaultLoaders() {
        register(new MaterialRuleLoader());
        register(new MaterialSuffixRuleLoader());
        register(new MaterialPrefixRuleLoader());
        register(new MaterialContainsRuleLoader());
        register(new TagRuleLoader());
        register(new NameRuleLoader());
        register(new LoreRuleLoader());
        register(new CustomModelDataRuleLoader());
        register(new AndRuleLoader(this));
    }

    /**
     * Registers the ItemsAdder rule loader.
     * Should only be called if ItemsAdder plugin is present.
     */
    public void registerItemsAdderLoader() {
        register(new ItemsAdderRuleLoader());
    }

    /**
     * Registers the Oraxen rule loader.
     * Should only be called if Oraxen plugin is present.
     */
    public void registerOraxenLoader() {
        register(new OraxenRuleLoader());
    }

    /**
     * Registers the Nexo rule loader.
     * Should only be called if Nexo plugin is present.
     */
    public void registerNexoLoader() {
        register(new NexoRuleLoader());
    }

    @Override
    public void register(RuleLoader loader) {
        String type = loader.getType().toLowerCase(Locale.ROOT);
        this.loaders.put(type, loader);

        // Register aliases
        for (String alias : loader.getAliases()) {
            this.loaders.put(alias.toLowerCase(Locale.ROOT), loader);
        }
    }

    @Override
    public void unregister(String type) {
        RuleLoader loader = this.loaders.remove(type.toLowerCase(Locale.ROOT));
        if (loader != null) {
            // Also remove aliases
            for (String alias : loader.getAliases()) {
                this.loaders.remove(alias.toLowerCase(Locale.ROOT));
            }
        }
    }

    @Override
    public Optional<RuleLoader> getLoader(String type) {
        return Optional.ofNullable(this.loaders.get(type.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<RuleLoader> getLoaders() {
        // Return unique loaders (aliases may point to same loader)
        return List.copyOf(new HashSet<>(this.loaders.values()));
    }

    @Override
    public Rule loadRule(Map<?, ?> configuration) {
        String type = RuleConfigHelper.getString(configuration, "type");
        if (type == null) {
            return null;
        }

        return getLoader(type).map(loader -> loader.load(configuration)).orElse(null);
    }

    @Override
    public List<Rule> loadRules(Map<?, ?> configuration) {
        Rule rule = loadRule(configuration);
        return rule != null ? List.of(rule) : List.of();
    }

    @Override
    public List<Rule> loadRulesFromList(List<Map<?, ?>> configurations) {
        List<Rule> rules = new ArrayList<>();

        for (Map<?, ?> config : configurations) {
            Rule rule = loadRule(config);
            if (rule != null) {
                rules.add(rule);
            }
        }

        return rules;
    }
}
