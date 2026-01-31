package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;

public class GlobalPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        var manager = plugin.getAuctionManager();

        placeholder.register("listed_items", player -> String.valueOf(manager.getItems(StorageType.LISTED).size()), "Returns the number of listed items");

        placeholder.register("category_count_", (player, args) -> {
            if (args == null || args.isEmpty()) {
                return "0";
            }

            String categoryId = args.toLowerCase();
            var items = manager.getItems(StorageType.LISTED);

            // Special case: "all" returns total count
            if (categoryId.equals("all")) {
                return String.valueOf(items.size());
            }

            // Count items that have this category
            long count = items.stream()
                    .filter(item -> item.hasCategory(categoryId))
                    .count();

            return String.valueOf(count);
        }, "Returns the number of items in a category", "<category_id>");
    }
}
