package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.rule.ZItemRuleContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Fabrique d'ItemStack mockes pour les tests de regles et de categories.
 * <p>
 * {@code ZItemRuleContext} lit, via {@code PaperComponent} : {@code itemStack.getType()},
 * {@code itemStack.hasItemMeta()}, {@code itemStack.getItemMeta()}, puis sur l'ItemMeta :
 * {@code hasDisplayName()}, {@code displayName()} (Adventure), {@code hasLore()}, {@code lore()}
 * (Adventure), {@code hasCustomModelData()}, {@code getCustomModelData()}. {@code ItemModelRule}
 * lit en plus {@code getItemModel()}. Ce sont exactement ces methodes qui sont stubbees ici.
 */
public final class RuleTestItems {

    private RuleTestItems() {
    }

    /**
     * Item nu : aucune meta ({@code hasItemMeta() == false}, {@code getItemMeta() == null}).
     */
    public static ItemStack plain(Material material) {
        return TestItems.itemStack(material, 1);
    }

    public static ItemStack named(Material material, String displayName) {
        return item(material, displayName, null, null, null);
    }

    public static ItemStack withLore(Material material, String... loreLines) {
        return item(material, null, Arrays.asList(loreLines), null, null);
    }

    public static ItemStack withModelData(Material material, int customModelData) {
        return item(material, null, null, customModelData, null);
    }

    public static ItemStack withItemModel(Material material, NamespacedKey itemModel) {
        return item(material, null, null, null, itemModel);
    }

    /**
     * Item avec une meta mockee. Un parametre {@code null} signifie "absent".
     */
    public static ItemStack item(Material material, String displayName, List<String> lore, Integer customModelData, NamespacedKey itemModel) {
        List<Component> loreComponents = lore == null ? null : lore.stream().map(line -> (Component) Component.text(line)).toList();
        Component nameComponent = displayName == null ? null : Component.text(displayName);
        return itemWithComponents(material, nameComponent, loreComponents, customModelData, itemModel);
    }

    /**
     * Variante acceptant des composants Adventure deja construits (couleurs, decorations...).
     */
    public static ItemStack itemWithComponents(Material material, Component displayName, List<Component> lore, Integer customModelData, NamespacedKey itemModel) {
        ItemMeta meta = mock(ItemMeta.class);
        lenient().when(meta.hasDisplayName()).thenReturn(displayName != null);
        lenient().when(meta.displayName()).thenReturn(displayName);
        lenient().when(meta.hasLore()).thenReturn(lore != null && !lore.isEmpty());
        lenient().when(meta.lore()).thenReturn(lore);
        lenient().when(meta.hasCustomModelData()).thenReturn(customModelData != null);
        lenient().when(meta.getCustomModelData()).thenReturn(customModelData != null ? customModelData : 0);
        lenient().when(meta.getItemModel()).thenReturn(itemModel);

        ItemStack itemStack = TestItems.itemStack(material, 1);
        lenient().when(itemStack.hasItemMeta()).thenReturn(true);
        lenient().when(itemStack.getItemMeta()).thenReturn(meta);
        return itemStack;
    }

    public static ItemRuleContext context(ItemStack itemStack) {
        return new ZItemRuleContext(itemStack);
    }

    public static ItemRuleContext context(Material material) {
        return context(plain(material));
    }
}
