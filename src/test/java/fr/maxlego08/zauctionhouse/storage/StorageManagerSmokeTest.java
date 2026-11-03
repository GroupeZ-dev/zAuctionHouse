package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie que le socle {@link StorageManagerTestBase} demarre un vrai manager sur SQLite.
 */
class StorageManagerSmokeTest extends StorageManagerTestBase {

    @Test
    @DisplayName("onEnable migre la base et enregistre les repositories")
    void onEnable_registersRepositories() throws Exception {
        assertNotNull(storageManager.getDatabaseConnection());
        assertNotNull(storageManager.with(ItemRepository.class));
        assertNotNull(storageManager.with(PlayerRepository.class));
        assertTrue(tableExists(Tables.ITEMS));
        assertEquals(11, count(MIGRATION_TABLE));
    }

    @Test
    @DisplayName("un redemarrage sur la meme base conserve les donnees et ne rejoue pas les migrations")
    void restart_keepsDataAndMigrations() throws Exception {
        newPlayer("Alice");

        restartStorageManager();

        assertEquals(1, count(Tables.PLAYERS));
        assertEquals(11, count(MIGRATION_TABLE));
        assertEquals("Alice", storageManager.getPlayerName(players().selectByName("Alice")));
    }
}
