package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sentinelle d'idempotence des migrations de donnees (C-034), sur SQLite reel.
 */
class MigrationStateRepositoryTest extends DatabaseTestBase {

    private static final String PROVIDER = "zauctionhousev3";

    @Test
    @DisplayName("la table existe apres les migrations de production, avec toutes ses colonnes")
    void table_existsWithColumns() throws Exception {
        assertTrue(tableExists(MigrationStateRepository.TABLE_NAME));
        assertTrue(columnsOf(MigrationStateRepository.TABLE_NAME).containsAll(
                List.of("provider_id", "server_name", "migrated_at", "players_imported", "items_imported", "transactions_imported")));
    }

    @Test
    @DisplayName("Tables.MIGRATION_STATE et MigrationStateRepository.TABLE_NAME ne peuvent pas diverger")
    void tableName_matchesTablesConstant() {
        assertEquals(Tables.MIGRATION_STATE, MigrationStateRepository.TABLE_NAME);
        assertEquals(MigrationStateRepository.TABLE_NAME, migrationState().getTableName());
    }

    @Test
    @DisplayName("select rend vide quand la migration n'a jamais tourne")
    void select_neverRan_empty() throws Exception {
        assertEquals(Optional.empty(), migrationState().select(PROVIDER));
        assertEquals(0, count(MigrationStateRepository.TABLE_NAME));
    }

    @Test
    @DisplayName("markMigrated insere une ligne complete, horodatee a l'instant de l'appel")
    void markMigrated_insertsRow() throws Exception {
        long before = System.currentTimeMillis();

        migrationState().markMigrated(PROVIDER, "node-1", 4, 3, 2);

        long after = System.currentTimeMillis();
        assertEquals(1, count(MigrationStateRepository.TABLE_NAME));

        MigrationStateRepository.MigrationStateDTO state = migrationState().select(PROVIDER).orElseThrow();
        assertEquals(PROVIDER, state.provider_id());
        assertEquals("node-1", state.server_name());
        assertEquals(4, state.players_imported());
        assertEquals(3, state.items_imported());
        assertEquals(2, state.transactions_imported());
        assertTrue(state.migrated_at() >= before && state.migrated_at() <= after, "migrated_at=" + state.migrated_at());

        assertEquals("node-1", queryString("SELECT server_name FROM " + MigrationStateRepository.TABLE_NAME + " WHERE provider_id = ?", PROVIDER));
        assertEquals(3, queryLong("SELECT items_imported FROM " + MigrationStateRepository.TABLE_NAME + " WHERE provider_id = ?", PROVIDER));
        assertEquals(state.migrated_at(), queryLong("SELECT migrated_at FROM " + MigrationStateRepository.TABLE_NAME + " WHERE provider_id = ?", PROVIDER));
    }

    @Test
    @DisplayName("markMigrated est un upsert : une relance (force) met la ligne a jour sans la dupliquer")
    void markMigrated_twice_updatesInPlace() throws Exception {
        migrationState().markMigrated(PROVIDER, "node-1", 4, 3, 2);
        long first = migrationState().select(PROVIDER).orElseThrow().migrated_at();

        migrationState().markMigrated(PROVIDER, "node-2", 0, 6, 4);

        assertEquals(1, count(MigrationStateRepository.TABLE_NAME));
        MigrationStateRepository.MigrationStateDTO state = migrationState().select(PROVIDER).orElseThrow();
        assertEquals("node-2", state.server_name());
        assertEquals(0, state.players_imported());
        assertEquals(6, state.items_imported());
        assertEquals(4, state.transactions_imported());
        assertTrue(state.migrated_at() >= first);
    }

    @Test
    @DisplayName("chaque provider a sa propre sentinelle")
    void markMigrated_distinctProviders_twoRows() throws Exception {
        migrationState().markMigrated(PROVIDER, "node-1", 1, 1, 1);
        migrationState().markMigrated("crazyauctions", "node-1", 2, 2, 2);

        assertEquals(2, count(MigrationStateRepository.TABLE_NAME));
        assertEquals(1, migrationState().select(PROVIDER).orElseThrow().items_imported());
        assertEquals(2, migrationState().select("crazyauctions").orElseThrow().items_imported());
        assertTrue(migrationState().select("donutauction").isEmpty());
    }

    @Test
    @DisplayName("select est sensible a la casse de l'identifiant : la commande normalise en minuscules avant")
    void select_isCaseSensitive() {
        migrationState().markMigrated(PROVIDER, "node-1", 1, 1, 1);

        assertTrue(migrationState().select(PROVIDER).isPresent());
        assertTrue(migrationState().select(PROVIDER.toUpperCase()).isEmpty());
    }

    @Test
    @DisplayName("des compteurs a zero sont acceptes (migration de transactions seules)")
    void markMigrated_zeroCounters() {
        migrationState().markMigrated(PROVIDER, "node-1", 0, 0, 0);

        MigrationStateRepository.MigrationStateDTO state = migrationState().select(PROVIDER).orElseThrow();
        assertEquals(0, state.players_imported());
        assertEquals(0, state.items_imported());
        assertEquals(0, state.transactions_imported());
    }
}
