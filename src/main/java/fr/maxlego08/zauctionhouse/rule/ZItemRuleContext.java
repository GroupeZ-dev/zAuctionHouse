package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Implementation of {@link ItemRuleContext} that pre-computes values on construction.
 * All expensive operations (color stripping, meta access) are done once.
 */
public class ZItemRuleContext implements ItemRuleContext {

    private final ItemStack itemStack;
    private final Material material;
    private final String displayName;
    private final boolean hasDisplayName;
    private final List<String> lore;
    private final boolean hasLore;
    private final int customModelData;
    private final boolean hasCustomModelData;

    public ZItemRuleContext(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.material = itemStack.getType();

        var componentHelper = ComponentMessageHelper.componentMessage;

        // Pre-compute display name
        this.hasDisplayName = componentHelper.hasDisplayName(itemStack);
        this.displayName = hasDisplayName ? componentHelper.getItemStackName(itemStack) : null;

        // Pre-compute lore
        this.lore = componentHelper.getItemStackLore(itemStack);
        this.hasLore = this.lore != null && !this.lore.isEmpty();

        // Pre-compute custom model data
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && meta.hasCustomModelData()) {
            this.hasCustomModelData = true;
            this.customModelData = meta.getCustomModelData();
        } else {
            this.hasCustomModelData = false;
            this.customModelData = 0;
        }
    }

    @Override
    public ItemStack getItemStack() {
        return itemStack;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean hasDisplayName() {
        return hasDisplayName;
    }

    @Override
    public List<String> getLore() {
        return lore;
    }

    @Override
    public boolean hasLore() {
        return hasLore;
    }

    @Override
    public int getCustomModelData() {
        return customModelData;
    }

    @Override
    public boolean hasCustomModelData() {
        return hasCustomModelData;
    }
}
