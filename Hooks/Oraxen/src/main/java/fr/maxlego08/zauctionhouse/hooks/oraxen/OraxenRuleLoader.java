package fr.maxlego08.zauctionhouse.hooks.oraxen;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads OraxenRule from configuration.
 * Matches items created by Oraxen plugin.
 */
public class OraxenRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "oraxen";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new OraxenRule(items);
    }
}
