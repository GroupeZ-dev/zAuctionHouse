package fr.maxlego08.zauctionhouse.api.services.result;

/**
 * Represents the result of a remove operation.
 *
 * @param success    whether the remove operation was successful
 * @param message    a human-readable message describing the result
 * @param itemGiven  whether the item was given directly to the player's inventory
 * @param failReason the reason for failure (NONE if successful)
 */
public record RemoveResult(
        boolean success,
        String message,
        boolean itemGiven,
        RemoveFailReason failReason
) {

    /**
     * Creates a successful remove result where the item was given to the player.
     *
     * @param message the success message
     * @return a successful remove result with item given
     */
    public static RemoveResult success(String message) {
        return new RemoveResult(true, message, true, RemoveFailReason.NONE);
    }

    /**
     * Creates a successful remove result.
     *
     * @param message   the success message
     * @param itemGiven whether the item was given directly to the player
     * @return a successful remove result
     */
    public static RemoveResult success(String message, boolean itemGiven) {
        return new RemoveResult(true, message, itemGiven, RemoveFailReason.NONE);
    }

    /**
     * Creates a failed remove result.
     *
     * @param message    the error message
     * @param failReason the reason for failure
     * @return a failed remove result
     */
    public static RemoveResult failure(String message, RemoveFailReason failReason) {
        return new RemoveResult(false, message, false, failReason);
    }

    /**
     * Checks if the remove operation was successful.
     *
     * @return {@code true} if the item was successfully removed
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
     * Gets the reason for failure if the remove was unsuccessful.
     *
     * @return the failure reason, or {@link RemoveFailReason#NONE} if successful
     */
    public RemoveFailReason getFailReason() {
        return failReason;
    }
}
