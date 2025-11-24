package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.items.ItemStatus;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.AuctionItemRepository;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class AuctionLoader {

    private final AuctionPlugin plugin;
    private final Logger logger;
    private final AuctionManager auctionManager;
    private final StorageManager storageManager;
    private final EconomyManager economyManager;
    private final Map<UUID, String> players;

    public AuctionLoader(AuctionPlugin plugin, StorageManager storageManager, Map<UUID, String> players) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageManager = storageManager;
        this.auctionManager = plugin.getAuctionManager();
        this.economyManager = plugin.getEconomyManager();
        this.players = players;
    }

    public void loadItems() {
        this.loadAuctionItems();
        this.loadRentItems();
        this.loadBidItems();
    }

    private void loadBidItems() {
    }

    private void loadRentItems() {
    }

    private void loadAuctionItems() {
        var auctionItems = this.storageManager.with(AuctionItemRepository.class).select();

        int amount = 0;

        for (AuctionItemDTO dto : auctionItems) {

            var sellerName = getPlayerName(dto.seller_unique_id());

            String buyerName = null; // ToDo
            if (dto.buyer_unique_id() != null) {
                buyerName = getPlayerName(dto.buyer_unique_id());
            }

            var itemStack = Base64ItemStack.decode(dto.itemstack());

            var economy = economyManager.getEconomy(dto.economy_name());
            if (economy.isEmpty()) {
                this.logger.severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                continue;
            }

            var auctionItem = new ZAuctionItem(plugin, dto.id(), dto.seller_unique_id(), sellerName, dto.price(), economy.get(), dto.created_at(), dto.expired_at(), itemStack);
            auctionItem.setStatus(switch (dto.storage_type()) {
                case LISTED -> ItemStatus.AVAILABLE;
                case PURCHASED -> ItemStatus.PURCHASED;
                case EXPIRED -> ItemStatus.REMOVED;
                case DELETED -> ItemStatus.DELETED;
            });

            auctionManager.addItem(dto.storage_type(), auctionItem);
            amount++;
        }

        this.logger.info("Loaded " + amount + " auction items successfully (" + auctionItems.size() + " total)");
    }

    private String getPlayerName(UUID uniqueId) {
        var playerName = this.players.get(uniqueId);
        if (playerName == null) {
            throw new IllegalStateException("Unknown player with UUID " + uniqueId);
        }
        return playerName;
    }
}
