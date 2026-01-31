package fr.maxlego08.zauctionhouse.api.rules;

import java.util.ArrayList;
import java.util.List;

public record Rules(boolean enabled, List<Rule> rules) implements Rule {

    public Rules(boolean enabled, List<Rule> rules) {
        this.enabled = enabled;
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (!enabled) return false;

        for (Rule rule : rules) {
            if (rule.matches(context)) {
                return true;
            }
        }
        return false;
    }

    public Rules withEnabled(boolean enabled) {
        return new Rules(enabled, this.rules);
    }

    public Rules withAddedRule(Rule rule) {
        List<Rule> copy = new ArrayList<>(this.rules);
        copy.add(rule);
        return new Rules(this.enabled, copy);
    }

    public static Rules emptyDisabled() {
        return new Rules(false, List.of());
    }
}

