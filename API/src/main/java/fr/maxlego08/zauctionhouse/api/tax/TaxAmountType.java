package fr.maxlego08.zauctionhouse.api.tax;

/**
 * Defines how the tax amount is calculated.
 */
public enum TaxAmountType {

    /**
     * Tax is calculated as a percentage of the price.
     */
    PERCENTAGE,

    /**
     * Tax is a fixed amount regardless of the price.
     */
    FIXED
}
