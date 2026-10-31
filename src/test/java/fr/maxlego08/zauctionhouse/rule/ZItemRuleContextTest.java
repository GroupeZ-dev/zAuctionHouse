package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ZItemRuleContextTest {

    @Test
    @DisplayName("item sans meta : materiau lu, aucun nom, lore vide, model data 0 et absent")
    void plainItem_exposesDefaults() {
        ItemStack itemStack = RuleTestItems.plain(Material.DIAMOND);

        ZItemRuleContext context = new ZItemRuleContext(itemStack);

        assertSame(itemStack, context.getItemStack());
        assertEquals(Material.DIAMOND, context.getMaterial());
        assertFalse(context.hasDisplayName());
        assertNull(context.getDisplayName());
        assertFalse(context.hasLore());
        assertTrue(context.getLore().isEmpty());
        assertFalse(context.hasCustomModelData());
        assertEquals(0, context.getCustomModelData());
    }

    @Test
    @DisplayName("nom d'affichage : lu en texte brut (couleurs et decorations retirees)")
    void displayName_isPlainText() {
        Component colored = Component.text("Epic ").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text("Sword").color(NamedTextColor.RED));
        ItemStack itemStack = RuleTestItems.itemWithComponents(Material.DIAMOND_SWORD, colored, null, null, null);

        ZItemRuleContext context = new ZItemRuleContext(itemStack);

        assertTrue(context.hasDisplayName());
        assertEquals("Epic Sword", context.getDisplayName());
    }

    @Test
    @DisplayName("nom d'affichage : la casse est conservee (le contexte ne normalise pas)")
    void displayName_preservesCase() {
        ZItemRuleContext context = new ZItemRuleContext(RuleTestItems.named(Material.PAPER, "VoUcHeR"));

        assertEquals("VoUcHeR", context.getDisplayName());
    }

    @Test
    @DisplayName("meta presente mais sans nom : hasDisplayName faux et nom null")
    void metaWithoutName_noDisplayName() {
        ZItemRuleContext context = new ZItemRuleContext(RuleTestItems.withLore(Material.PAPER, "just lore"));

        assertFalse(context.hasDisplayName());
        assertNull(context.getDisplayName());
    }

    @Test
    @DisplayName("lore : chaque ligne est conservee dans l'ordre, en texte brut")
    void lore_isPlainTextLineByLine() {
        List<Component> lore = List.of(
                Component.text("Line one").color(NamedTextColor.GRAY),
                Component.text("Line ").append(Component.text("two").color(NamedTextColor.AQUA)),
                Component.empty()
        );
        ItemStack itemStack = RuleTestItems.itemWithComponents(Material.PAPER, null, lore, null, null);

        ZItemRuleContext context = new ZItemRuleContext(itemStack);

        assertTrue(context.hasLore());
        assertEquals(List.of("Line one", "Line two", ""), context.getLore());
    }

    @Test
    @DisplayName("lore vide : hasLore faux et liste vide (pas null)")
    void emptyLore_hasLoreFalse() {
        ZItemRuleContext context = new ZItemRuleContext(RuleTestItems.withLore(Material.PAPER));

        assertFalse(context.hasLore());
        assertEquals(List.of(), context.getLore());
    }

    @Test
    @DisplayName("custom model data : lu quand present, y compris la valeur 0")
    void customModelData_readWhenPresent() {
        ZItemRuleContext withValue = new ZItemRuleContext(RuleTestItems.withModelData(Material.PAPER, 1234));
        ZItemRuleContext withZero = new ZItemRuleContext(RuleTestItems.withModelData(Material.PAPER, 0));

        assertTrue(withValue.hasCustomModelData());
        assertEquals(1234, withValue.getCustomModelData());
        assertTrue(withZero.hasCustomModelData());
        assertEquals(0, withZero.getCustomModelData());
    }

    @Test
    @DisplayName("tout est pre-calcule a la construction : les getters ne retouchent pas l'ItemStack")
    void values_arePrecomputedOnce() {
        ItemStack itemStack = RuleTestItems.item(Material.PAPER, "Name", List.of("Lore"), 7, null);
        ZItemRuleContext context = new ZItemRuleContext(itemStack);
        int typeCallsAfterConstruction = 1;
        verify(itemStack, times(typeCallsAfterConstruction)).getType();

        for (int i = 0; i < 5; i++) {
            context.getMaterial();
            context.getDisplayName();
            context.getLore();
            context.getCustomModelData();
            context.hasDisplayName();
            context.hasLore();
            context.hasCustomModelData();
        }

        verify(itemStack, times(typeCallsAfterConstruction)).getType();
        assertEquals("Name", context.getDisplayName());
        assertEquals(List.of("Lore"), context.getLore());
        assertEquals(7, context.getCustomModelData());
    }

    @Test
    @DisplayName("item complet : toutes les facettes lues ensemble")
    void fullItem_allFacets() {
        ZItemRuleContext context = new ZItemRuleContext(RuleTestItems.item(Material.NETHER_STAR, "Voucher", List.of("Untradeable", "VIP"), 1001, null));

        assertEquals(Material.NETHER_STAR, context.getMaterial());
        assertEquals("Voucher", context.getDisplayName());
        assertEquals(List.of("Untradeable", "VIP"), context.getLore());
        assertEquals(1001, context.getCustomModelData());
        assertTrue(context.hasDisplayName() && context.hasLore() && context.hasCustomModelData());
    }
}
