package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialContainsRule;

import java.util.List;
import java.util.Map;

/**
 * Loads MaterialContainsRule from configuration.
 * Matches items whose material name contains specified patterns.
 */
public class MaterialContainsRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "material-contains";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> patterns = RuleConfigHelper.getStringList(configuration, "patterns");
        if (patterns.isEmpty()) {
            return null;
        }
        return new MaterialContainsRule(patterns);
    }
}
