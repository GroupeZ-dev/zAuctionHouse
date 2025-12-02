package fr.maxlego08.zauctionhouse.storage.repository.repositeries;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class ItemRepository extends Repository {

    public ItemRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.ITEMS);
    }

    public int create(Player seller, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {
        var expiredAtDate = new Date(expiredAt);
        var serverName = this.plugin.getConfiguration().getServerName();
        return insertSchema(schema -> {
            schema.string("item_type", itemType.name());
            schema.uuid("seller_unique_id", seller.getUniqueId());
            schema.string("economy_name", auctionEconomy.getName());
            schema.decimal("price", price);
            schema.object("expired_at", expiredAtDate);
            schema.object("storage_type", StorageType.LISTED);
            schema.string("server_name", serverName);
        });
    }

    public List<ItemDTO> select() {
        return select(ItemDTO.class, schema -> schema.where("storage_type", "!=", StorageType.DELETED.name()));
    }

    public void updateItem(Item item, StorageType storageType) {
        this.update(schema -> {
            schema.where("id", item.getId());
            schema.string("storage_type", storageType.name());
            if (storageType != StorageType.DELETED) {
                schema.object("expired_at", item.getExpiredAt());
            }
            if (storageType == StorageType.PURCHASED) {
                schema.uuid("buyer_unique_id", item.getBuyerUniqueId());
            }
        });
    }
}
