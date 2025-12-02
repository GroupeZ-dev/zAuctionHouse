package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.ItemRepository;

import java.util.List;
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

        var items = this.storageManager.with(ItemRepository.class).select();
        var auctionItems = this.storageManager.with(AuctionItemRepository.class).select();

        int amount = 0;

        for (ItemDTO dto : items) {

            var sellerName = getPlayerName(dto.seller_unique_id());

            String buyerName = null;
            if (dto.buyer_unique_id() != null) {
                buyerName = getPlayerName(dto.buyer_unique_id());
            }

            var economy = economyManager.getEconomy(dto.economy_name());
            if (economy.isEmpty()) {
                this.logger.severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                continue;
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var currentAuctionItems = getAuctionItems(auctionItems, dto.id());

                    var itemStacks = currentAuctionItems.stream().map(e -> Base64ItemStack.decode(e.itemstack())).toList();

                    var auctionItem = new ZAuctionItem(plugin, dto.id(), dto.server_name(), dto.seller_unique_id(), sellerName, dto.price(), economy.get(), dto.created_at(), dto.expired_at(), itemStacks);
                    auctionItem.setStatus(switch (dto.storage_type()) {
                        case LISTED -> ItemStatus.AVAILABLE;
                        case PURCHASED -> ItemStatus.PURCHASED;
                        case EXPIRED -> ItemStatus.REMOVED;
                        case DELETED -> ItemStatus.DELETED;
                    });

                    if (buyerName != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), buyerName);
                    }

                    auctionManager.addItem(dto.storage_type(), auctionItem);
                }
                case BID -> {
                    this.plugin.getLogger().severe("Bid items not implemented");
                }
                case RENT -> {
                    this.plugin.getLogger().severe("Rent items not implemented");
                }
            }

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

    private List<AuctionItemDTO> getAuctionItems(List<AuctionItemDTO> auctionItemDTOS, int itemId) {
        return auctionItemDTOS.stream().filter(e -> e.item_id() == itemId).toList();
    }
}
