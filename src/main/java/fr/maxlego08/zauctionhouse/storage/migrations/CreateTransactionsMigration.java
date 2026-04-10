package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.storage.StorageSchemaDefinitions;

public class CreateTransactionsMigration extends Migration {

    @Override
    public void up() {
        create(Tables.TRANSACTIONS, StorageSchemaDefinitions::transactions);
    }
}
