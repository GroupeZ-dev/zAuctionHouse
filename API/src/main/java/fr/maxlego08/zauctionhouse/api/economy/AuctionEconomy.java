package fr.maxlego08.zauctionhouse.api.economy;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

public interface AuctionEconomy {

    /**
     * Gets the name of the economy system.
     *
     * @return The name of the economy system.
     */
    String getName();

    /**
     * Gets the display name of the economy system.
     *
     * @return The display name of the economy system.
     */
    String getDisplayName();

    /**
     * Gets the symbol used for the currency of the economy system.
     *
     * @return The symbol used for the currency.
     */
    String getSymbol();

    /**
     * Gets the format used for displaying currency amounts.
     *
     * @return The format used for displaying currency amounts.
     */
    String getFormat();

    /**
     * Formats the specified price as a string according to the economy format and amount.
     *
     * @param priceAsString The price as a string.
     * @param amount        The amount.
     * @return The formatted price string.
     */
    default String format(String priceAsString, long amount) {
        return getFormat().replace("%price%", priceAsString).replace("%s%", amount > 1 ? "s" : "");
    }

    /**
     * Retrieves the amount of money associated with the specified player's economy account.
     *
     * @param offlinePlayer The player to retrieve the money for.
     * @return The amount of money associated with the player's economy account.
     */
    BigDecimal get(OfflinePlayer offlinePlayer);

    /**
     * Checks if the specified player has at least the specified amount of money.
     *
     * @param offlinePlayer The player to check.
     * @param price         The amount of money to check for.
     * @return True if the player has at least the specified amount of money, false otherwise.
     */
    boolean has(OfflinePlayer offlinePlayer, BigDecimal price);

    /**
     * Deposits the specified amount of money into the player's economy account.
     *
     * @param offlinePlayer The player to deposit money into.
     * @param value         The amount of money to deposit.
     * @param reason        The reason for the deposit.
     */
    void deposit(OfflinePlayer offlinePlayer, BigDecimal value, String reason);

    /**
     * Withdraws the specified amount of money from the player's economy account.
     *
     * @param offlinePlayer The player to withdraw money from.
     * @param value         The amount of money to withdraw.
     * @param reason        The reason for the withdrawal.
     */
    void withdraw(OfflinePlayer offlinePlayer, BigDecimal value, String reason);

    /**
     * Gets the reason for depositing money into an account.
     *
     * @return the deposit reason as a string.
     */
    String getDepositReason();

    /**
     * Gets the reason for withdrawing money from an account.
     *
     * @return the withdraw reason as a string.
     */
    String getWithdrawReason();

    /**
     * Gets the permission string associated with the economy.
     *
     * @return the permission string, or null if no permission is required.
     */
    @Nullable
    String getPermission();
}
