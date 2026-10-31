package fr.maxlego08.zauctionhouse.tax;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.tax.ItemTaxRule;
import fr.maxlego08.zauctionhouse.api.tax.TaxAmountType;
import fr.maxlego08.zauctionhouse.api.tax.TaxConfiguration;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.economy.ZAuctionEconomy;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import fr.traqueur.currencies.Currencies;
import fr.traqueur.currencies.CurrencyProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZPurchaseChargeTest {

    private static final String BYPASS = "zauctionhouse.tax.bypass";
    private static final BigDecimal PRICE = new BigDecimal("100");

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private Player buyer;
    private UUID seller;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.buyer = ConfigTestSupport.playerWithPermissions("Buyer");
        this.seller = UUID.randomUUID();
    }

    // ---------------------------------------------------------------- helpers

    private ZAuctionEconomy economy(TaxConfiguration taxConfiguration) {
        CurrencyProvider provider = mock(CurrencyProvider.class);
        return new ZAuctionEconomy(this.plugin, provider, Currencies.VAULT, "vault", "Vault", "%price%$", "$", null,
                "deposit", "withdraw", PriceFormat.PRICE_RAW, new EnumMap<>(ItemType.class), new EnumMap<>(ItemType.class),
                true, false, taxConfiguration);
    }

    private static ZTaxConfiguration tax(TaxType type, double percentage) {
        return new ZTaxConfiguration(true, type, TaxAmountType.PERCENTAGE, percentage, BYPASS, List.of(), false, List.of());
    }

    private static ZTaxConfiguration taxWithRules(TaxType type, double percentage, ItemTaxRule... rules) {
        return new ZTaxConfiguration(true, type, TaxAmountType.PERCENTAGE, percentage, BYPASS, List.of(), true, List.of(rules));
    }

    /**
     * Regle d'item mockee qui matche uniquement les ItemStack du materiau donne.
     */
    private static ItemTaxRule materialRule(Material material, int priority, TaxType type, double percentage) {
        ItemTaxRule rule = mock(ItemTaxRule.class);
        lenient().when(rule.getName()).thenReturn(material.name().toLowerCase());
        lenient().when(rule.getPriority()).thenReturn(priority);
        lenient().when(rule.getTaxType()).thenReturn(type);
        lenient().when(rule.getAmountType()).thenReturn(TaxAmountType.PERCENTAGE);
        lenient().when(rule.getAmount()).thenReturn(percentage);
        lenient().when(rule.matches(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack itemStack = invocation.getArgument(0);
            return itemStack != null && itemStack.getType() == material;
        });
        return rule;
    }

    private ZAuctionItem listing(AuctionEconomy economy, List<ItemStack> itemStacks) {
        return TestItems.auctionItem(this.plugin, 42, this.seller, "Seller", PRICE, economy, System.currentTimeMillis() + 60_000L, itemStacks);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "attendu " + expected + " mais obtenu " + actual);
    }

    // ---------------------------------------------------------------- tests

    @Test
    @DisplayName("taxe desactivee : l'acheteur paie le prix, le vendeur touche le prix")
    void resolve_taxDisabled_buyerAndSellerAtPrice() {
        ZAuctionEconomy economy = economy(ZTaxConfiguration.disabled());
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
        assertFalse(charge.taxResult().hasTax());
        assertFalse(charge.taxResult().isBypassed());
    }

    @Test
    @DisplayName("economie SELL sans regle : aucune taxe a l'achat")
    void resolve_sellEconomy_noPurchaseTax() {
        ZAuctionEconomy economy = economy(tax(TaxType.SELL, 10));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
        assertFalse(charge.taxResult().hasTax());
    }

    @Test
    @DisplayName("PURCHASE 10 % : l'acheteur paie 100, le vendeur touche 90")
    void resolve_purchaseTax_sellerReceivesLess() {
        ZAuctionEconomy economy = economy(tax(TaxType.PURCHASE, 10));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("90", charge.sellerReceives());
        assertMoney("10.00", charge.taxResult().taxAmount());
        assertEquals(TaxType.PURCHASE, charge.taxResult().appliedType());
    }

    @Test
    @DisplayName("CAPITALISM 10 % : l'acheteur paie 110, le vendeur touche 100")
    void resolve_capitalismTax_buyerPaysMore() {
        ZAuctionEconomy economy = economy(tax(TaxType.CAPITALISM, 10));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("110", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
        assertEquals(TaxType.CAPITALISM, charge.taxResult().appliedType());
    }

    @Test
    @DisplayName("BOTH 10 % : a l'achat, le vendeur touche 90")
    void resolve_bothTax_sellerReceivesLess() {
        ZAuctionEconomy economy = economy(tax(TaxType.BOTH, 10));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("90", charge.sellerReceives());
    }

    @Test
    @DisplayName("C-019 : economie SELL mais regle PURCHASE sur le diamant : la regle s'applique a l'achat")
    void resolve_itemRuleOnSellEconomy_appliesOnPurchase() {
        ZAuctionEconomy economy = economy(taxWithRules(TaxType.SELL, 5, materialRule(Material.DIAMOND, 100, TaxType.PURCHASE, 20)));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("80", charge.sellerReceives());
        assertEquals(TaxType.PURCHASE, charge.taxResult().appliedType());
    }

    @Test
    @DisplayName("plusieurs stacks : la taxe la plus elevee l'emporte, quel que soit l'ordre")
    void resolve_multipleStacks_highestTaxWins() {
        ItemTaxRule diamondRule = materialRule(Material.DIAMOND, 100, TaxType.PURCHASE, 20);
        ZAuctionEconomy economy = economy(taxWithRules(TaxType.PURCHASE, 5, diamondRule));

        // Le diamant est en DEUXIEME position : ne regarder que le premier stack le laisserait passer a 5 %.
        ZAuctionItem item = listing(economy, List.of(TestItems.itemStack(Material.DIRT, 64), TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("20.00", charge.taxResult().taxAmount());
        assertMoney("80", charge.sellerReceives());
        assertMoney("100", charge.buyerPays());
    }

    @Test
    @DisplayName("plusieurs stacks : CAPITALISM sur un stack prime sur PURCHASE moins eleve")
    void resolve_multipleStacks_capitalismCandidateChosenByAmount() {
        ItemTaxRule diamondRule = materialRule(Material.DIAMOND, 100, TaxType.CAPITALISM, 30);
        ZAuctionEconomy economy = economy(taxWithRules(TaxType.PURCHASE, 10, diamondRule));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1), TestItems.itemStack(Material.DIRT, 1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("130", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
        assertEquals(TaxType.CAPITALISM, charge.taxResult().appliedType());
    }

    @Test
    @DisplayName("item sans contenu (pas un AuctionItem) : seule la configuration par defaut est evaluee")
    void resolve_plainItem_usesDefaultConfigurationOnly() {
        ItemTaxRule diamondRule = materialRule(Material.DIAMOND, 100, TaxType.PURCHASE, 20);
        ZAuctionEconomy economy = economy(taxWithRules(TaxType.PURCHASE, 5, diamondRule));

        Item item = mock(Item.class);
        when(item.getPrice()).thenReturn(PRICE);
        when(item.getId()).thenReturn(7);

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("5.00", charge.taxResult().taxAmount());
        assertMoney("95", charge.sellerReceives());
        verify(diamondRule, never()).matches(any(ItemStack.class));
    }

    @Test
    @DisplayName("AuctionItem avec liste de stacks vide : configuration par defaut, pas d'exception")
    void resolve_emptyStacks_usesDefaultConfiguration() {
        ZAuctionEconomy economy = economy(tax(TaxType.PURCHASE, 5));
        ZAuctionItem item = listing(economy, List.of());

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("95", charge.sellerReceives());
    }

    @Test
    @DisplayName("AuctionItem avec liste de stacks null : configuration par defaut, pas d'exception")
    void resolve_nullStacks_usesDefaultConfiguration() {
        ZAuctionEconomy economy = economy(tax(TaxType.PURCHASE, 5));
        ZAuctionItem item = listing(economy, null);

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("95", charge.sellerReceives());
    }

    @Test
    @DisplayName("acheteur avec bypass : montant inchange et exoneration conservee dans le resultat")
    void resolve_bypassedBuyer_keepsBypassFlag() {
        Player vip = ConfigTestSupport.playerWithPermissions("Vip", BYPASS);
        ZAuctionEconomy economy = economy(tax(TaxType.CAPITALISM, 10));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(vip, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
        assertTrue(charge.taxResult().isBypassed(), "le message TAX_EXEMPT doit pouvoir etre affiche");
    }

    @Test
    @DisplayName("taxe nulle sans bypass : le resultat initial (disabled) est conserve")
    void resolve_zeroTaxNoBypass_keepsDisabledResult() {
        ZAuctionEconomy economy = economy(tax(TaxType.PURCHASE, 0));
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertFalse(charge.taxResult().hasTax());
        assertFalse(charge.taxResult().isBypassed());
        assertMoney("100", charge.buyerPays());
    }

    @Test
    @DisplayName("configuration de taxe null sur l'economie : traitee comme desactivee")
    void resolve_nullTaxConfiguration_treatedAsDisabled() {
        ZAuctionEconomy economy = economy(null);
        ZAuctionItem item = listing(economy, List.of(TestItems.diamond(1)));

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
    }

    @Test
    @DisplayName("economie dont getTaxConfiguration rend null : aucune taxe, aucune exception")
    void resolve_economyReturningNullTaxConfiguration_noTax() {
        AuctionEconomy economy = mock(AuctionEconomy.class);
        when(economy.getName()).thenReturn("weird");
        when(economy.getTaxConfiguration()).thenReturn(null);
        Item item = mock(Item.class);
        when(item.getPrice()).thenReturn(PRICE);

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
        verify(economy, never()).calculatePurchaseTax(any(), any(), any());
    }

    @Test
    @DisplayName("garde-fou : une configuration qui creerait de la monnaie fait echouer l'achat")
    void resolve_mintingConfiguration_throws() {
        TaxConfiguration taxConfiguration = mock(TaxConfiguration.class);
        when(taxConfiguration.isEnabled()).thenReturn(true);
        // Resultat incoherent : vendeur credite de 110 alors que l'acheteur ne paie que 100.
        TaxResult minting = new TaxResult(BigDecimal.TEN, 10, PRICE, new BigDecimal("110"), false, false, 0, null);
        AuctionEconomy economy = mock(AuctionEconomy.class);
        when(economy.getName()).thenReturn("broken");
        when(economy.getTaxConfiguration()).thenReturn(taxConfiguration);
        when(economy.calculatePurchaseTax(any(), any(), any())).thenReturn(minting);
        Item item = mock(Item.class);
        when(item.getPrice()).thenReturn(PRICE);
        when(item.getId()).thenReturn(99);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> ZPurchaseCharge.resolve(buyer, item, economy));

        assertTrue(exception.getMessage().contains("broken"));
        assertTrue(exception.getMessage().contains("99"));
    }

    @Test
    @DisplayName("un candidat null rendu par l'economie est ignore")
    void resolve_nullCandidate_ignored() {
        TaxConfiguration taxConfiguration = mock(TaxConfiguration.class);
        when(taxConfiguration.isEnabled()).thenReturn(true);
        AuctionEconomy economy = mock(AuctionEconomy.class);
        when(economy.getName()).thenReturn("nullish");
        when(economy.getTaxConfiguration()).thenReturn(taxConfiguration);
        when(economy.calculatePurchaseTax(any(), any(), any())).thenReturn(null);
        Item item = mock(Item.class);
        when(item.getPrice()).thenReturn(PRICE);

        ZPurchaseCharge charge = ZPurchaseCharge.resolve(buyer, item, economy);

        assertMoney("100", charge.buyerPays());
        assertMoney("100", charge.sellerReceives());
    }

    @Test
    @DisplayName("le record expose le TaxResult qui a produit les montants")
    void record_exposesTaxResult() {
        TaxResult result = TaxResult.disabled(PRICE);
        ZPurchaseCharge charge = new ZPurchaseCharge(PRICE, PRICE, result);

        assertSame(result, charge.taxResult());
        assertSame(PRICE, charge.buyerPays());
        assertSame(PRICE, charge.sellerReceives());
    }
}
