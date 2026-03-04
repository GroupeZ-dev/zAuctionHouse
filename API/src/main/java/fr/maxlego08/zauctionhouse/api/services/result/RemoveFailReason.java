package fr.maxlego08.zauctionhouse.api.services.result;

/**
 * Reasons why a remove operation may fail.
 */
public enum RemoveFailReason {

    /**
     * No failure - the operation was successful.
     */
    NONE,

    /**
     * The item has expired.
     */
    ITEM_EXPIRED,

    /**
     * The item was not found in the storage.
     */
    ITEM_NOT_FOUND,

    /**
     * The item is no longer available for removal.
     */
    ITEM_NOT_AVAILABLE,

    /**
     * The item is not in the correct status for this operation.
     */
    INVALID_ITEM_STATUS,

    /**
     * Failed to acquire a lock on the item (cluster mode).
     */
    LOCK_FAILED,

    /**
     * The player does not have permission to remove this item.
     */
    NO_PERMISSION,

    /**
     * The player does not have enough inventory space.
     */
    INSUFFICIENT_SPACE,

    /**
     * The player cannot remove items in the current world.
     */
    WORLD_RESTRICTED,

    /**
     * The remove event was cancelled by another plugin.
     */
    EVENT_CANCELLED,

    /**
     * An internal error occurred during the remove process.
     */
    INTERNAL_ERROR

}
