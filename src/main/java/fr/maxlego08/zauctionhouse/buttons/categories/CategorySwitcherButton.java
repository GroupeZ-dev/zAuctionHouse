package fr.maxlego08.zauctionhouse.buttons.categories;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Combined category switcher button that cycles through categories on click
 * and displays dynamic lore showing which category is currently selected.
 * <p>
 * Left-click cycles forward, right-click cycles backward.
 * The special category ID "main" represents no filter (all items).
 * <p>
 * In lore, each category ID is replaced with either the enableText or disableText
 * depending on whether it's the currently selected category.
 */
public class CategorySwitcherButton extends Button {

    private final AuctionPlugin plugin;
    private final String enableText;
    private final String disableText;
    private final List<String> categories;

    public CategorySwitcherButton(AuctionPlugin plugin, String enableText, String disableText, List<String> categories) {
        this.plugin = plugin;
        this.enableText = enableText;
        this.disableText = disableText;
        this.categories = List.copyOf(categories);
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {

        MenuItemStack itemStack = this.getItemStack();
        var cache = this.plugin.getAuctionManager().getCache(player);
        Category currentCategory = cache.get(PlayerCacheKey.CURRENT_CATEGORY);

        String currentId = currentCategory != null ? currentCategory.getId() : "main";
        placeholders.register("category", currentCategory != null ? currentCategory.getDisplayName() : this.plugin.getCategoryManager().getAllCategoryName());

        var categoryManager = this.plugin.getCategoryManager();
        this.categories.forEach(categoryId -> {
            String displayName = categoryManager.getCategory(categoryId).map(Category::getDisplayName).orElse(this.plugin.getCategoryManager().getAllCategoryName());
            String text = (categoryId.equalsIgnoreCase(currentId) ? this.enableText : this.disableText).replace("%category%", displayName);
            placeholders.register(categoryId, text);
        });

        return itemStack.build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        if (this.categories.isEmpty()) return;

        var cache = this.plugin.getAuctionManager().getCache(player);
        Category currentCategory = cache.get(PlayerCacheKey.CURRENT_CATEGORY);

        String currentId = currentCategory != null ? currentCategory.getId() : "main";

        int index = -1;
        for (int i = 0; i < this.categories.size(); i++) {
            if (this.categories.get(i).equalsIgnoreCase(currentId)) {
                index = i;
                break;
            }
        }

        if (index == -1) index = 0;

        int direction = event.isRightClick() ? -1 : 1;
        int nextIndex = (index + direction + this.categories.size()) % this.categories.size();
        String nextCategoryId = this.categories.get(nextIndex);

        if (nextCategoryId.equalsIgnoreCase("main")) {
            cache.remove(PlayerCacheKey.CURRENT_CATEGORY);
        } else {
            var categoryManager = this.plugin.getCategoryManager();
            var optionalCategory = categoryManager.getCategory(nextCategoryId);
            if (optionalCategory.isEmpty()) {
                this.plugin.getLogger().warning("Category not found: " + nextCategoryId);
                return;
            }
            cache.set(PlayerCacheKey.CURRENT_CATEGORY, optionalCategory.get());
        }

        cache.remove(PlayerCacheKey.ITEMS_LISTED);
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.AUCTION);
    }
}
