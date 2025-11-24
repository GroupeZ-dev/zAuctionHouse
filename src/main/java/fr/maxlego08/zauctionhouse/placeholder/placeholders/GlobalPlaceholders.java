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
    }
}
