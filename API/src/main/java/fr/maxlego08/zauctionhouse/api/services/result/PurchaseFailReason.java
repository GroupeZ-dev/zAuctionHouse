package fr.maxlego08.zauctionhouse.api.services.result;

/**
 * Reasons why a purchase operation may fail.
 */
public enum PurchaseFailReason {

    /**
     * No failure - the operation was successful.
     */
    NONE,

    /**
     * The item has expired.
     */
    ITEM_EXPIRED,

    /**
     * The item is not in the purchase confirmation state.
     */
    ITEM_NOT_IN_PURCHASE_STATE,

    /**
     * The item is no longer available for purchase.
     */
    ITEM_NOT_AVAILABLE,

    /**
     * The item was already purchased by another player.
     */
    ALREADY_PURCHASED,

    /**
     * The player does not have enough money to buy the item.
     */
    INSUFFICIENT_FUNDS,

    /**
     * The player does not have enough inventory space.
     */
    INSUFFICIENT_SPACE,

    /**
     * The player tried to buy their own item.
     */
    CANNOT_BUY_OWN_ITEM,

    /**
     * The player cannot purchase items in the current world.
     */
    WORLD_RESTRICTED,

    /**
     * The purchase event was cancelled by another plugin.
     */
    EVENT_CANCELLED,

    /**
     * Failed to acquire a lock on the item (cluster mode).
     */
    LOCK_FAILED,

    /**
     * An internal error occurred during the purchase process.
     */
    INTERNAL_ERROR

}
