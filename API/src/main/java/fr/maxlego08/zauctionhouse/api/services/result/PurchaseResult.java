package fr.maxlego08.zauctionhouse.api.services.result;

/**
 * Represents the result of a purchase operation.
 *
 * @param success    whether the purchase operation was successful
 * @param message    a human-readable message describing the result
 * @param itemGiven  whether the item was given directly to the player's inventory
 * @param failReason the reason for failure (NONE if successful)
 */
public record PurchaseResult(
        boolean success,
        String message,
        boolean itemGiven,
        PurchaseFailReason failReason
) {

    /**
     * Creates a successful purchase result where the item was given to the player.
     *
     * @param message the success message
     * @return a successful purchase result with item given
     */
    public static PurchaseResult success(String message) {
        return new PurchaseResult(true, message, true, PurchaseFailReason.NONE);
    }

    /**
     * Creates a successful purchase result.
     *
     * @param message   the success message
     * @param itemGiven whether the item was given directly to the player
     * @return a successful purchase result
     */
    public static PurchaseResult success(String message, boolean itemGiven) {
        return new PurchaseResult(true, message, itemGiven, PurchaseFailReason.NONE);
    }

    /**
     * Creates a failed purchase result.
     *
     * @param message    the error message
     * @param failReason the reason for failure
     * @return a failed purchase result
     */
    public static PurchaseResult failure(String message, PurchaseFailReason failReason) {
        return new PurchaseResult(false, message, false, failReason);
    }

    /**
     * Checks if the purchase operation was successful.
     *
     * @return {@code true} if the item was successfully purchased
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets a human-readable message describing the result.
     *
     * @return the result message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Checks if the item was given directly to the player's inventory.
     *
     * @return {@code true} if the item was added to the player's inventory
     */
    public boolean isItemGiven() {
        return itemGiven;
    }

    /**
     * Gets the reason for failure if the purchase was unsuccessful.
     *
     * @return the failure reason, or {@link PurchaseFailReason#NONE} if successful
     */
    public PurchaseFailReason getFailReason() {
        return failReason;
    }
}
