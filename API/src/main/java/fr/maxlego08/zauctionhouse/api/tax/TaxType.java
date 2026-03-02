package fr.maxlego08.zauctionhouse.api.tax;

/**
 * Defines when taxes are applied in transactions.
 */
public enum TaxType {

    /**
     * Tax is paid by the seller when listing an item.
     */
    SELL,

    /**
     * Tax is deducted from the amount received by the seller when an item is purchased.
     */
    PURCHASE,

    /**
     * Tax is applied on both listing and purchase.
     */
    BOTH,

    /**
     * VAT-style tax: the buyer pays the price + tax, and the seller receives the full price.
     */
    CAPITALISM
}
