package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;

import java.math.BigDecimal;
import java.util.Map;

public class PlayerPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        var manager = plugin.getAuctionManager();
        var configuration = plugin.getConfiguration();

        placeholder.register("expired_items", player -> String.valueOf(manager.getExpiredItems(player).size()), "Returns the number of expired items for a player");
        placeholder.register("selling_items", player -> String.valueOf(manager.getPlayerSellingItems(player).size()), "Returns the number of items being sold by a player");
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

        // Pending money placeholders
        placeholder.register("pending_money", player -> {
            var cache = manager.getCache(player);
            if (cache.has(PlayerCacheKey.PENDING_MONEY_DATA)) {
                Map<String, BigDecimal> pendingData = cache.get(PlayerCacheKey.PENDING_MONEY_DATA);
                if (pendingData != null && !pendingData.isEmpty()) {
                    BigDecimal total = pendingData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    var defaultEconomy = plugin.getEconomyManager().getDefaultEconomy(fr.maxlego08.zauctionhouse.api.item.ItemType.AUCTION);
                    if (defaultEconomy != null) {
                        return plugin.getEconomyManager().format(defaultEconomy, total);
                    }
                    return total.toString();
                }
            }
            return "0";
        }, "Returns the total pending money for a player");

        placeholder.register("pending_money_raw", player -> {
            var cache = manager.getCache(player);
            if (cache.has(PlayerCacheKey.PENDING_MONEY_DATA)) {
                Map<String, BigDecimal> pendingData = cache.get(PlayerCacheKey.PENDING_MONEY_DATA);
                if (pendingData != null && !pendingData.isEmpty()) {
                    BigDecimal total = pendingData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    return total.toString();
                }
            }
            return "0";
        }, "Returns the raw pending money value for a player");

        placeholder.register("pending_money_", (player, economyName) -> {
            if (economyName == null || economyName.isEmpty()) return "0";
            var cache = manager.getCache(player);
            if (cache.has(PlayerCacheKey.PENDING_MONEY_DATA)) {
                Map<String, BigDecimal> pendingData = cache.get(PlayerCacheKey.PENDING_MONEY_DATA);
                if (pendingData != null && pendingData.containsKey(economyName)) {
                    var optionalEconomy = plugin.getEconomyManager().getEconomy(economyName);
                    if (optionalEconomy.isPresent()) {
                        return plugin.getEconomyManager().format(optionalEconomy.get(), pendingData.get(economyName));
                    }
                    return pendingData.get(economyName).toString();
                }
            }
            return "0";
        }, "Returns the pending money for a specific economy", "<economy>");

        placeholder.register("has_pending_money", player -> {
            var cache = manager.getCache(player);
            if (cache.has(PlayerCacheKey.PENDING_MONEY_DATA)) {
                Map<String, BigDecimal> pendingData = cache.get(PlayerCacheKey.PENDING_MONEY_DATA);
                if (pendingData != null && !pendingData.isEmpty()) {
                    BigDecimal total = pendingData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    return String.valueOf(total.compareTo(BigDecimal.ZERO) > 0);
                }
            }
            return "false";
        }, "Returns true if the player has pending money to claim");
    }
}
