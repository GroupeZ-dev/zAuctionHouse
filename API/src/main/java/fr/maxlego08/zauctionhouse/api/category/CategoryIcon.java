package fr.maxlego08.zauctionhouse.api.category;

import org.bukkit.Material;

/**
 * Represents the icon configuration for a category.
 * Used to display the category in inventory menus.
 *
 * @param material        the base material for the icon
 * @param customModelData optional custom model data for resource packs (0 if not set)
 * @param glow            whether to add enchantment glow effect
 */
public record CategoryIcon(
        Material material,
        int customModelData,
        boolean glow
) {
    /**
     * Creates a simple icon with just a material.
     *
     * @param material the material to use
     * @return a new CategoryIcon instance
     */
    public static CategoryIcon of(Material material) {
        return new CategoryIcon(material, 0, false);
    }

    /**
     * Creates an icon with material and custom model data.
     *
     * @param material        the material to use
     * @param customModelData the custom model data value
     * @return a new CategoryIcon instance
     */
    public static CategoryIcon of(Material material, int customModelData) {
        return new CategoryIcon(material, customModelData, false);
    }

    /**
     * Creates an icon with all options.
     *
     * @param material        the material to use
     * @param customModelData the custom model data value
     * @param glow            whether to add glow effect
     * @return a new CategoryIcon instance
     */
    public static CategoryIcon of(Material material, int customModelData, boolean glow) {
        return new CategoryIcon(material, customModelData, glow);
    }

    /**
     * Returns a default icon using CHEST material.
     *
     * @return default CategoryIcon
     */
    public static CategoryIcon defaultIcon() {
        return new CategoryIcon(Material.CHEST, 0, false);
    }
}
