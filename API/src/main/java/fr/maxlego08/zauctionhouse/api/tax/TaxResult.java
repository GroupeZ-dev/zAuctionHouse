package fr.maxlego08.zauctionhouse.api.tax;

import java.math.BigDecimal;

/**
 * Contains the result of a tax calculation.
 *
 * @param taxAmount           the calculated tax amount
 * @param taxPercentage       the effective tax percentage applied
 * @param originalPrice       the original price before tax
 * @param finalPrice          the final price (depends on context: what buyer pays or seller receives)
 * @param isBypassed          whether the tax was bypassed due to permission
 * @param isReduced           whether a tax reduction was applied
 * @param reductionPercentage the reduction percentage applied (0 if no reduction)
 * @param appliedType         le type de taxe REELLEMENT applique : celui de la regle par item
 *                            si une regle a matche, sinon celui de l'economie. {@code null}
 *                            pour les resultats construits par l'ancien constructeur a sept
 *                            arguments ; dans ce cas la semantique PURCHASE/BOTH est supposee
 *                            (l'acheteur paie {@code originalPrice}, le vendeur touche
 *                            {@code finalPrice}), la seule qui ne puisse pas creer de monnaie.
 */
public record TaxResult(
        BigDecimal taxAmount,
        double taxPercentage,
        BigDecimal originalPrice,
        BigDecimal finalPrice,
        boolean isBypassed,
        boolean isReduced,
        double reductionPercentage,
        TaxType appliedType
) {

    /**
     * Constructeur historique, conserve pour la compatibilite SOURCE et BINAIRE des addons
     * compiles contre une version anterieure de l'API. Son descripteur JVM est inchange.
     *
     * @param taxAmount           the calculated tax amount
     * @param taxPercentage       the effective tax percentage applied
     * @param originalPrice       the original price before tax
     * @param finalPrice          the final price
     * @param isBypassed          whether the tax was bypassed due to permission
     * @param isReduced           whether a tax reduction was applied
     * @param reductionPercentage the reduction percentage applied (0 if no reduction)
     * @deprecated utiliser le constructeur canonique a huit arguments, qui porte le type de
     * taxe reellement applique.
     */
    @Deprecated
    public TaxResult(BigDecimal taxAmount, double taxPercentage, BigDecimal originalPrice,
                     BigDecimal finalPrice, boolean isBypassed, boolean isReduced,
                     double reductionPercentage) {
        this(taxAmount, taxPercentage, originalPrice, finalPrice, isBypassed, isReduced, reductionPercentage, null);
    }

    /**
     * Creates a result for when tax is bypassed.
     *
     * @param price the original price
     * @return a TaxResult with no tax applied
     */
    public static TaxResult bypassed(BigDecimal price) {
        return new TaxResult(BigDecimal.ZERO, 0, price, price, true, false, 0, null);
    }

    /**
     * Creates a result for when tax is disabled.
     *
     * @param price the original price
     * @return a TaxResult with no tax applied
     */
    public static TaxResult disabled(BigDecimal price) {
        return new TaxResult(BigDecimal.ZERO, 0, price, price, false, false, 0, null);
    }

    /**
     * Checks if any tax was applied.
     *
     * @return true if tax amount is greater than zero
     */
    public boolean hasTax() {
        return taxAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Montant reellement debite a l'ACHETEUR.
     * <p>
     * Aucun consommateur ne doit plus rebrancher sur {@code TaxConfiguration.getTaxType()} :
     * c'est exactement la divergence qui faisait payer a l'acheteur un montant different de
     * celui qui avait ete verifie.
     *
     * @return le montant que l'acheteur doit payer
     */
    public BigDecimal buyerPays() {
        if (!hasTax()) return originalPrice;
        return appliedType == TaxType.CAPITALISM ? finalPrice : originalPrice;
    }

    /**
     * Montant reellement credite au VENDEUR.
     *
     * @return le montant que le vendeur doit toucher
     */
    public BigDecimal sellerReceives() {
        if (!hasTax()) return originalPrice;
        return appliedType == TaxType.CAPITALISM ? originalPrice : finalPrice;
    }

    /**
     * Vrai si la configuration produirait de la monnaie, c'est-a-dire si le vendeur toucherait
     * strictement plus que ce que l'acheteur paie. Doit etre teste AVANT tout mouvement d'argent.
     *
     * @return true si la configuration cree de la monnaie
     */
    public boolean mintsMoney() {
        return sellerReceives().compareTo(buyerPays()) > 0;
    }
}
