package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialSuffixRule;

import java.util.List;
import java.util.Map;

/**
 * Loads MaterialSuffixRule from configuration.
 * Matches items whose material name ends with specified suffixes.
 */
public class MaterialSuffixRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "material-suffix";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> suffixes = RuleConfigHelper.getStringList(configuration, "suffixes");
        if (suffixes.isEmpty()) {
            return null;
        }
        return new MaterialSuffixRule(suffixes);
    }
}
