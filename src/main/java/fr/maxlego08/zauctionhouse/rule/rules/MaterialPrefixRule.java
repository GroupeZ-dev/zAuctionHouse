package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items whose material name starts with one of the specified prefixes.
 * Useful for matching groups like "DIAMOND_", "IRON_", "GOLDEN_", etc.
 */
public class MaterialPrefixRule implements Rule {

    private final List<String> prefixes;

    public MaterialPrefixRule(List<String> prefixes) {
        this.prefixes = prefixes.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        String materialName = context.getMaterial().name();
        for (String prefix : this.prefixes) {
            if (materialName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isValid() {
        return !this.prefixes.isEmpty();
    }
}
