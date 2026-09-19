package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

/**
 * Index sur transactions(player_unique_id) : selectByPlayerAndStatus est appele a CHAQUE
 * connexion de joueur, et faisait jusqu'ici un scan complet de la table.
 * <p>
 * UNE CLASSE PAR INDEX, deliberement : Sarah emet CREATE INDEX sans IF NOT EXISTS et
 * MigrationManager.insertMigration est appele PAR SCHEMA. Regrouper plusieurs index dans
 * une seule Migration ferait qu'un echec au 3e index sur 4 laisserait les suivants
 * definitivement non crees (le nom de classe est deja enregistre au boot suivant).
 */
public class CreateTransactionsPlayerIndexMigration extends Migration {

    @Override
    public void up() {
        index(Tables.TRANSACTIONS, "player_unique_id");
    }
}
