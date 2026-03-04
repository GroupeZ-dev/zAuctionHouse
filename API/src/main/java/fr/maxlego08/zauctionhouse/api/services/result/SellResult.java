package fr.maxlego08.zauctionhouse.api.services.result;

import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;

import java.util.Optional;

/**
 * Represents the result of a sell operation.
 *
 * @param success     whether the sell operation was successful
 * @param message     a human-readable message describing the result
 * @param auctionItem the created auction item (null if failed)
 * @param failReason  the reason for failure (NONE if successful)
 */
public record SellResult(
        boolean success,
        String message,
        AuctionItem auctionItem,
        SellFailReason failReason
) {

    /**
     * Creates a successful sell result.
     *
     * @param message     the success message
     * @param auctionItem the created auction item
     * @return a successful sell result
     */
    public static SellResult success(String message, AuctionItem auctionItem) {
        return new SellResult(true, message, auctionItem, SellFailReason.NONE);
    }

    /**
     * Creates a failed sell result.
     *
     * @param message    the error message
     * @param failReason the reason for failure
     * @return a failed sell result
     */
    public static SellResult failure(String message, SellFailReason failReason) {
        return new SellResult(false, message, null, failReason);
    }

    /**
     * Checks if the sell operation was successful.
     *
     * @return {@code true} if the item was successfully listed
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
     * Gets the auction item that was created if the sell was successful.
     *
     * @return an optional containing the created auction item, or empty if the sell failed
     */
    public Optional<AuctionItem> getAuctionItem() {
        return Optional.ofNullable(auctionItem);
    }

    /**
     * Gets the reason for failure if the sell was unsuccessful.
     *
     * @return the failure reason, or {@link SellFailReason#NONE} if successful
     */
    public SellFailReason getFailReason() {
        return failReason;
    }
}
