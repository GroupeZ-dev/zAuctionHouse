package fr.maxlego08.zauctionhouse.api.economy;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.tax.TaxConfiguration;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
     * Retrieves the current balance of the specified player asynchronously.
     *
     * @param playerId The player to retrieve the balance for.
     * @return A CompletableFuture containing the player's current balance.
     */
    CompletableFuture<BigDecimal> get(UUID playerId);

    /**
     * Retrieves a boolean indicating whether the player has the specified amount of money asynchronously.
     *
     * @param playerId The player to check the balance for.
     * @param price    The amount of money to check for.
     * @return A CompletableFuture containing a boolean indicating whether the player has the specified amount of money.
     */
    CompletableFuture<Boolean> has(UUID playerId, BigDecimal price);

    /**
     * Synchronously checks if the player has at least the specified amount of money.
     * This method should only be used when the player is online and on the main thread.
     * <p>
     * Override this method in implementations that have native synchronous balance checks
     * for better performance.
     *
     * @param player The online player to check the balance for.
     * @param price  The amount of money to check for.
     * @return true if the player has at least the specified amount, false otherwise.
     */
    default boolean hasSync(Player player, BigDecimal price) {
        return has(player.getUniqueId(), price).join();
    }

    /**
     * Deposits the specified amount of money into the player's economy account.
     *
     * @param playerId The player to deposit money into.
     * @param value    The amount of money to deposit.
     * @param reason   The reason for the deposit.
     */
    void deposit(UUID playerId, BigDecimal value, String reason);

    /**
     * Withdraws the specified amount of money from the player's economy account.
     *
     * @param playerId The player to withdraw money from.
     * @param value    The amount of money to withdraw.
     * @param reason   The reason for the withdrawal.
     */
    void withdraw(UUID playerId, BigDecimal value, String reason);

    /**
     * Retire le montant du compte du joueur et indique si le retrait a REELLEMENT eu lieu.
     * <p>
     * L'implementation par defaut delegue a {@link #withdraw(UUID, BigDecimal, String)} et
     * repond {@code true} : elle preserve a l'identique le comportement des implementations
     * ecrites avant l'introduction de cette methode. Les implementations qui savent controler
     * leur provider (voir {@code ZAuctionEconomy}) doivent la surcharger.
     * <p>
     * Un {@code false} signifie qu'AUCUN argent n'a bouge : l'appelant doit abandonner
     * l'operation avant tout mouvement d'item.
     *
     * @param playerId the player to withdraw money from
     * @param value    the amount of money to withdraw
     * @param reason   the reason for the withdrawal
     * @return {@code true} si le retrait a eu lieu, {@code false} s'il a ete refuse
     */
    default boolean withdrawChecked(UUID playerId, BigDecimal value, String reason) {
        withdraw(playerId, value, reason);
        return true;
    }

    /**
     * Depose le montant sur le compte du joueur et indique si le depot a REELLEMENT eu lieu.
     * <p>
     * Meme contrat de compatibilite que {@link #withdrawChecked(UUID, BigDecimal, String)}.
     * Un {@code false} signifie que l'argent n'a pas ete credite et que l'appelant doit le
     * conserver sous forme de dette (transaction PENDING) ou le journaliser en SEVERE.
     *
     * @param playerId the player to deposit money into
     * @param value    the amount of money to deposit
     * @param reason   the reason for the deposit
     * @return {@code true} si le depot a eu lieu, {@code false} sinon
     */
    default boolean depositChecked(UUID playerId, BigDecimal value, String reason) {
        deposit(playerId, value, reason);
        return true;
    }

    /**
     * Indique si cette economie sait crediter un joueur HORS LIGNE.
     * <p>
     * Les economies adossees a l'entite joueur (niveaux, experience, items) sont des no-op
     * silencieux quand le joueur n'est pas connecte : l'argent du vendeur disparait. Le plugin
     * s'appuie sur ce predicat pour transformer le paiement en transaction PENDING plutot que
     * de le detruire.
     *
     * @return {@code true} si un depot hors ligne est possible (valeur par defaut)
     */
    default boolean supportsOfflineDeposit() {
        return true;
    }

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

    /**
     * Retrieves the price format associated with the economy.
     *
     * @return the price format associated with the economy.
     */
    PriceFormat getPriceFormat();

    /**
     * Indicates whether rewards for this economy should be delivered automatically or held until claimed.
     *
     * @return {@code true} if the money is delivered automatically, {@code false} if it must be claimed manually.
     */
    boolean isAutoClaim();

    /**
     * Indicates whether players must be online to claim rewards for this economy.
     *
     * @return {@code true} if players must be online to claim rewards, {@code false} otherwise.
     */
    boolean mustBeOnline();

    /**
     * Retrieves the maximum price allowed for the specified auction item type.
     *
     * @param itemType the auction item type to retrieve the maximum price for
     * @return the maximum price allowed for the specified auction item type
     */
    BigDecimal getMaxPrice(ItemType itemType);

    /**
     * Retrieves the minimum price allowed for the specified auction item type.
     *
     * @param itemType the auction item type to retrieve the minimum price for
     * @return the minimum price allowed for the specified auction item type
     */
    BigDecimal getMinPrice(ItemType itemType);

    /**
     * Gets the tax configuration for this economy.
     *
     * @return the tax configuration
     */
    TaxConfiguration getTaxConfiguration();

    /**
     * Calculates the tax for a sell operation.
     *
     * @param player    the player selling the item
     * @param price     the sale price
     * @param itemStack the item being sold (for item-specific rules)
     * @return the tax calculation result
     */
    default TaxResult calculateSellTax(Player player, BigDecimal price, ItemStack itemStack) {
        return getTaxConfiguration().calculateSellTax(player, price, itemStack);
    }

    /**
     * Calculates the tax for a purchase operation.
     *
     * @param player    the player buying the item
     * @param price     the purchase price
     * @param itemStack the item being purchased (for item-specific rules)
     * @return the tax calculation result
     */
    default TaxResult calculatePurchaseTax(Player player, BigDecimal price, ItemStack itemStack) {
        return getTaxConfiguration().calculatePurchaseTax(player, price, itemStack);
    }
}
