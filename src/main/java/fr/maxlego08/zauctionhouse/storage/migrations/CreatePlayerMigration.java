package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.storage.StorageSchemaDefinitions;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreatePlayerMigration extends Migration {

    @Override
    public void up() {
        create(Tables.PLAYERS, StorageSchemaDefinitions::players);
    }
}
