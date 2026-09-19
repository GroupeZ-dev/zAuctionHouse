package fr.maxlego08.zauctionhouse.tax;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Montant unique d'un achat : ce que paie l'acheteur, ce que touche le vendeur, et le
 * {@link TaxResult} qui les a produits.
 * <p>
 * Ce type existe pour qu'il n'y ait plus qu'UN SEUL calcul de taxe a l'achat. Auparavant
 * {@code PurchaseService} calculait le solde exige avec un ItemStack {@code null} (donc en
 * ignorant les regles de taxe par item, cf. ZTaxConfiguration l.128) pendant que
 * {@code ZAuctionManager} prelevait avec l'ItemStack reel : le montant verifie pouvait etre
 * strictement inferieur au montant preleve.
 *
 * @param buyerPays      le montant reellement debite a l'acheteur
 * @param sellerReceives le montant reellement credite au vendeur
 * @param taxResult      le resultat de taxe qui a produit les deux montants
 */
public record ZPurchaseCharge(BigDecimal buyerPays, BigDecimal sellerReceives, TaxResult taxResult) {

    /**
     * Calcule le montant d'un achat.
     * <p>
     * La regle de selection du stack representatif est la MEME que celle du chemin de vente
     * ({@code SellService.applySellTaxAsync}) : on retient la taxe la plus elevee parmi tous
     * les stacks de l'annonce. Ne regarder que le premier stack permettrait de contourner une
     * regle par item en la rangeant en deuxieme position.
     *
     * @param player         l'acheteur
     * @param item           l'annonce
     * @param auctionEconomy l'economie de l'annonce
     * @return le montant a verifier ET a prelever
     * @throws IllegalStateException si la configuration de taxe creerait de la monnaie
     */
    public static ZPurchaseCharge resolve(Player player, Item item, AuctionEconomy auctionEconomy) {

        BigDecimal price = item.getPrice();
        var taxConfiguration = auctionEconomy.getTaxConfiguration();

        TaxResult taxResult = TaxResult.disabled(price);

        // C-019 : ne PAS pre-filtrer sur taxConfiguration.getTaxType(). Une regle par item peut
        // porter un type d'achat alors que l'economie est declaree en SELL ; c'est
        // calculatePurchaseTax qui tranche, et lui seul.
        if (taxConfiguration != null && taxConfiguration.isEnabled()) {
            for (ItemStack itemStack : representativeStacks(item)) {
                TaxResult candidate = auctionEconomy.calculatePurchaseTax(player, price, itemStack);
                if (isBetterCandidate(candidate, taxResult)) {
                    taxResult = candidate;
                }
            }
        }

        BigDecimal buyerPays = taxResult.buyerPays();
        BigDecimal sellerReceives = taxResult.sellerReceives();

        // Garde-fou anti-frappe monetaire. Aucun mouvement d'argent ne doit avoir lieu si la
        // configuration ferait toucher au vendeur plus que ce que l'acheteur paie.
        if (taxResult.mintsMoney()) {
            throw new IllegalStateException("Tax configuration of economy '" + auctionEconomy.getName()
                    + "' would mint money on item " + item.getId() + ": buyer pays " + buyerPays
                    + " but seller would receive " + sellerReceives + ". Purchase aborted.");
        }

        return new ZPurchaseCharge(buyerPays, sellerReceives, taxResult);
    }

    /**
     * Retourne les stacks a soumettre aux regles par item. Une liste contenant un seul
     * {@code null} force une evaluation unique sur la configuration par defaut, ce qui est le
     * comportement attendu pour un item sans contenu.
     */
    private static List<ItemStack> representativeStacks(Item item) {
        if (item instanceof AuctionItem auctionItem) {
            var itemStacks = auctionItem.getItemStacks();
            if (itemStacks != null && !itemStacks.isEmpty()) return itemStacks;
        }
        return Collections.singletonList(null);
    }

    private static boolean isBetterCandidate(TaxResult candidate, TaxResult current) {
        if (candidate == null) return false;
        int comparison = candidate.taxAmount().compareTo(current.taxAmount());
        if (comparison > 0) return true;
        // A taxe egale (typiquement zero), preferer le resultat qui porte l'exoneration pour
        // que le message TAX_EXEMPT reste affiche a l'acheteur.
        return comparison == 0 && candidate.isBypassed() && !current.isBypassed();
    }
}
