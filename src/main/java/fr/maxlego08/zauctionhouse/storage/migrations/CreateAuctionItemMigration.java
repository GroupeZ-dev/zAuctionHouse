package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateAuctionItemMigration extends Migration {

    @Override
    public void up() {
        create(Tables.AUCTION_ITEMS, table -> {
            table.autoIncrement("id");
            table.string("seller_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("buyer_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);
            table.longText("itemstack");
            table.decimal("price", 65, 2);
            table.string("economy_name", 255);
            table.string("storage_type", 32);
            table.timestamp("expire_at");
            table.timestamps();
        });
    }
}
