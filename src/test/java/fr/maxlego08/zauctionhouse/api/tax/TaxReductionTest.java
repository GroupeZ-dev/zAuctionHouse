package fr.maxlego08.zauctionhouse.api.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxReductionTest {

    @ParameterizedTest(name = "{0} % de reduction => multiplicateur {1}")
    @CsvSource({
            "0, 1.0",
            "10, 0.9",
            "25, 0.75",
            "50, 0.5",
            "100, 0.0",
    })
    void getMultiplier_isOneMinusPercentage(double percentage, double expected) {
        assertEquals(expected, new TaxReduction("perm", percentage).getMultiplier(), 1e-9);
    }

    @Test
    @DisplayName("les composants sont exposes tels quels")
    void components_exposed() {
        TaxReduction reduction = new TaxReduction("zauctionhouse.tax.vip", 50);

        assertEquals("zauctionhouse.tax.vip", reduction.permission());
        assertEquals(50, reduction.percentage(), 1e-9);
    }

    @Test
    @DisplayName("une reduction superieure a 100 % donne un multiplicateur negatif (aucune borne)")
    void getMultiplier_above100_isNegative() {
        assertEquals(-0.5, new TaxReduction("perm", 150).getMultiplier(), 1e-9);
    }
}
