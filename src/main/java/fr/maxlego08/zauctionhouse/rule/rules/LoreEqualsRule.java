package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items that have a lore line exactly equal to one of the specified values.
 * Comparison is case-insensitive. Uses pre-computed lore from context.
 */
public class LoreEqualsRule implements Rule {

    private final List<String> loreLines;

    public LoreEqualsRule(List<String> loreLines) {
        this.loreLines = loreLines.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (!context.hasLore()) return false;

        for (String line : context.getLore()) {
            String cleanLine = line.toLowerCase(Locale.ROOT);
            if (this.loreLines.contains(cleanLine)) {
                return true;
            }
        }
        return false;
    }
}
