package fr.maxlego08.zauctionhouse.api.tax;

import java.math.BigDecimal;

/**
 * Contains the result of a tax calculation.
 *
 * @param taxAmount      the calculated tax amount
 * @param taxPercentage  the effective tax percentage applied
 * @param originalPrice  the original price before tax
 * @param finalPrice     the final price (depends on context: what buyer pays or seller receives)
 * @param isBypassed     whether the tax was bypassed due to permission
 * @param isReduced      whether a tax reduction was applied
 * @param reductionPercentage the reduction percentage applied (0 if no reduction)
 */
public record TaxResult(
        BigDecimal taxAmount,
        double taxPercentage,
        BigDecimal originalPrice,
        BigDecimal finalPrice,
        boolean isBypassed,
        boolean isReduced,
        double reductionPercentage
) {

    /**
     * Creates a result for when tax is bypassed.
     *
     * @param price the original price
     * @return a TaxResult with no tax applied
     */
    public static TaxResult bypassed(BigDecimal price) {
        return new TaxResult(BigDecimal.ZERO, 0, price, price, true, false, 0);
    }

    /**
     * Creates a result for when tax is disabled.
     *
     * @param price the original price
     * @return a TaxResult with no tax applied
     */
    public static TaxResult disabled(BigDecimal price) {
        return new TaxResult(BigDecimal.ZERO, 0, price, price, false, false, 0);
    }

    /**
     * Checks if any tax was applied.
     *
     * @return true if tax amount is greater than zero
     */
    public boolean hasTax() {
        return taxAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
