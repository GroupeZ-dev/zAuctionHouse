package fr.maxlego08.zauctionhouse.storage.repository.repositeries;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LogRepository extends Repository {

    public LogRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.LOGS);
    }

    public void createLog(LogType logType, int itemId, UUID playerUniqueId, UUID targetUniqueId, String itemstack, BigDecimal price, String economyName, String additionalData) {
        insert(schema -> {
            schema.string("log_type", logType.name());
            schema.object("item_id", itemId);
            schema.uuid("player_unique_id", playerUniqueId);
            if (targetUniqueId != null) schema.uuid("target_unique_id", targetUniqueId);
            if (itemstack != null) schema.string("itemstack", itemstack);
            schema.decimal("price", price == null ? BigDecimal.ZERO : price);
            if (economyName != null) schema.string("economy_name", economyName);
            if (additionalData != null) schema.string("additional_data", additionalData);
        });
    }

    public List<LogDTO> selectByPlayer(UUID playerUniqueId) {
        return select(LogDTO.class, schema -> schema.where("player_unique_id", playerUniqueId.toString()).orderByDesc("created_at"));
    }

    public List<LogDTO> selectByTarget(UUID targetUniqueId) {
        return select(LogDTO.class, schema -> schema.where("target_unique_id", targetUniqueId.toString()).orderByDesc("created_at"));
    }

    public List<LogDTO> selectByPlayerOrTarget(UUID uniqueId) {
        // Get logs where player is the actor
        List<LogDTO> playerLogs = selectByPlayer(uniqueId);
        // Get logs where player is the target
        List<LogDTO> targetLogs = selectByTarget(uniqueId);

        // Merge results, avoiding duplicates
        Set<Integer> seenIds = new HashSet<>();
        List<LogDTO> result = new ArrayList<>();

        for (LogDTO log : playerLogs) {
            if (seenIds.add(log.id())) {
                result.add(log);
            }
        }
        for (LogDTO log : targetLogs) {
            if (seenIds.add(log.id())) {
                result.add(log);
            }
        }

        // Sort by created_at descending
        result.sort((a, b) -> b.created_at().compareTo(a.created_at()));
        return result;
    }

    /**
     * Selects all unread purchase logs where the player is the seller (target_unique_id).
     * These are sales made while the player was offline.
     *
     * @param sellerUniqueId the seller's UUID
     * @return list of unread purchase logs
     */
    public List<LogDTO> selectUnreadSales(UUID sellerUniqueId) {
        return select(LogDTO.class, schema -> schema
                .where("target_unique_id", sellerUniqueId.toString())
                .where("log_type", LogType.PURCHASE.name())
                .whereNull("readed_at")
                .orderByDesc("created_at"));
    }

    /**
     * Selects all purchase logs where the player is the seller (target_unique_id).
     * These are the player's sales history.
     *
     * @param sellerUniqueId the seller's UUID
     * @return list of purchase logs for this seller
     */
    public List<LogDTO> selectSalesHistory(UUID sellerUniqueId) {
        return select(LogDTO.class, schema -> schema
                .where("target_unique_id", sellerUniqueId.toString())
                .where("log_type", LogType.PURCHASE.name())
                .orderByDesc("created_at"));
    }

    /**
     * Marks the specified logs as read by setting readed_at to the current timestamp.
     *
     * @param logIds the IDs of the logs to mark as read
     */
    public void markAsRead(Collection<Integer> logIds) {
        if (logIds == null || logIds.isEmpty()) return;

        logIds.forEach(logId -> update(schema -> {
            schema.where("id", logId);
            schema.object("readed_at", new Date());
        }));
    }

    /**
     * Marks a single log as read.
     *
     * @param logId the ID of the log to mark as read
     */
    public void markAsRead(int logId) {
        markAsRead(List.of(logId));
    }
}
