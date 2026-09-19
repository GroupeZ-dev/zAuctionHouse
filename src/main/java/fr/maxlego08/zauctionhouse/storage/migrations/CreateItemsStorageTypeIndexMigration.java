package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

/**
 * Index sur items(storage_type) : ItemRepository.select() filtre systematiquement
 * storage_type != DELETED.
 * <p>
 * UNE CLASSE PAR INDEX, deliberement : Sarah emet CREATE INDEX sans IF NOT EXISTS et
 * MigrationManager.insertMigration est appele PAR SCHEMA. Regrouper plusieurs index dans
 * une seule Migration ferait qu'un echec au 3e index sur 4 laisserait les suivants
 * definitivement non crees (le nom de classe est deja enregistre au boot suivant).
 */
public class CreateItemsStorageTypeIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.ITEMS, "storage_type");
    }
}
