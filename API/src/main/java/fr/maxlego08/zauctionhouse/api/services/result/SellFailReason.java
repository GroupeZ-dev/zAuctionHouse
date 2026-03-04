package fr.maxlego08.zauctionhouse.api.services.result;

/**
 * Reasons why a sell operation may fail.
 */
public enum SellFailReason {

    /**
     * No failure - the operation was successful.
     */
    NONE,

    /**
     * The player tried to sell air or an empty item.
     */
    INVALID_ITEM,

    /**
     * The item is on the blacklist and cannot be sold.
     */
    BLACKLISTED,

    /**
     * The item is not on the whitelist (when whitelist is enabled).
     */
    NOT_WHITELISTED,

    /**
     * The price exceeds the maximum allowed price.
     */
    PRICE_TOO_HIGH,

    /**
     * The price is below the minimum allowed price.
     */
    PRICE_TOO_LOW,

    /**
     * The player has reached their maximum listing limit.
     */
    LISTING_LIMIT_REACHED,

    /**
     * The player cannot sell items in the current world.
     */
    WORLD_RESTRICTED,

    /**
     * The specified economy provider was not found.
     */
    ECONOMY_NOT_FOUND,

    /**
     * The player does not have enough money to pay the listing tax.
     */
    INSUFFICIENT_FUNDS_FOR_TAX,

    /**
     * The item in the player's hand changed during the sell process.
     */
    ITEM_CHANGED,

    /**
     * The items in the player's inventory changed during the async sell process.
     */
    ITEMS_CHANGED,

    /**
     * The player does not have enough money for the operation.
     */
    INSUFFICIENT_FUNDS,

    /**
     * A database error occurred during the sell process.
     */
    DATABASE_ERROR,

    /**
     * An error occurred while calculating or applying the tax.
     */
    TAX_ERROR,

    /**
     * The sell event was cancelled by another plugin.
     */
    EVENT_CANCELLED,

    /**
     * An internal error occurred during the sell process.
     */
    INTERNAL_ERROR

}
