package fr.maxlego08.zauctionhouse.api.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxResultTest {

    private static final BigDecimal PRICE = new BigDecimal("1000");
    private static final BigDecimal TAX = new BigDecimal("50.00");

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "attendu " + expected + " mais obtenu " + actual);
    }

    @Test
    @DisplayName("disabled() : aucune taxe, prix inchange, ni bypass ni reduction")
    void disabled_isNeutral() {
        TaxResult result = TaxResult.disabled(PRICE);

        assertMoney("0", result.taxAmount());
        assertEquals(0, result.taxPercentage(), 1e-9);
        assertSame(PRICE, result.originalPrice());
        assertSame(PRICE, result.finalPrice());
        assertFalse(result.isBypassed());
        assertFalse(result.isReduced());
        assertEquals(0, result.reductionPercentage(), 1e-9);
        assertNull(result.appliedType());
        assertFalse(result.hasTax());
        assertFalse(result.mintsMoney());
    }

    @Test
    @DisplayName("bypassed() : identique a disabled() mais marque bypassed")
    void bypassed_isFlagged() {
        TaxResult result = TaxResult.bypassed(PRICE);

        assertTrue(result.isBypassed());
        assertFalse(result.hasTax());
        assertSame(PRICE, result.finalPrice());
        assertMoney("1000", result.buyerPays());
        assertMoney("1000", result.sellerReceives());
    }

    @Test
    @DisplayName("hasTax est vrai uniquement pour une taxe strictement positive")
    void hasTax_strictlyPositiveOnly() {
        assertFalse(new TaxResult(BigDecimal.ZERO, 0, PRICE, PRICE, false, false, 0, TaxType.SELL).hasTax());
        assertFalse(new TaxResult(new BigDecimal("0.00"), 0, PRICE, PRICE, false, false, 0, TaxType.SELL).hasTax());
        assertFalse(new TaxResult(new BigDecimal("-1"), 0, PRICE, PRICE, false, false, 0, TaxType.SELL).hasTax());
        assertTrue(new TaxResult(new BigDecimal("0.01"), 0, PRICE, PRICE, false, false, 0, TaxType.SELL).hasTax());
    }

    @Test
    @DisplayName("sans taxe, acheteur et vendeur sont au prix d'origine quel que soit finalPrice")
    void noTax_bothSidesAtOriginalPrice() {
        TaxResult result = new TaxResult(BigDecimal.ZERO, 0, PRICE, new BigDecimal("999"), false, false, 0, TaxType.CAPITALISM);

        assertMoney("1000", result.buyerPays());
        assertMoney("1000", result.sellerReceives());
        assertFalse(result.mintsMoney());
    }

    @ParameterizedTest(name = "type {0} : l'acheteur paie le prix, le vendeur touche finalPrice")
    @EnumSource(value = TaxType.class, names = {"SELL", "PURCHASE", "BOTH"})
    void nonCapitalism_sellerReceivesFinalPrice(TaxType type) {
        TaxResult result = new TaxResult(TAX, 5, PRICE, new BigDecimal("950"), false, false, 0, type);

        assertMoney("1000", result.buyerPays());
        assertMoney("950", result.sellerReceives());
        assertFalse(result.mintsMoney());
    }

    @Test
    @DisplayName("CAPITALISM : l'acheteur paie finalPrice, le vendeur touche le prix")
    void capitalism_buyerPaysFinalPrice() {
        TaxResult result = new TaxResult(TAX, 5, PRICE, new BigDecimal("1050"), false, false, 0, TaxType.CAPITALISM);

        assertMoney("1050", result.buyerPays());
        assertMoney("1000", result.sellerReceives());
        assertFalse(result.mintsMoney());
    }

    @Test
    @DisplayName("appliedType null avec taxe : semantique PURCHASE (la seule qui ne cree pas de monnaie)")
    void nullAppliedType_behavesLikePurchase() {
        TaxResult result = new TaxResult(TAX, 5, PRICE, new BigDecimal("950"), false, false, 0, null);

        assertMoney("1000", result.buyerPays());
        assertMoney("950", result.sellerReceives());
    }

    @Test
    @DisplayName("le constructeur historique a sept arguments laisse appliedType a null")
    @SuppressWarnings("deprecation")
    void legacyConstructor_nullAppliedType() {
        TaxResult result = new TaxResult(TAX, 5, PRICE, new BigDecimal("950"), false, true, 25);

        assertNull(result.appliedType());
        assertTrue(result.isReduced());
        assertEquals(25, result.reductionPercentage(), 1e-9);
        assertMoney("950", result.sellerReceives());
        assertMoney("1000", result.buyerPays());
    }

    @Test
    @DisplayName("mintsMoney detecte un vendeur credite de plus que ce que paie l'acheteur")
    void mintsMoney_detectsInconsistentResult() {
        // Type PURCHASE mais finalPrice superieur au prix : le vendeur toucherait 1100 pour 1000 payes.
        TaxResult minting = new TaxResult(TAX, 5, PRICE, new BigDecimal("1100"), false, false, 0, TaxType.PURCHASE);
        assertTrue(minting.mintsMoney());

        // Type CAPITALISM mais finalPrice inferieur au prix : l'acheteur paierait 900 pour un vendeur a 1000.
        TaxResult mintingCapitalism = new TaxResult(TAX, 5, PRICE, new BigDecimal("900"), false, false, 0, TaxType.CAPITALISM);
        assertTrue(mintingCapitalism.mintsMoney());

        // Egalite stricte : pas de creation de monnaie.
        TaxResult equal = new TaxResult(TAX, 5, PRICE, PRICE, false, false, 0, TaxType.PURCHASE);
        assertFalse(equal.mintsMoney());
    }
}
