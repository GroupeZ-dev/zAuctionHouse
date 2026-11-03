package fr.maxlego08.zauctionhouse.tax;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.tax.ItemTaxRule;
import fr.maxlego08.zauctionhouse.api.tax.TaxAmountType;
import fr.maxlego08.zauctionhouse.api.tax.TaxReduction;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ZTaxConfiguration} : calcul de la taxe de vente et d'achat, exemption,
 * reductions, regles par item et semantique CAPITALISM.
 * <p>
 * Les regles par item sont des doublures de {@link Rule} : la mise en correspondance d'un
 * ItemStack n'est pas l'objet de ces tests.
 */
class ZTaxConfigurationTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Player player;
    private ItemStack diamond;

    @BeforeEach
    void setUp() {
        this.player = MockPlugins.player("Taxed");
        this.diamond = TestItems.diamond(1);
    }

    private static ZTaxConfiguration config(TaxType taxType, TaxAmountType amountType, double amount) {
        return new ZTaxConfiguration(true, taxType, amountType, amount, null, List.of(), false, List.of());
    }

    private static ItemTaxRule rule(String name, int priority, TaxType taxType, TaxAmountType amountType, double amount, boolean matches) {
        Rule matcher = mock(Rule.class);
        when(matcher.matches(any())).thenReturn(matches);
        return new ZItemTaxRule(name, priority, taxType, amountType, amount, matcher);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "attendu " + expected + ", obtenu " + actual);
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("configuration desactivee")
    class Disabled {

        @Test
        @DisplayName("disabled() ne taxe ni la vente ni l'achat et conserve le prix")
        void disabled_appliesNoTax() {
            ZTaxConfiguration config = ZTaxConfiguration.disabled();

            assertFalse(config.isEnabled());
            assertFalse(config.hasItemRules());
            TaxResult sell = config.calculateSellTax(player, HUNDRED, diamond);
            TaxResult purchase = config.calculatePurchaseTax(player, HUNDRED, diamond);

            assertFalse(sell.hasTax());
            assertFalse(sell.isBypassed());
            assertMoney("100", sell.finalPrice());
            assertFalse(purchase.hasTax());
            assertMoney("100", purchase.buyerPays());
            assertMoney("100", purchase.sellerReceives());
        }

        @Test
        @DisplayName("enabled=false avec un montant configure : aucune taxe")
        void disabledFlag_overridesAmount() {
            ZTaxConfiguration config = new ZTaxConfiguration(false, TaxType.BOTH, TaxAmountType.PERCENTAGE, 50, null, List.of(), false, List.of());

            assertFalse(config.calculateSellTax(player, HUNDRED, diamond).hasTax());
            assertFalse(config.calculatePurchaseTax(player, HUNDRED, diamond).hasTax());
        }
    }

    @Nested
    @DisplayName("taxe de vente")
    class SellTax {

        @Test
        @DisplayName("10 % sur 100 : taxe 10.00, vendeur paie 10 a la mise en vente, type SELL")
        void percentage_computesTax() {
            TaxResult result = config(TaxType.SELL, TaxAmountType.PERCENTAGE, 10).calculateSellTax(player, HUNDRED, diamond);

            assertTrue(result.hasTax());
            assertEquals(new BigDecimal("10.00"), result.taxAmount());
            assertEquals(10.0, result.taxPercentage());
            assertMoney("100", result.originalPrice());
            assertMoney("90", result.finalPrice());
            assertFalse(result.isBypassed());
            assertFalse(result.isReduced());
            assertEquals(0.0, result.reductionPercentage());
            assertEquals(TaxType.SELL, result.appliedType());
        }

        @Test
        @DisplayName("montant fixe 5 sur 100 : taxe 5.00 et pourcentage affiche 5.0")
        void fixed_computesTaxAndDisplayPercentage() {
            TaxResult result = config(TaxType.SELL, TaxAmountType.FIXED, 5).calculateSellTax(player, HUNDRED, diamond);

            assertEquals(new BigDecimal("5.00"), result.taxAmount());
            assertEquals(5.0, result.taxPercentage());
            assertMoney("95", result.finalPrice());
        }

        @Test
        @DisplayName("montant fixe sur un prix nul : pourcentage 0 (pas de division par zero)")
        void fixed_onZeroPrice_hasZeroPercentage() {
            TaxResult result = config(TaxType.SELL, TaxAmountType.FIXED, 5).calculateSellTax(player, BigDecimal.ZERO, diamond);

            assertEquals(new BigDecimal("5.00"), result.taxAmount());
            assertEquals(0.0, result.taxPercentage());
            assertMoney("-5", result.finalPrice());
        }

        @Test
        @DisplayName("la taxe est arrondie a 2 decimales (HALF_UP)")
        void tax_isRoundedToTwoDecimals() {
            TaxResult result = config(TaxType.SELL, TaxAmountType.PERCENTAGE, 33.333).calculateSellTax(player, BigDecimal.TEN, diamond);

            assertEquals(new BigDecimal("3.33"), result.taxAmount());
            assertEquals(new BigDecimal("6.67"), result.finalPrice());
            assertEquals(2, result.taxAmount().scale());
        }

        @Test
        @DisplayName("0 % : resultat sans taxe mais non desactive")
        void zeroPercent_hasNoTax() {
            TaxResult result = config(TaxType.SELL, TaxAmountType.PERCENTAGE, 0).calculateSellTax(player, HUNDRED, diamond);

            assertFalse(result.hasTax());
            assertEquals(new BigDecimal("0.00"), result.taxAmount());
            assertMoney("100", result.finalPrice());
        }

        @ParameterizedTest(name = "type {0}")
        @EnumSource(value = TaxType.class, names = {"PURCHASE", "CAPITALISM"})
        @DisplayName("un type qui ne concerne pas la vente ne taxe pas la mise en vente")
        void nonSellType_appliesNoSellTax(TaxType taxType) {
            TaxResult result = config(taxType, TaxAmountType.PERCENTAGE, 10).calculateSellTax(player, HUNDRED, diamond);

            assertFalse(result.hasTax());
            assertMoney("100", result.finalPrice());
        }

        @Test
        @DisplayName("BOTH taxe la mise en vente")
        void both_appliesSellTax() {
            TaxResult result = config(TaxType.BOTH, TaxAmountType.PERCENTAGE, 10).calculateSellTax(player, HUNDRED, diamond);

            assertEquals(new BigDecimal("10.00"), result.taxAmount());
            assertEquals(TaxType.BOTH, result.appliedType());
        }
    }

    @Nested
    @DisplayName("taxe d'achat")
    class PurchaseTax {

        @Test
        @DisplayName("PURCHASE 10 % : l'acheteur paie 100, le vendeur touche 90")
        void purchase_sellerReceivesLess() {
            TaxResult result = config(TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 10).calculatePurchaseTax(player, HUNDRED, diamond);

            assertEquals(new BigDecimal("10.00"), result.taxAmount());
            assertMoney("100", result.buyerPays());
            assertMoney("90", result.sellerReceives());
            assertMoney("90", result.finalPrice());
            assertEquals(TaxType.PURCHASE, result.appliedType());
            assertFalse(result.mintsMoney());
        }

        @Test
        @DisplayName("CAPITALISM 10 % : l'acheteur paie 110, le vendeur touche 100, aucune creation de monnaie")
        void capitalism_buyerPaysMore() {
            TaxResult result = config(TaxType.CAPITALISM, TaxAmountType.PERCENTAGE, 10).calculatePurchaseTax(player, HUNDRED, diamond);

            assertEquals(new BigDecimal("10.00"), result.taxAmount());
            assertMoney("110", result.buyerPays());
            assertMoney("100", result.sellerReceives());
            assertMoney("110", result.finalPrice());
            assertEquals(TaxType.CAPITALISM, result.appliedType());
            assertFalse(result.mintsMoney());
        }

        @Test
        @DisplayName("BOTH taxe aussi l'achat (vendeur touche moins)")
        void both_appliesPurchaseTax() {
            TaxResult result = config(TaxType.BOTH, TaxAmountType.FIXED, 7).calculatePurchaseTax(player, HUNDRED, diamond);

            assertEquals(new BigDecimal("7.00"), result.taxAmount());
            assertMoney("93", result.sellerReceives());
            assertMoney("100", result.buyerPays());
            assertEquals(TaxType.BOTH, result.appliedType());
        }

        @Test
        @DisplayName("SELL ne taxe pas l'achat")
        void sellType_appliesNoPurchaseTax() {
            TaxResult result = config(TaxType.SELL, TaxAmountType.PERCENTAGE, 10).calculatePurchaseTax(player, HUNDRED, diamond);

            assertFalse(result.hasTax());
            assertMoney("100", result.buyerPays());
            assertMoney("100", result.sellerReceives());
        }

        @Test
        @DisplayName("exemption a l'achat : resultat bypassed sans taxe")
        void purchase_bypassed() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 10, "zah.tax.bypass", List.of(), false, List.of());
            when(player.hasPermission("zah.tax.bypass")).thenReturn(true);

            TaxResult result = config.calculatePurchaseTax(player, HUNDRED, diamond);

            assertTrue(result.isBypassed());
            assertFalse(result.hasTax());
            assertMoney("100", result.finalPrice());
        }
    }

    @Nested
    @DisplayName("exemption et reductions")
    class BypassAndReductions {

        @Test
        @DisplayName("canBypass est faux sans permission configuree (null ou vide)")
        void canBypass_withoutPermission_isFalse() {
            when(player.hasPermission(any(String.class))).thenReturn(true);

            assertFalse(new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, null, List.of(), false, List.of()).canBypass(player));
            assertFalse(new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, "", List.of(), false, List.of()).canBypass(player));
        }

        @Test
        @DisplayName("joueur avec la permission de bypass : resultat bypassed, prix inchange")
        void bypass_appliesNoTax() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, "zah.tax.bypass", List.of(), false, List.of());
            when(player.hasPermission("zah.tax.bypass")).thenReturn(true);

            assertTrue(config.canBypass(player));
            TaxResult result = config.calculateSellTax(player, HUNDRED, diamond);
            assertTrue(result.isBypassed());
            assertFalse(result.hasTax());
            assertMoney("100", result.finalPrice());
            assertNull(result.appliedType());
        }

        @Test
        @DisplayName("sans la permission de bypass, la taxe s'applique normalement")
        void noBypassPermission_appliesTax() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, "zah.tax.bypass", List.of(), false, List.of());

            assertFalse(config.canBypass(player));
            assertEquals(new BigDecimal("10.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());
        }

        @Test
        @DisplayName("reduction de 25 % : taxe 7.50, pourcentage effectif 7.5, drapeau isReduced")
        void reduction_lowersTax() {
            TaxReduction vip = new TaxReduction("zah.tax.vip", 25);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, null, List.of(vip), false, List.of());
            when(player.hasPermission("zah.tax.vip")).thenReturn(true);

            assertSame(vip, config.getReduction(player));
            TaxResult result = config.calculateSellTax(player, HUNDRED, diamond);
            assertTrue(result.isReduced());
            assertEquals(25.0, result.reductionPercentage());
            assertEquals(new BigDecimal("7.50"), result.taxAmount());
            assertEquals(7.5, result.taxPercentage(), 1e-9);
            assertMoney("92.50", result.finalPrice());
        }

        @Test
        @DisplayName("la premiere reduction dont le joueur a la permission gagne, dans l'ordre de la liste")
        void reduction_firstMatchingWins() {
            TaxReduction premium = new TaxReduction("zah.tax.premium", 50);
            TaxReduction vip = new TaxReduction("zah.tax.vip", 25);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, null, List.of(premium, vip), false, List.of());
            when(player.hasPermission("zah.tax.premium")).thenReturn(true);
            when(player.hasPermission("zah.tax.vip")).thenReturn(true);

            assertSame(premium, config.getReduction(player));
            assertEquals(new BigDecimal("5.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());
        }

        @Test
        @DisplayName("sans permission de reduction : getReduction rend null et la taxe est pleine")
        void reduction_noPermission_isNull() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, null, List.of(new TaxReduction("zah.tax.vip", 25)), false, List.of());

            assertNull(config.getReduction(player));
            TaxResult result = config.calculateSellTax(player, HUNDRED, diamond);
            assertFalse(result.isReduced());
            assertEquals(new BigDecimal("10.00"), result.taxAmount());
        }

        @Test
        @DisplayName("reduction de 100 % : taxe 0.00, resultat sans taxe mais reduit")
        void reduction_full_leavesNoTax() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, null, List.of(new TaxReduction("zah.tax.free", 100)), false, List.of());
            when(player.hasPermission("zah.tax.free")).thenReturn(true);

            TaxResult result = config.calculateSellTax(player, HUNDRED, diamond);

            assertFalse(result.hasTax());
            assertTrue(result.isReduced());
            assertEquals(new BigDecimal("0.00"), result.taxAmount());
        }

        @Test
        @DisplayName("le bypass prime sur la reduction")
        void bypass_beatsReduction() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, "zah.tax.bypass", List.of(new TaxReduction("zah.tax.vip", 25)), false, List.of());
            when(player.hasPermission("zah.tax.bypass")).thenReturn(true);
            when(player.hasPermission("zah.tax.vip")).thenReturn(true);

            TaxResult result = config.calculateSellTax(player, HUNDRED, diamond);

            assertTrue(result.isBypassed());
            assertFalse(result.isReduced());
        }

        @Test
        @DisplayName("une liste de reductions nulle est toleree")
        void nullReductions_areTolerated() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, null, null, false, null);

            assertTrue(config.getReductions().isEmpty());
            assertTrue(config.getItemRules().isEmpty());
            assertNull(config.getReduction(player));
            assertEquals(new BigDecimal("10.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());
        }
    }

    @Nested
    @DisplayName("regles par item")
    class ItemRules {

        @Test
        @DisplayName("une regle qui matche remplace le taux par defaut")
        void matchingRule_overridesDefault() {
            ItemTaxRule rule = rule("gold", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            assertTrue(config.hasItemRules());
            TaxResult result = config.calculateSellTax(player, HUNDRED, diamond);
            assertEquals(new BigDecimal("20.00"), result.taxAmount());
            assertEquals(TaxType.SELL, result.appliedType());
        }

        @Test
        @DisplayName("sans regle qui matche, le taux par defaut s'applique")
        void noMatchingRule_usesDefault() {
            ItemTaxRule rule = rule("gold", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, false);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            assertEquals(new BigDecimal("5.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());
        }

        @Test
        @DisplayName("les regles sont triees par priorite decroissante et la premiere qui matche gagne")
        void rules_areSortedByPriorityDescending() {
            ItemTaxRule low = rule("low", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, true);
            ItemTaxRule high = rule("high", 10, TaxType.SELL, TaxAmountType.PERCENTAGE, 30, true);
            ItemTaxRule middle = rule("middle", 5, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(low, high, middle));

            assertEquals(List.of("high", "middle", "low"), config.getItemRules().stream().map(ItemTaxRule::getName).toList());
            assertEquals(new BigDecimal("30.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());
        }

        @Test
        @DisplayName("regles desactivees (itemRulesEnabled=false) : ignorees meme si elles matchent")
        void rulesDisabled_areIgnored() {
            ItemTaxRule rule = rule("gold", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), false, List.of(rule));

            assertFalse(config.hasItemRules());
            assertEquals(new BigDecimal("5.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());
        }

        @Test
        @DisplayName("regles activees mais liste vide : hasItemRules est faux")
        void rulesEnabledButEmpty_hasNoRules() {
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of());

            assertFalse(config.hasItemRules());
        }

        @Test
        @DisplayName("ItemStack null : les regles sont sautees, le defaut s'applique")
        void nullItemStack_skipsRules() {
            ItemTaxRule rule = rule("gold", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            assertEquals(new BigDecimal("5.00"), config.calculateSellTax(player, HUNDRED, null).taxAmount());
        }

        @Test
        @DisplayName("regle PURCHASE sur une vente : le defaut SELL s'applique quand il existe")
        void purchaseRuleOnSell_fallsBackToSellDefault() {
            ItemTaxRule rule = rule("gold", 1, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            TaxResult result = config.calculateSellTax(player, HUNDRED, diamond);
            assertEquals(new BigDecimal("5.00"), result.taxAmount());
            assertEquals(TaxType.SELL, result.appliedType());
        }

        @Test
        @DisplayName("regle PURCHASE sur une vente avec un defaut PURCHASE : aucune taxe de vente")
        void purchaseRuleOnSell_withPurchaseDefault_appliesNothing() {
            ItemTaxRule rule = rule("gold", 1, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            assertFalse(config.calculateSellTax(player, HUNDRED, diamond).hasTax());
        }

        @Test
        @DisplayName("regle CAPITALISM sur un achat : l'acheteur paie le prix + la taxe de la regle")
        void capitalismRuleOnPurchase_buyerPaysRuleTax() {
            ItemTaxRule rule = rule("luxury", 1, TaxType.CAPITALISM, TaxAmountType.PERCENTAGE, 50, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            TaxResult result = config.calculatePurchaseTax(player, HUNDRED, diamond);
            assertEquals(new BigDecimal("50.00"), result.taxAmount());
            assertMoney("150", result.buyerPays());
            assertMoney("100", result.sellerReceives());
            assertEquals(TaxType.CAPITALISM, result.appliedType());
        }

        @Test
        @DisplayName("regle SELL sur un achat avec un defaut SELL : aucune taxe d'achat")
        void sellRuleOnPurchase_withSellDefault_appliesNothing() {
            ItemTaxRule rule = rule("gold", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            assertFalse(config.calculatePurchaseTax(player, HUNDRED, diamond).hasTax());
        }

        @Test
        @DisplayName("regle SELL sur un achat avec un defaut PURCHASE : le defaut s'applique")
        void sellRuleOnPurchase_withPurchaseDefault_usesDefault() {
            ItemTaxRule rule = rule("gold", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.PURCHASE, TaxAmountType.PERCENTAGE, 5, null, List.of(), true, List.of(rule));

            TaxResult result = config.calculatePurchaseTax(player, HUNDRED, diamond);
            assertEquals(new BigDecimal("5.00"), result.taxAmount());
            assertEquals(TaxType.PURCHASE, result.appliedType());
        }

        @Test
        @DisplayName("la regle par item utilise son propre type de montant (FIXED)")
        void rule_usesItsOwnAmountType() {
            ItemTaxRule rule = rule("flat", 1, TaxType.SELL, TaxAmountType.FIXED, 3, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 50, null, List.of(), true, List.of(rule));

            assertEquals(new BigDecimal("3.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());
        }

        @Test
        @DisplayName("exemption et reduction s'appliquent aussi aux taux des regles")
        void rule_respectsBypassAndReduction() {
            ItemTaxRule rule = rule("gold", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 20, true);
            ZTaxConfiguration config = new ZTaxConfiguration(true, TaxType.SELL, TaxAmountType.PERCENTAGE, 5, "zah.tax.bypass", List.of(new TaxReduction("zah.tax.vip", 50)), true, List.of(rule));

            when(player.hasPermission("zah.tax.vip")).thenReturn(true);
            assertEquals(new BigDecimal("10.00"), config.calculateSellTax(player, HUNDRED, diamond).taxAmount());

            when(player.hasPermission("zah.tax.bypass")).thenReturn(true);
            assertTrue(config.calculateSellTax(player, HUNDRED, diamond).isBypassed());
        }
    }

    @Nested
    @DisplayName("accesseurs")
    class Accessors {

        @ParameterizedTest(name = "{0} / {1} / {2}")
        @CsvSource({
                "SELL, PERCENTAGE, 10",
                "PURCHASE, FIXED, 2.5",
                "BOTH, PERCENTAGE, 0",
                "CAPITALISM, PERCENTAGE, 99.9"
        })
        @DisplayName("les accesseurs rendent les valeurs du constructeur")
        void accessors_returnConstructorValues(TaxType taxType, TaxAmountType amountType, double amount) {
            TaxReduction reduction = new TaxReduction("p", 10);
            ZTaxConfiguration config = new ZTaxConfiguration(true, taxType, amountType, amount, "bypass", List.of(reduction), false, Collections.emptyList());

            assertTrue(config.isEnabled());
            assertEquals(taxType, config.getTaxType());
            assertEquals(amountType, config.getAmountType());
            assertEquals(amount, config.getAmount());
            assertEquals("bypass", config.getBypassPermission());
            assertEquals(List.of(reduction), config.getReductions());
        }

        @Test
        @DisplayName("TaxReduction.getMultiplier vaut 1 - pourcentage / 100")
        void reduction_multiplier() {
            assertEquals(0.75, new TaxReduction("p", 25).getMultiplier(), 1e-9);
            assertEquals(1.0, new TaxReduction("p", 0).getMultiplier(), 1e-9);
            assertEquals(0.0, new TaxReduction("p", 100).getMultiplier(), 1e-9);
        }

        @Test
        @DisplayName("les materiaux des ItemStack ne sont pas lus par la configuration elle-meme")
        void configuration_doesNotTouchMaterialRegistry() {
            // Garde-fou du montage : un ItemStack d'un autre materiau donne le meme resultat sans regle.
            ItemStack stone = TestItems.itemStack(Material.STONE, 1);
            ZTaxConfiguration config = config(TaxType.SELL, TaxAmountType.PERCENTAGE, 10);

            assertEquals(config.calculateSellTax(player, HUNDRED, diamond).taxAmount(), config.calculateSellTax(player, HUNDRED, stone).taxAmount());
        }
    }
}
