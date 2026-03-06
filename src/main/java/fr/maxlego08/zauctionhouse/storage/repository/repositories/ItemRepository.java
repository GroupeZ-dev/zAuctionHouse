package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.sarah.database.Schema;
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
import java.util.*;
import java.util.function.Consumer;

public class ItemRepository extends Repository {

    public ItemRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.ITEMS);
    }

    public int create(Player seller, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {
        return create(seller.getUniqueId(), itemType, price, expiredAt, auctionEconomy);
    }

    public int create(UUID sellerUniqueId, ItemType itemType, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {
        var expiredAtDate = new Date(expiredAt);
        var serverName = this.plugin.getConfiguration().getServerName();
        return insertSync(schema -> {
            schema.string("item_type", itemType.name());
            schema.uuid("seller_unique_id", sellerUniqueId);
            schema.string("economy_name", auctionEconomy.getName());
            schema.decimal("price", price);
            schema.object("expired_at", expiredAtDate);
            schema.object("storage_type", StorageType.LISTED.name());
            schema.string("server_name", serverName);
        });
    }

    public List<ItemDTO> select() {
        return select(ItemDTO.class, schema -> schema.where("storage_type", "!=", StorageType.DELETED.name()));
    }

    public void updateItem(Item item, StorageType storageType) {
        this.update(createUpdateSchema(item, storageType));
    }

    public Optional<ItemDTO> select(int id) {
        return select(ItemDTO.class, schema -> schema.where("id", id)).stream().findFirst();
    }

    public void updateItems(Map<StorageType, List<Item>> itemsByStorageType) {
        for (Map.Entry<StorageType, List<Item>> entry : itemsByStorageType.entrySet()) {
            StorageType storageType = entry.getKey();
            List<Item> items = entry.getValue();
            if (items.isEmpty()) continue;

            // Update each item individually using the consumer pattern
            for (Item item : items) {
                update(createUpdateSchema(item, storageType));
            }
        }
    }

    private Consumer<Schema> createUpdateSchema(Item item, StorageType storageType) {
        return schema -> {
            schema.where("id", item.getId());
            schema.string("storage_type", storageType.name());
            if (storageType != StorageType.DELETED) {
                schema.object("expired_at", item.getExpiredAt());
            }
            if (storageType == StorageType.PURCHASED) {
                schema.uuid("buyer_unique_id", item.getBuyerUniqueId());
            }
        };
    }

    public List<ItemDTO> select(List<String> ids) {
        return select(ItemDTO.class, schema -> schema.whereIn("id", ids));
    }
}
