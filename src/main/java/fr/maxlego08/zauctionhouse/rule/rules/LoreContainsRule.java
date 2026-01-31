package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Rule that matches items whose lore contains one of the specified values.
 * Uses pre-computed lore from context.
 */
public class LoreContainsRule implements Rule {

    private final List<String> needles;

    public LoreContainsRule(List<String> needles) {
        this.needles = needles.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (!context.hasLore()) return false;

        String joined = context.getLore().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
        return this.needles.stream().anyMatch(joined::contains);
    }

    @Override
    public boolean isValid() {
        return !this.needles.isEmpty();
    }
}
