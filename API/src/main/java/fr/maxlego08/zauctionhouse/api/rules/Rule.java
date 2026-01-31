package fr.maxlego08.zauctionhouse.api.rules;

public interface Rule {

    /**
     * Checks whether the given item context matches this rule.
     * Uses pre-computed values from the context for efficient matching.
     *
     * @param context the item context containing pre-computed values
     * @return true if the item matches this rule, false otherwise
     */
    boolean matches(ItemRuleContext context);
}
