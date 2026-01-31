package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items whose display name contains one of the specified values.
 * Uses pre-computed display name from context.
 */
public class NameContainsRule implements Rule {

    private final List<String> needles;

    public NameContainsRule(List<String> needles) {
        this.needles = needles.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (!context.hasDisplayName()) return false;

        String name = context.getDisplayName().toLowerCase(Locale.ROOT);
        return this.needles.stream().anyMatch(name::contains);
    }
}
