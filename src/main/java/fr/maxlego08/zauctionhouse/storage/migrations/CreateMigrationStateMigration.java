package fr.maxlego08.zauctionhouse.storage.migrations;

import fr.maxlego08.sarah.database.Migration;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;

/**
 * Trace des migrations de donnees deja executees.
 * <p>
 * La sentinelle vit en base PARTAGEE et non en memoire : c'est la seule facon de bloquer aussi
 * une seconde execution lancee depuis un AUTRE noeud du reseau.
 * <p>
 * Le nom de table est repris de {@link MigrationStateRepository#TABLE_NAME} et non de
 * {@code Tables.MIGRATION_STATE} : c'est le repository qui lit et ecrit la table, les deux
 * constantes ne peuvent donc jamais diverger.
 */
public class CreateMigrationStateMigration extends Migration {

    @Override
    public void up() {
        // createOrAlter : sur une base ou la table existerait deja (rejeu manuel, restauration
        // partielle), les colonnes manquantes sont ajoutees au lieu d'etre silencieusement
        // sautees. Sur une base neuve le comportement est strictement celui de create().
        createOrAlter(MigrationStateRepository.TABLE_NAME, table -> {
            table.string("provider_id", 64).primary();
            table.string("server_name", 255);
            table.bigInt("migrated_at");
            table.integer("players_imported");
            table.integer("items_imported");
            table.integer("transactions_imported");
        });
    }
}
