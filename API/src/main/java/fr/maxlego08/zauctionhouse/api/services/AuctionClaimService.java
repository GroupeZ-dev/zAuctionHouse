package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.services.result.ClaimResult;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling money claims from pending transactions.
 */
public interface AuctionClaimService {

    /**
     * Claims all pending money for a player.
     *
     * @param player the player to claim money for
     * @return a future containing the result of the claim operation
     */
    CompletableFuture<ClaimResult> claimMoney(Player player);

    /**
     * Gets the total pending money for a player, grouped by economy.
     *
     * @param playerUniqueId the player's UUID
     * @return a future containing a map of economy name to pending amount
     */
    CompletableFuture<Map<String, BigDecimal>> getPendingMoneyByEconomy(UUID playerUniqueId);

    /**
     * Gets the total pending money for a player across all economies.
     *
     * @param playerUniqueId the player's UUID
     * @return a future containing the total pending amount
     */
    CompletableFuture<BigDecimal> getTotalPendingMoney(UUID playerUniqueId);

    /**
     * Gets all pending transactions for a player.
     *
     * @param playerUniqueId the player's UUID
     * @return a future containing the list of pending transactions
     */
    CompletableFuture<List<TransactionDTO>> getPendingTransactions(UUID playerUniqueId);

    /**
     * Checks and processes pending money for a player on join.
     *
     * @param player the player who just joined
     */
    void handlePlayerJoin(Player player);
}
