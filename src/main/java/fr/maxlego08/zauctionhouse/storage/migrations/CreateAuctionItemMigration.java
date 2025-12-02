package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateAuctionItemMigration extends Migration {

    @Override
    public void up() {
        create(Tables.AUCTION_ITEMS, table -> {
            table.autoIncrement("id");
            table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
            table.longText("itemstack");
            table.timestamps();
        });
    }
}
