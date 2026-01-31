package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.rule.rules.AndRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads AndRule from configuration.
 * Combines multiple rules with AND logic - all must match.
 */
public class AndRuleLoader implements RuleLoader {

    private final RuleLoaderRegistry registry;

    public AndRuleLoader(RuleLoaderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getType() {
        return "and";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<Map<?, ?>> ruleConfigs = RuleConfigHelper.getMapList(configuration, "rules");
        if (ruleConfigs.isEmpty()) {
            return null;
        }

        List<Rule> rules = new ArrayList<>();
        for (Map<?, ?> ruleConfig : ruleConfigs) {
            Rule rule = registry.loadRule(ruleConfig);
            if (rule != null) {
                rules.add(rule);
            }
        }

        if (rules.isEmpty()) {
            return null;
        }

        if (rules.size() == 1) {
            return rules.getFirst();
        }

        return new AndRule(rules);
    }
}
