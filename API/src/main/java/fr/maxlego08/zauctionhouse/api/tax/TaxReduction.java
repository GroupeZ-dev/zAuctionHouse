package fr.maxlego08.zauctionhouse.api.tax;

/**
 * Represents a tax reduction based on a permission.
 *
 * @param permission the permission required to get this reduction
 * @param percentage the percentage reduction (0-100)
 */
public record TaxReduction(String permission, double percentage) {

    /**
     * Calculates the reduction multiplier.
     * For example, a 25% reduction returns 0.75.
     *
     * @return the multiplier to apply to the tax amount
     */
    public double getMultiplier() {
        return 1.0 - (percentage / 100.0);
    }
}
