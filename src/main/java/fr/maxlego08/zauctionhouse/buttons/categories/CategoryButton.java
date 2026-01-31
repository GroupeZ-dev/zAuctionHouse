package fr.maxlego08.zauctionhouse.buttons.categories;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NonNull;

/**
 * Button that opens a specific category when clicked.
 * The category is defined in the inventory YAML configuration by its ID.
 */
public class CategoryButton extends Button {

    private final AuctionPlugin plugin;
    private final String categoryId;

    public CategoryButton(AuctionPlugin plugin, String categoryId) {
        this.plugin = plugin;
        this.categoryId = categoryId;
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);

        // Special case: "all" removes the category filter
        if (this.categoryId.equalsIgnoreCase("all")) {
            cache.remove(PlayerCacheKey.CURRENT_CATEGORY);
            cache.remove(PlayerCacheKey.ITEMS_LISTED);
            this.plugin.getInventoriesLoader().openInventory(player, Inventories.AUCTION);
            return;
        }

        var categoryManager = this.plugin.getCategoryManager();
        var optionalCategory = categoryManager.getCategory(this.categoryId);

        if (optionalCategory.isEmpty()) {
            this.plugin.getLogger().warning("Category not found: " + this.categoryId);
            return;
        }

        Category category = optionalCategory.get();

        // Store the selected category in the player cache
        cache.set(PlayerCacheKey.CURRENT_CATEGORY, category);

        // Clear the cached listed items to force a refresh with the new category filter
        cache.remove(PlayerCacheKey.ITEMS_LISTED);

        // Update the inventory to show items from this category
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.AUCTION);
    }

    /**
     * Gets the category ID this button opens.
     *
     * @return the category ID
     */
    public String getCategoryId() {
        return categoryId;
    }
}
