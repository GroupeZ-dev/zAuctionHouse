package fr.maxlego08.zauctionhouse.storage.repository.repositeries;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.log.LogContentType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.UUID;

public class LogRepository extends Repository {

    public LogRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.LOGS);
    }

    public void createLog(LogType logType, LogContentType contentType, int contentId, UUID playerUniqueId, UUID targetUniqueId,
                          ItemStack itemStack, BigDecimal price, String economyName, String additionalData) {
        insertSchema(schema -> {
            schema.string("log_type", logType.name());
            schema.string("content_type", contentType.name());
            schema.integer("content_id", contentId);
            schema.uuid("player_unique_id", playerUniqueId);
            schema.uuid("target_unique_id", targetUniqueId);
            schema.string("itemstack", itemStack == null ? null : Base64ItemStack.encode(itemStack));
            schema.decimal("price", price == null ? BigDecimal.ZERO : price);
            schema.string("economy_name", economyName);
            schema.string("additional_data", additionalData);
        });
    }
}
