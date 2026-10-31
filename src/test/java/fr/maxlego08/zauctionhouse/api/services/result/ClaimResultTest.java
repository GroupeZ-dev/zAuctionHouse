package fr.maxlego08.zauctionhouse.api.services.result;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ClaimResultTest {

    @Test
    @DisplayName("success : succes, montant et economie conserves")
    void success_keepsAmountAndEconomy() {
        AuctionEconomy economy = mock(AuctionEconomy.class);

        ClaimResult result = ClaimResult.success("ok", 12.5, economy);

        assertTrue(result.success());
        assertTrue(result.isSuccess());
        assertEquals("ok", result.message());
        assertEquals("ok", result.getMessage());
        assertEquals(12.5, result.amountClaimed());
        assertEquals(12.5, result.getAmountClaimed());
        assertSame(economy, result.economy());
        assertSame(economy, result.getEconomy());
    }

    @Test
    @DisplayName("failure : echec, montant nul, aucune economie")
    void failure_isNotSuccessful() {
        ClaimResult result = ClaimResult.failure("boom");

        assertFalse(result.success());
        assertFalse(result.isSuccess());
        assertEquals("boom", result.message());
        assertEquals(0.0, result.amountClaimed());
        assertNull(result.economy());
    }

    @Test
    @DisplayName("nothingToClaim : succes sans montant ni economie (distinct d'un echec)")
    void nothingToClaim_isSuccessfulWithZeroAmount() {
        ClaimResult result = ClaimResult.nothingToClaim("nothing");

        assertTrue(result.success());
        assertEquals("nothing", result.message());
        assertEquals(0.0, result.amountClaimed());
        assertNull(result.economy());
        assertFalse(result.equals(ClaimResult.failure("nothing")));
    }
}
