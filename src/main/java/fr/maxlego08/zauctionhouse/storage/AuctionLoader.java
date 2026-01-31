package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.ItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.PlayerRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AuctionLoader {

    private final AuctionPlugin plugin;
    private final Logger logger;
    private final AuctionManager auctionManager;
    private final StorageManager storageManager;
    private final EconomyManager economyManager;

    public AuctionLoader(AuctionPlugin plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageManager = storageManager;
        this.auctionManager = plugin.getAuctionManager();
        this.economyManager = plugin.getEconomyManager();
    }

    public void loadItems() {

        var players = this.storageManager.with(PlayerRepository.class).select().stream().collect(Collectors.toMap(PlayerDTO::unique_id, PlayerDTO::name));
        this.plugin.getLogger().info("Loaded " + players.size() + " players successfully");

        var categoryManager = this.plugin.getCategoryManager();
        var items = this.storageManager.with(ItemRepository.class).select();
        var auctionItems = this.storageManager.with(AuctionItemRepository.class).select(getIDS(items, ItemType.AUCTION));

        int amount = 0;

        for (ItemDTO dto : items) {

            var sellerName = getPlayerName(players, dto.seller_unique_id());

            String buyerName = null;
            if (dto.buyer_unique_id() != null) {
                buyerName = getPlayerName(players, dto.buyer_unique_id());
            }

            var optional = economyManager.getEconomy(dto.economy_name());
            if (optional.isEmpty()) {
                this.logger.severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                continue;
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var currentAuctionItems = getAuctionItems(auctionItems, dto.id());
                    var auctionItem = createAuctionItem(dto, sellerName, currentAuctionItems, optional.get());

                    if (buyerName != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), buyerName);
                    }

                    categoryManager.applyCategories(auctionItem);

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

        this.logger.info("Loaded " + amount + " items successfully (" + auctionItems.size() + " total)");
    }

    public AuctionItem createAuctionItem(ItemDTO dto, String sellerName, List<AuctionItemDTO> currentAuctionItems, AuctionEconomy auctionEconomy) {
        var itemStacks = currentAuctionItems.stream().map(e -> Base64ItemStack.decode(e.itemstack())).toList();

        var auctionItem = new ZAuctionItem(plugin, dto.id(), dto.server_name(), dto.seller_unique_id(), sellerName, dto.price(), auctionEconomy, dto.created_at(), dto.expired_at(), itemStacks);
        auctionItem.setStatus(switch (dto.storage_type()) {
            case LISTED -> ItemStatus.AVAILABLE;
            case PURCHASED -> ItemStatus.PURCHASED;
            case EXPIRED -> ItemStatus.REMOVED;
            case DELETED -> ItemStatus.DELETED;
        });
        return auctionItem;
    }

    private String getPlayerName(Map<UUID, String> players, UUID uniqueId) {
        var playerName = players.get(uniqueId);
        if (playerName == null) {
            throw new IllegalStateException("Unknown player with UUID " + uniqueId);
        }
        return playerName;
    }

    private List<AuctionItemDTO> getAuctionItems(List<AuctionItemDTO> auctionItemDTOS, int itemId) {
        return auctionItemDTOS.stream().filter(e -> e.item_id() == itemId).toList();
    }

    private List<String> getIDS(List<ItemDTO> itemDTOS, ItemType itemType) {
        return itemDTOS.stream().filter(e -> e.item_type() == itemType).map(ItemDTO::id).map(String::valueOf).toList();
    }
}
