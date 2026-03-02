package fr.maxlego08.zauctionhouse.api.tax;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a tax rule that applies to specific items.
 */
public interface ItemTaxRule {

    /**
     * Gets the unique name of this rule.
     *
     * @return the rule name
     */
    String getName();

    /**
     * Gets the priority of this rule (higher = checked first).
     *
     * @return the priority value
     */
    int getPriority();

    /**
     * Gets the tax type for items matching this rule.
     *
     * @return the tax type
     */
    TaxType getTaxType();

    /**
     * Gets how the tax amount is calculated for matching items.
     *
     * @return the tax amount type
     */
    TaxAmountType getAmountType();

    /**
     * Gets the tax amount for matching items.
     *
     * @return the tax amount
     */
    double getAmount();

    /**
     * Gets the rule used to match items.
     *
     * @return the item matching rule
     */
    Rule getRule();

    /**
     * Checks if the given item matches this rule.
     *
     * @param itemStack the item to check
     * @return true if the item matches
     */
    boolean matches(ItemStack itemStack);
}
