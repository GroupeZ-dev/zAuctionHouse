package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;

import java.math.BigDecimal;
import java.util.*;

public class LogRepository extends Repository {

    /**
     * Marqueur ecrit dans {@code additional_data} par l'import V3
     * ({@code V3MigrationService.createLogEntry}). C'est le SEUL discriminant fiable d'un log
     * migre : depuis le correctif de C-033, une ligne migree ne porte plus {@code item_id = 0}
     * mais l'identifiant d'une ligne sentinelle de la table items, DIFFERENT pour chaque
     * transaction importee.
     */
    public static final String MIGRATED_FROM_V3 = "migrated_from_v3";

    public LogRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.LOGS);
    }

    public void createLog(LogType logType, int itemId, UUID playerUniqueId, UUID targetUniqueId, String itemstack, BigDecimal price, String economyName, String additionalData, Date readedAt) {
        insert(schema -> {
            schema.string("log_type", logType.name());
            schema.object("item_id", itemId);
            schema.uuid("player_unique_id", playerUniqueId);
            if (targetUniqueId != null) schema.uuid("target_unique_id", targetUniqueId);
            if (itemstack != null) schema.string("itemstack", itemstack);
            schema.decimal("price", price == null ? BigDecimal.ZERO : price);
            if (economyName != null) schema.string("economy_name", economyName);
            if (additionalData != null) schema.string("additional_data", additionalData);
            if (readedAt != null) schema.object("readed_at", readedAt);
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
        return select(LogDTO.class, schema -> schema.where("target_unique_id", sellerUniqueId.toString()).where("log_type", LogType.PURCHASE.name()).whereNull("readed_at").orderByDesc("created_at"));
    }

    /**
     * Selects all purchase logs where the player is the seller (target_unique_id).
     * These are the player's sales history.
     *
     * @param sellerUniqueId the seller's UUID
     * @return list of purchase logs for this seller
     */
    public List<LogDTO> selectSalesHistory(UUID sellerUniqueId, long expireAfterMs) {
        return select(LogDTO.class, schema -> {
            schema.where("target_unique_id", sellerUniqueId.toString());
            schema.where("log_type", LogType.PURCHASE.name());
            schema.where("item_id", ">", 0);
            if (expireAfterMs > 0) {
                schema.where("created_at", ">", new java.util.Date(System.currentTimeMillis() - expireAfterMs));
            }
            schema.orderByDesc("created_at");
        });
    }

    /**
     * Marks the specified logs as read by setting readed_at to the current timestamp.
     *
     * @param logIds the IDs of the logs to mark as read
     */
    public void markAsRead(Collection<Integer> logIds) {
        if (logIds == null || logIds.isEmpty()) return;

        // Use batch update for better performance
        Date now = new Date();
        var schemas = logIds.stream()
                .map(logId -> createUpdateSchema(schema -> {
                    schema.where("id", logId);
                    schema.object("readed_at", now);
                }))
                .toList();
        update(schemas);
    }

    /**
     * Marks a single log as read.
     *
     * @param logId the ID of the log to mark as read
     */
    public void markAsRead(int logId) {
        markAsRead(List.of(logId));
    }

    /**
     * Marks unread purchase logs as read for a specific item and seller.
     * Used by the cluster addon when the seller receives a real-time notification
     * on another server, to prevent a duplicate "while you were away" notification.
     *
     * @param itemId          the item ID
     * @param sellerUniqueId  the seller's UUID (stored as target_unique_id)
     */
    public void markPurchaseLogsAsReadByItem(int itemId, UUID sellerUniqueId) {
        update(schema -> {
            schema.where("item_id", itemId);
            schema.where("target_unique_id", sellerUniqueId.toString());
            schema.where("log_type", LogType.PURCHASE.name());
            schema.whereNull("readed_at");
            schema.object("readed_at", new Date());
        });
    }

    /**
     * Deletes every log entry produced by a player.
     *
     * @param playerUniqueId the actor whose logs are purged
     * @return the number of rows actually deleted
     */
    public long deleteByPlayer(UUID playerUniqueId) {
        // Sarah remonte deja le rowcount (DeleteRequest:35). Le pre-comptage par select()
        // materialisait en heap toutes les lignes, chacune portant un itemstack LONGTEXT,
        // et balayait la table deux fois.
        return Math.max(0, delete(schema -> schema.where("player_unique_id", playerUniqueId.toString())));
    }

    /**
     * Deletes every log entry older than the given age.
     *
     * @param olderThanMs the age, in milliseconds, beyond which a log is purged
     * @return the number of rows actually deleted
     */
    public long deleteOlderThan(long olderThanMs) {
        Date cutoff = new Date(System.currentTimeMillis() - olderThanMs);
        return Math.max(0, delete(schema -> schema.where("created_at", "<", cutoff)));
    }

    /**
     * Deletes the log entries imported from a previous version.
     * <p>
     * La purge porte sur le marqueur {@link #MIGRATED_FROM_V3} et NON sur {@code item_id = 0}.
     * Le correctif de C-033 a fait porter aux logs migres l'identifiant d'une ligne sentinelle
     * (pour satisfaire la cle etrangere vers items) : le predicat {@code item_id = 0} ne
     * ramenait donc plus aucune ligne et la commande {@code /ah admin logs clear-migrated}
     * annoncait un succes a zero ligne purgee. Le marqueur, lui, est ecrit par les DEUX
     * generations d'import -- avant comme apres C-033 -- donc la purge couvre les deux.
     *
     * @return the number of rows actually deleted
     */
    public long deleteMigrated() {
        return Math.max(0, delete(schema -> schema.where("additional_data", MIGRATED_FROM_V3)));
    }
}
