package fr.maxlego08.zauctionhouse.api.services.result;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;

/**
 * Represents the result of a claim operation.
 *
 * @param success       whether the claim operation was successful
 * @param message       a human-readable message describing the result
 * @param amountClaimed the amount of money that was claimed
 * @param economy       the economy used for the claim
 */
public record ClaimResult(
        boolean success,
        String message,
        double amountClaimed,
        AuctionEconomy economy
) {

    /**
     * Creates a successful claim result.
     *
     * @param message       the success message
     * @param amountClaimed the amount of money claimed
     * @param economy       the economy used
     * @return a successful claim result
     */
    public static ClaimResult success(String message, double amountClaimed, AuctionEconomy economy) {
        return new ClaimResult(true, message, amountClaimed, economy);
    }

    /**
     * Creates a failed claim result.
     *
     * @param message the error message
     * @return a failed claim result
     */
    public static ClaimResult failure(String message) {
        return new ClaimResult(false, message, 0, null);
    }

    /**
     * Creates a result indicating there was nothing to claim.
     *
     * @param message the message
     * @return a claim result with zero amount
     */
    public static ClaimResult nothingToClaim(String message) {
        return new ClaimResult(true, message, 0, null);
    }

    /**
     * Checks if the claim operation was successful.
     *
     * @return {@code true} if the money was successfully claimed
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
     * Gets the amount of money that was claimed.
     *
     * @return the amount claimed, or 0 if the claim failed
     */
    public double getAmountClaimed() {
        return amountClaimed;
    }

    /**
     * Gets the economy that was used for the claim.
     *
     * @return the economy, or {@code null} if the claim failed or no specific economy was used
     */
    public AuctionEconomy getEconomy() {
        return economy;
    }
}
