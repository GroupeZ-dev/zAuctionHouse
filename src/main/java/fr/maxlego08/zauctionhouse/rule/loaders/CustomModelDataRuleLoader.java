package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.AndRule;
import fr.maxlego08.zauctionhouse.rule.rules.ModelDataRangeRule;
import fr.maxlego08.zauctionhouse.rule.rules.ModelDataRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Loads custom model data rules from configuration.
 * Supports both exact values and ranges.
 */
public class CustomModelDataRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "custom-model-data";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<Rule> rules = new ArrayList<>();

        // Check for ranges first
        List<Map<?, ?>> ranges = RuleConfigHelper.getMapList(configuration, "ranges");
        if (!ranges.isEmpty()) {
            for (Map<?, ?> range : ranges) {
                int min = RuleConfigHelper.getInt(range, "min", 0);
                int max = RuleConfigHelper.getInt(range, "max", Integer.MAX_VALUE);
                rules.add(new ModelDataRangeRule(min, max));
            }
        }

        // Check for exact values
        List<Integer> values = RuleConfigHelper.getIntegerList(configuration, "values");
        if (!values.isEmpty()) {
            rules.add(new ModelDataRule(new HashSet<>(values)));
        }

        if (rules.isEmpty()) {
            return null;
        }

        // If multiple rules, combine with AND logic
        if (rules.size() == 1) {
            return rules.get(0);
        }
        return new AndRule(rules);
    }
}
