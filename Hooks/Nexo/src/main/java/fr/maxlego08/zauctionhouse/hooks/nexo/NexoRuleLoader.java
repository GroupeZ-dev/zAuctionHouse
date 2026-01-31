package fr.maxlego08.zauctionhouse.hooks.nexo;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads NexoRule from configuration.
 * Matches items created by Nexo plugin.
 */
public class NexoRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "nexo";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new NexoRule(items);
    }
}
