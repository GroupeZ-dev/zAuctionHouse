package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateTransactionsMigration extends Migration {

    @Override
    public void up() {
        create(Tables.TRANSACTIONS, table -> {
            table.autoIncrement("id");
            table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
            table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("economy_name", 255);
            table.decimal("before", 65, 2);
            table.decimal("after", 65, 2);
            table.decimal("value", 65, 2);
            table.string("status", 32);
            table.timestamps();
        });
    }
}
