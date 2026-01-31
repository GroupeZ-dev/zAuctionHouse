package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items whose material name contains one of the specified patterns.
 * Useful for matching groups like "BANNER", "WOOL", "CANDLE", etc.
 */
public class MaterialContainsRule implements Rule {

    private final List<String> patterns;

    public MaterialContainsRule(List<String> patterns) {
        this.patterns = patterns.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        String materialName = context.getMaterial().name();
        for (String pattern : this.patterns) {
            if (materialName.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
