package fr.maxlego08.zauctionhouse.api.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class LogTypeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "SALE, Sale",
            "PURCHASE, Purchase",
            "REMOVE_LISTED, Remove Listed",
            "REMOVE_SELLING, Remove Selling",
            "REMOVE_EXPIRED, Remove Expired",
            "REMOVE_PURCHASED, Remove Purchased"
    })
    @DisplayName("getDefaultDisplayName rend le libelle anglais par defaut de chaque type")
    void getDefaultDisplayName_returnsExpectedLabel(LogType logType, String expected) {
        assertEquals(expected, logType.getDefaultDisplayName());
    }

    @Test
    @DisplayName("l'enum compte exactement les six types connus de la base (colonne log_type)")
    void values_containsExactlySixTypes() {
        assertEquals(6, LogType.values().length);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LogType.class)
    @DisplayName("name()/valueOf font l'aller-retour utilise par le mapping log_type")
    void valueOf_roundTripsName(LogType logType) {
        assertSame(logType, LogType.valueOf(logType.name()));
        assertFalse(logType.getDefaultDisplayName().isBlank());
    }
}
