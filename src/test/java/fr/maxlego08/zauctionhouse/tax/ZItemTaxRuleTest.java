package fr.maxlego08.zauctionhouse.tax;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.tax.TaxAmountType;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialRule;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du record {@link ZItemTaxRule}.
 */
class ZItemTaxRuleTest {

    @Test
    @DisplayName("les accesseurs de l'interface rendent les composantes du record")
    void accessors_mirrorRecordComponents() {
        Rule rule = mock(Rule.class);
        ZItemTaxRule itemRule = new ZItemTaxRule("gold", 7, TaxType.BOTH, TaxAmountType.FIXED, 2.5, rule);

        assertEquals("gold", itemRule.getName());
        assertEquals(7, itemRule.getPriority());
        assertEquals(TaxType.BOTH, itemRule.getTaxType());
        assertEquals(TaxAmountType.FIXED, itemRule.getAmountType());
        assertEquals(2.5, itemRule.getAmount());
        assertSame(rule, itemRule.getRule());
        assertEquals("gold", itemRule.name());
        assertEquals(7, itemRule.priority());
    }

    @Test
    @DisplayName("matches delegue a la regle avec un contexte construit depuis l'ItemStack")
    void matches_delegatesToRuleWithContext() {
        Rule rule = mock(Rule.class);
        when(rule.matches(any())).thenReturn(true);
        ItemStack diamond = TestItems.diamond(3);
        ZItemTaxRule itemRule = new ZItemTaxRule("any", 0, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, rule);

        assertTrue(itemRule.matches(diamond));

        ArgumentCaptor<ItemRuleContext> captor = ArgumentCaptor.forClass(ItemRuleContext.class);
        verify(rule).matches(captor.capture());
        assertSame(diamond, captor.getValue().getItemStack());
        assertEquals(Material.DIAMOND, captor.getValue().getMaterial());
        assertFalse(captor.getValue().hasDisplayName());
        assertFalse(captor.getValue().hasLore());
        assertFalse(captor.getValue().hasCustomModelData());
    }

    @Test
    @DisplayName("matches rend false quand la regle refuse")
    void matches_ruleRefuses_isFalse() {
        Rule rule = mock(Rule.class);
        when(rule.matches(any())).thenReturn(false);
        ZItemTaxRule itemRule = new ZItemTaxRule("any", 0, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, rule);

        assertFalse(itemRule.matches(TestItems.diamond(1)));
    }

    @Test
    @DisplayName("matches rend false sans regle, sans interroger quoi que ce soit")
    void matches_nullRule_isFalse() {
        ZItemTaxRule itemRule = new ZItemTaxRule("none", 0, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, null);

        assertFalse(itemRule.matches(TestItems.diamond(1)));
    }

    @Test
    @DisplayName("matches rend false pour un ItemStack null sans appeler la regle")
    void matches_nullItemStack_isFalse() {
        Rule rule = mock(Rule.class);
        ZItemTaxRule itemRule = new ZItemTaxRule("any", 0, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, rule);

        assertFalse(itemRule.matches(null));
        verify(rule, never()).matches(any());
    }

    @Test
    @DisplayName("avec une vraie MaterialRule, seul le materiau configure matche")
    void matches_withMaterialRule() {
        ZItemTaxRule itemRule = new ZItemTaxRule("diamonds", 1, TaxType.SELL, TaxAmountType.PERCENTAGE, 10, new MaterialRule(Set.of(Material.DIAMOND)));

        assertTrue(itemRule.matches(TestItems.diamond(1)));
        assertFalse(itemRule.matches(TestItems.itemStack(Material.STONE, 1)));
    }
}
