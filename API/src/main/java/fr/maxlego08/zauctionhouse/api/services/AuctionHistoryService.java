package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.history.ItemLog;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling player sales history and notifications.
 */
public interface AuctionHistoryService {

    /**
     * Gets the sales history for a player (items they sold).
     *
     * @param playerUniqueId the player's UUID
     * @return a future containing the list of sales logs
     */
    CompletableFuture<List<ItemLog>> getSalesHistory(UUID playerUniqueId);

    /**
     * Gets unread sales for a player (sales made while they were offline).
     *
     * @param playerUniqueId the player's UUID
     * @return a future containing the list of unread sales logs
     */
    CompletableFuture<List<LogDTO>> getUnreadSales(UUID playerUniqueId);

    /**
     * Marks sales as read for a player.
     *
     * @param logIds the IDs of the logs to mark as read
     * @return a future that completes when the logs are marked
     */
    CompletableFuture<Void> markSalesAsRead(List<Integer> logIds);

    /**
     * Handles player join to notify about sales made while offline.
     *
     * @param player the player who just joined
     */
    void handlePlayerJoin(Player player);

    /**
     * Opens the sales history inventory for a player.
     *
     * @param player the player to open the inventory for
     */
    void openHistoryInventory(Player player);

    /**
     * Opens the sales history inventory for a player at a specific page.
     *
     * @param player the player to open the inventory for
     * @param page   the page number
     */
    void openHistoryInventory(Player player, int page);
}
