package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;

public class PlayerPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        var manager = plugin.getAuctionManager();
        var configuration = plugin.getConfiguration();

        placeholder.register("expired_items", player -> String.valueOf(manager.getExpiredItems(player).size()), "Returns the number of expired items for a player");
        placeholder.register("owned_items", player -> String.valueOf(manager.getPlayerOwnedItems(player).size()), "Returns the number of owned items for a player");
        placeholder.register("purchased_items", player -> String.valueOf(manager.getPurchasedItems(player).size()), "Returns the number of purchased items for a player");

        placeholder.register("sorting_value", player -> manager.getCache(player).get(PlayerCacheKey.ITEM_SORT, plugin.getConfiguration().getSort().defaultSort()).name(), "Returns the value for sorting the items for the player");
        placeholder.register("sorting_name", player -> {
            var sortConfiguration = configuration.getSort();
            return sortConfiguration.sortItems().get(manager.getCache(player).get(PlayerCacheKey.ITEM_SORT, sortConfiguration.defaultSort()));
        }, "Returns the name of the value used to sort the items for the player");

        placeholder.register("category_name", player -> {

            var cache = manager.getCache(player);

            if (cache.has(PlayerCacheKey.CURRENT_CATEGORY)) {
                Category category = cache.get(PlayerCacheKey.CURRENT_CATEGORY);
                return category.getDisplayName();
            }

            return plugin.getCategoryManager().getAllCategoryName();
        }, "Returns the name of the current category for the player");
    }
}
