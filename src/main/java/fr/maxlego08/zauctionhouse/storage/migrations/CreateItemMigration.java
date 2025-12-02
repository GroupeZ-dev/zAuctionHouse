package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateItemMigration extends Migration {

    @Override
    public void up() {
        create(Tables.ITEMS, table -> {
            table.autoIncrement("id");
            table.string("item_type", 255);
            table.string("seller_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("buyer_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);
            table.decimal("price", 65, 2);
            table.string("economy_name", 255);
            table.string("storage_type", 32);
            table.string("server_name", 255);
            table.timestamp("expired_at");
            table.timestamps();
        });
    }
}
