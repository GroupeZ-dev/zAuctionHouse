package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

/**
 * Rule that matches items whose custom model data falls within a specified range.
 * Uses pre-computed custom model data from context.
 */
public class ModelDataRangeRule implements Rule {

    private final int min;
    private final int max;

    public ModelDataRangeRule(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        if (!context.hasCustomModelData()) return false;

        int modelData = context.getCustomModelData();
        return modelData >= this.min && modelData <= this.max;
    }

    @Override
    public boolean isValid() {
        return this.min <= this.max;
    }
}
