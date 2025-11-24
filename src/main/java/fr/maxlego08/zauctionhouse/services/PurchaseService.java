package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.services.AuctionPurchaseService;
import org.bukkit.entity.Player;

public class PurchaseService implements AuctionPurchaseService {

    private final AuctionPlugin plugin;

    public PurchaseService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void purchaseItem(Player player, Item item) {
        // ToDo
        this.plugin.getLogger().severe("ToDo");
    }
}
