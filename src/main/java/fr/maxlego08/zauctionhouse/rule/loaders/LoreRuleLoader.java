package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.LoreContainsRule;
import fr.maxlego08.zauctionhouse.rule.rules.LoreEqualsRule;

import java.util.List;
import java.util.Map;

/**
 * Loads lore-based rules from configuration.
 * Supports both CONTAINS and EQUALS modes for lore matching.
 */
public class LoreRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "lore";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        String mode = RuleConfigHelper.getString(configuration, "mode");
        List<String> values = RuleConfigHelper.getStringList(configuration, "values");

        if (values.isEmpty()) {
            return null;
        }

        if ("EQUALS".equalsIgnoreCase(mode)) {
            return new LoreEqualsRule(values);
        } else {
            return new LoreContainsRule(values);
        }
    }
}
