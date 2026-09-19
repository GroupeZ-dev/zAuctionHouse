package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

/**
 * Index sur logs(created_at) : LogRepository.deleteOlderThan (purge des logs admin) faisait
 * un scan complet de la table.
 * <p>
 * UNE CLASSE PAR INDEX, voir {@link CreateItemsStorageTypeIndexMigration}.
 */
public class CreateLogsCreatedAtIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.LOGS, "created_at");
    }
}
