package fr.maxlego08.zauctionhouse.migration.v3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3MigrationResultTest {

    @Test
    @DisplayName("success() porte les compteurs et aucun message d'erreur")
    void success_carriesCounters() {
        V3MigrationResult result = V3MigrationResult.success(4, 3, 2, 1, 250L);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getPlayersImported());
        assertEquals(3, result.getItemsImported());
        assertEquals(2, result.getTransactionsImported());
        assertEquals(1, result.getErrors());
        assertEquals(250L, result.getDurationMs());
        assertNull(result.getErrorMessage());
        assertEquals("Migration completed in 250ms - Players: 4, Items: 3, Transactions: 2, Errors: 1", result.toString());
    }

    @Test
    @DisplayName("failure() remet tous les compteurs a zero et porte le message")
    void failure_zeroCountersAndMessage() {
        V3MigrationResult result = V3MigrationResult.failure("No data found to migrate");

        assertFalse(result.isSuccess());
        assertEquals(0, result.getPlayersImported());
        assertEquals(0, result.getItemsImported());
        assertEquals(0, result.getTransactionsImported());
        assertEquals(0, result.getErrors());
        assertEquals(0L, result.getDurationMs());
        assertEquals("No data found to migrate", result.getErrorMessage());
        assertEquals("Migration failed: No data found to migrate", result.toString());
    }
}
