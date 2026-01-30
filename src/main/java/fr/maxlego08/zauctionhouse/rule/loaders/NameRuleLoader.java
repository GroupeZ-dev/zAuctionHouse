package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.NameContainsRule;
import fr.maxlego08.zauctionhouse.rule.rules.NameEqualsRule;

import java.util.List;
import java.util.Map;

/**
 * Loads name-based rules from configuration.
 * Supports both CONTAINS and EQUALS modes for display name matching.
 */
public class NameRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "name";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        String mode = RuleConfigHelper.getString(configuration, "mode");
        List<String> values = RuleConfigHelper.getStringList(configuration, "values");

        if (values.isEmpty()) {
            return null;
        }

        if ("EQUALS".equalsIgnoreCase(mode)) {
            boolean ignoreCase = RuleConfigHelper.getBoolean(configuration, "ignore-case", true);
            return new NameEqualsRule(values, ignoreCase);
        } else {
            return new NameContainsRule(values);
        }
    }
}
