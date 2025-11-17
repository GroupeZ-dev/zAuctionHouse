package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateLogsMigration extends Migration {

    @Override
    public void up() {
        create(Tables.LOGS, table -> {
            table.autoIncrement("id");
            table.string("log_type", 255);
            table.string("content_type", 255);
            table.integer("content_id");
            table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("target_unique_id", 36).nullable().foreignKey(Tables.PLAYERS, "unique_id", true);
            table.longText("itemstack").nullable();
            table.decimal("price", 65, 2).defaultValue(0);
            table.string("economy_name", 255).nullable();
            table.longText("additional_data").nullable();
            table.timestamps();
        });
    }
}
