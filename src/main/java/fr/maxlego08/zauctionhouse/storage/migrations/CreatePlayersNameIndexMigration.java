package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

/**
 * Index sur players(name) : ZStorageManager.findUniqueId (3 commandes admin) et
 * CommandAuctionAdminGenerate faisaient un scan complet de la table.
 * <p>
 * ATTENTION chantier 8 (C-081) : NE PAS recreer cet index ailleurs, il est livre ici.
 * UNE CLASSE PAR INDEX, voir {@link CreateItemsStorageTypeIndexMigration}.
 */
public class CreatePlayersNameIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.PLAYERS, "name");
    }
}
