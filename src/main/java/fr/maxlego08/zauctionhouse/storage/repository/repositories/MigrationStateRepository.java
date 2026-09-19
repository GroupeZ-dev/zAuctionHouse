package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.storage.Repository;

import java.util.Optional;

/**
 * Trace des migrations de donnees deja executees.
 * <p>
 * La sentinelle vit en base PARTAGEE et non en memoire : c'est la seule facon de bloquer aussi
 * une seconde execution lancee depuis un AUTRE noeud du reseau.
 */
public class MigrationStateRepository extends Repository {

    /**
     * Nom de la table, prefixe non resolu (Sarah remplace {@code %prefix%} a l'execution).
     * <p>
     * La constante est portee ici et non par {@code Tables} pour que ce repository reste
     * compilable independamment : toute migration qui cree la table doit utiliser EXACTEMENT
     * cette valeur.
     */
    public static final String TABLE_NAME = "%prefix%migration_state";

    public MigrationStateRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, TABLE_NAME);
    }

    /**
     * Lit la sentinelle d'un provider de migration.
     *
     * @param providerId the migration provider identifier
     * @return the sentinel row, or {@link Optional#empty()} when the migration never ran
     */
    public Optional<MigrationStateDTO> select(String providerId) {
        return select(MigrationStateDTO.class, schema -> schema.where("provider_id", providerId)).stream().findFirst();
    }

    /**
     * Marque un provider comme deja migre.
     * <p>
     * N'est appele QU'EN CAS DE SUCCES : une sentinelle ecrite apres un echec bloquerait
     * definitivement une reprise, et l'argent V3 resterait perdu pour toujours.
     *
     * @param providerId   the migration provider identifier
     * @param serverName   the server that ran the migration
     * @param players      number of imported players
     * @param items        number of imported items
     * @param transactions number of imported transactions
     */
    public void markMigrated(String providerId, String serverName, int players, int items, int transactions) {
        upsert(schema -> {
            schema.string("provider_id", providerId).primary();
            schema.string("server_name", serverName);
            schema.bigInt("migrated_at", System.currentTimeMillis());
            schema.object("players_imported", players);
            schema.object("items_imported", items);
            schema.object("transactions_imported", transactions);
        });
    }

    /**
     * Sentinelle d'idempotence d'une migration de donnees.
     *
     * @param provider_id           the migration provider identifier
     * @param server_name           the server that ran the migration
     * @param migrated_at           epoch millis of the migration
     * @param players_imported      number of imported players
     * @param items_imported        number of imported items
     * @param transactions_imported number of imported transactions
     */
    public record MigrationStateDTO(String provider_id, String server_name, long migrated_at,
                                    int players_imported, int items_imported, int transactions_imported) {
    }
}
