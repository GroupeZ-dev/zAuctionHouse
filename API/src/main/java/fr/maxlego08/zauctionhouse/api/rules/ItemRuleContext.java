package fr.maxlego08.zauctionhouse.api.rules;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Context object for category matching that pre-computes commonly used values.
 * This avoids redundant computations when checking multiple rules against an item.
 * <p>
 * Values like display name and lore are computed once (without colors) and cached
 * for efficient rule matching.
 */
public interface ItemRuleContext {

    /**
     * @return the original ItemStack being evaluated
     */
    ItemStack getItemStack();

    /**
     * @return the material of the item
     */
    Material getMaterial();

    /**
     * @return the display name without color codes, or null if no custom name
     */
    String getDisplayName();

    /**
     * @return true if the item has a custom display name
     */
    boolean hasDisplayName();

    /**
     * @return the lore lines without color codes, or empty list if no lore
     */
    List<String> getLore();

    /**
     * @return true if the item has lore
     */
    boolean hasLore();

    /**
     * @return the custom model data, or 0 if not set
     */
    int getCustomModelData();

    /**
     * @return true if the item has custom model data
     */
    boolean hasCustomModelData();
}
