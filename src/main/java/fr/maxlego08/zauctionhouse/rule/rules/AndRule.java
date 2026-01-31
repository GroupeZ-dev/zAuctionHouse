package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;

public class AndRule implements Rule {

    private final List<Rule> children;

    public AndRule(List<Rule> children) {
        this.children = children;
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        for (Rule child : this.children) {
            if (!child.matches(context)) {
                return false;
            }
        }
        return !this.children.isEmpty();
    }
}
