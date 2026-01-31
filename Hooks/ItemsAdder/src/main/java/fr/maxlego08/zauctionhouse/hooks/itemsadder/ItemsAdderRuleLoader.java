package fr.maxlego08.zauctionhouse.hooks.itemsadder;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;

import java.util.List;
import java.util.Map;

/**
 * Loads ItemsAdderRule from configuration.
 * Matches items created by ItemsAdder plugin.
 */
public class ItemsAdderRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "itemsadder";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> items = RuleConfigHelper.getStringList(configuration, "items");
        if (items.isEmpty()) {
            return null;
        }
        return new ItemsAdderRule(items);
    }
}
