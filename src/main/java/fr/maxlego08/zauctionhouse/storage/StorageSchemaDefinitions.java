package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.sarah.database.Schema;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class StorageSchemaDefinitions {

    private StorageSchemaDefinitions() {
    }

    public static Map<String, Consumer<Schema>> requiredTables() {
        Map<String, Consumer<Schema>> tables = new LinkedHashMap<>();
        tables.put(Tables.PLAYERS, StorageSchemaDefinitions::players);
        tables.put(Tables.ITEMS, StorageSchemaDefinitions::items);
        tables.put(Tables.AUCTION_ITEMS, StorageSchemaDefinitions::auctionItems);
        tables.put(Tables.TRANSACTIONS, StorageSchemaDefinitions::transactions);
        tables.put(Tables.LOGS, StorageSchemaDefinitions::logs);
        return tables;
    }

    public static void players(Schema table) {
        table.uuid("unique_id").primary().unique();
        table.string("name", 16);
        table.timestamps();
    }

    public static void items(Schema table) {
        table.autoIncrement("id");
        table.string("item_type", 255);
        table.string("seller_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
        table.string("buyer_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);
        table.decimal("price", 65, 2);
        table.string("economy_name", 255);
        table.enumType("storage_type", StorageType.class);
        table.string("server_name", 255);
        table.timestamp("expired_at");
        table.timestamps();
    }

    public static void auctionItems(Schema table) {
        table.autoIncrement("id");
        table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
        table.longText("itemstack");
        table.timestamps();
    }

    public static void transactions(Schema table) {
        table.autoIncrement("id");
        table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
        table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
        table.string("economy_name", 255);
        table.decimal("before", 65, 2);
        table.decimal("after", 65, 2);
        table.decimal("value", 65, 2);
        table.string("status", 32);
        table.timestamps();
    }

    public static void logs(Schema table) {
        table.autoIncrement("id");
        table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
        table.string("log_type", 255);
        table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
        table.string("target_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);
        table.longText("itemstack").nullable();
        table.decimal("price", 65, 2).defaultValue(0);
        table.string("economy_name", 255).nullable();
        table.longText("additional_data").nullable();
        table.timestamp("readed_at").nullable();
        table.timestamps();
    }
}
