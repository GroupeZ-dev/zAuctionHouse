package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.Set;

/**
 * Rule that matches items with specific custom model data values.
 * Uses pre-computed custom model data from context.
 */
public class ModelDataRule implements Rule {

    private final Set<Integer> modelData;

    public ModelDataRule(Set<Integer> modelData) {
        this.modelData = modelData;
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (!context.hasCustomModelData()) return false;

        return this.modelData.contains(context.getCustomModelData());
    }
}
