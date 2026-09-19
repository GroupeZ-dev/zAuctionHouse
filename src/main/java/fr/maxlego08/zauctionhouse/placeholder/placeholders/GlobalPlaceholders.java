package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;

public class GlobalPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        var manager = plugin.getAuctionManager();
        var categoryManager = plugin.getCategoryManager();

        // Servi en O(1) par le cache trie, dont le filtre de reconstruction est litteralement
        // isActivelyListed() : le compteur reste donc consistant avec la liste affichee, ce qui
        // etait l'intention du commentaire d'origine. L'ancien code recopiait TOUT le store
        // LISTED a chaque rendu d'inventaire (le placeholder est livre dans
        // inventories/auction.yml, donc evalue a chaque updateInventory de chaque joueur).
        placeholder.register("listed_items", player -> String.valueOf(manager.getListedItemCount()),
                "Returns the number of listed items");

        placeholder.register("category_count_", (player, args) -> {
            if (args == null || args.isEmpty()) {
                return "0";
            }

            long count = categoryManager.getItemCountForCategory(args);
            return String.valueOf(count);
        }, "Returns the number of items in a category", "<category_id>");
    }
}
