package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;

public class PlayerPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        var manager = plugin.getAuctionManager();

        placeholder.register("expired_items", player -> String.valueOf(manager.getExpiredItems(player).size()), "Returns the number of expired items for a player");
        placeholder.register("owned_items", player -> String.valueOf(manager.getPlayerOwnedItems(player).size()), "Returns the number of owned items for a player");
        placeholder.register("purchased_items", player -> String.valueOf(manager.getPurchasedItems(player).size()), "Returns the number of purchased items for a player");
    }
}
