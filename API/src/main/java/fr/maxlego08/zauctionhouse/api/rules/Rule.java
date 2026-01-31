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

    /**
     * Checks whether this rule has a valid configuration.
     * <p>
     * A rule is considered invalid if its configuration would make it
     * impossible to match any item. For example:
     * <ul>
     *     <li>A TagRule with no valid tags</li>
     *     <li>A MaterialRule with an empty material set</li>
     *     <li>A NameContainsRule with no search patterns</li>
     * </ul>
     * <p>
     * Invalid rules should be filtered out during loading to avoid
     * unnecessary processing and to warn administrators of configuration errors.
     *
     * @return {@code true} if the rule configuration is valid, {@code false} otherwise
     */
    default boolean isValid() {
        return true;
    }
}
