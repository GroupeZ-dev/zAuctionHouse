package fr.maxlego08.zauctionhouse.storage.repository.repositeries;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TransactionRepository extends Repository {

    public TransactionRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.TRANSACTIONS);
    }

    public void create(Item item, UUID playerUniqueId, String economyName, BigDecimal moneyBefore, BigDecimal moneyAfter, BigDecimal value, TransactionStatus status) {
        insert(schema -> {
            schema.object("item_id", item.getId());
            schema.uuid("player_unique_id", playerUniqueId);
            schema.string("economy_name", economyName);
            schema.decimal("before", moneyBefore);
            schema.decimal("after", moneyAfter);
            schema.decimal("value", value);
            schema.string("status", status.name());
        });
    }

    public List<TransactionDTO> selectByPlayer(UUID playerUniqueId) {
        return select(TransactionDTO.class, schema -> schema.where("player_unique_id", playerUniqueId.toString()));
    }

    public List<TransactionDTO> selectByPlayerAndStatus(UUID playerUniqueId, TransactionStatus status) {
        return select(TransactionDTO.class, schema -> schema.where("player_unique_id", playerUniqueId.toString()).where("status", status.name()));
    }

    public void updateStatus(int transactionId, TransactionStatus status) {
        updateStatus(List.of(transactionId), status);
    }

    public void updateStatus(Collection<Integer> transactionIds, TransactionStatus status) {
        if (transactionIds == null || transactionIds.isEmpty()) return;

        transactionIds.forEach(transactionId -> update(schema -> {
            schema.where("id", transactionId);
            schema.string("status", status.name());
        }));
    }
}
