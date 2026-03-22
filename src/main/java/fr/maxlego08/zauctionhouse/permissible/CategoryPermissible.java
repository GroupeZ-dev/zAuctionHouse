package fr.maxlego08.zauctionhouse.permissible;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.requirement.Action;
import fr.maxlego08.menu.api.requirement.Permissible;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * zMenu Permissible that checks if the player's currently selected category matches a specified category ID.
 * <p>
 * If the player has no category selected, the default category "main" is used for comparison.
 * <p>
 * YAML usage example:
 * <pre>
 * requirements:
 *   - type: zauctionhouse_category
 *     category: "weapons"
 * </pre>
 */
public class CategoryPermissible extends Permissible {

    private final AuctionPlugin plugin;
    private final String categoryId;

    public CategoryPermissible(@NotNull AuctionPlugin plugin, @NotNull String categoryId, @NotNull List<Action> denyActions, @NotNull List<Action> successActions) {
        super(denyActions, successActions);
        this.plugin = plugin;
        this.categoryId = categoryId;
    }

    @Override
    public boolean hasPermission(@NotNull Player player, @Nullable Button button, @NotNull InventoryEngine inventoryEngine, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        Category category = cache.get(PlayerCacheKey.CURRENT_CATEGORY);

        String currentCategoryId = category != null ? category.getId() : "main";
        return currentCategoryId.equalsIgnoreCase(this.categoryId);
    }

    @Override
    public boolean isValid() {
        return this.categoryId != null && !this.categoryId.isEmpty();
    }

    /**
     * Gets the category ID this permissible checks against.
     *
     * @return the category ID
     */
    public String getCategoryId() {
        return categoryId;
    }
}
