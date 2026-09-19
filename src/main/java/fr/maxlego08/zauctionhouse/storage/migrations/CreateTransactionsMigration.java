package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.api.storage.Tables;

public class CreateTransactionsMigration extends Migration {

    @Override
    public void up() {
        // createOrAlter et non create : positionne isAlter() a true, ce qui autorise
        // MigrationManager a AJOUTER les colonnes manquantes sur les bases deja migrees.
        // Avec create(), MigrationManager sort immediatement (l.100-102) et claim_token
        // n'existerait jamais sur une installation existante.
        createOrAlter(Tables.TRANSACTIONS, table -> {
            table.autoIncrement("id");
            table.integer("item_id").foreignKey(Tables.ITEMS, "id", true);
            table.string("player_unique_id", 36).foreignKey(Tables.PLAYERS, "unique_id", true);
            table.string("economy_name", 255);
            table.decimal("before", 65, 2);
            table.decimal("after", 65, 2);
            table.decimal("value", 65, 2);
            table.string("status", 32);
            // Reservation atomique du claim : un claim ecrit son jeton sur les lignes qu'il
            // remporte AVANT de payer. Nullable = ligne libre.
            table.string("claim_token", 36).nullable();
            // Horodatage ECRIT PAR NOUS (updated_at n'est pas auto-bumpe sous SQLite),
            // seule base fiable du filet de recuperation des reservations orphelines.
            table.timestamp("claim_reserved_at").nullable();
            table.timestamps();
        });
    }
}
