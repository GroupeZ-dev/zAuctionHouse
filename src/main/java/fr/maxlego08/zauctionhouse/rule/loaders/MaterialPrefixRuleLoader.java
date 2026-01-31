package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialPrefixRule;

import java.util.List;
import java.util.Map;

/**
 * Loads MaterialPrefixRule from configuration.
 * Matches items whose material name starts with specified prefixes.
 */
public class MaterialPrefixRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "material-prefix";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> prefixes = RuleConfigHelper.getStringList(configuration, "prefixes");
        if (prefixes.isEmpty()) {
            return null;
        }
        return new MaterialPrefixRule(prefixes);
    }
}
