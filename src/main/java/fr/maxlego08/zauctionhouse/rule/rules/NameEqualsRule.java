package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items whose display name exactly equals one of the specified values.
 * Supports case-insensitive matching. Uses pre-computed display name from context.
 */
public class NameEqualsRule implements Rule {

    private final List<String> names;
    private final boolean ignoreCase;

    public NameEqualsRule(List<String> names, boolean ignoreCase) {
        this.ignoreCase = ignoreCase;

        this.names = ignoreCase ? names.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList() : names;
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (!context.hasDisplayName()) return false;

        String displayName = context.getDisplayName();
        if (ignoreCase) {
            displayName = displayName.toLowerCase(Locale.ROOT);
        }

        return this.names.contains(displayName);
    }
}
