package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.AuctionItemRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public abstract class ItemLoaderUtils {

    protected String getPlayerName(Map<UUID, String> players, UUID uniqueId) {
        var playerName = players.get(uniqueId);
        if (playerName == null) {
            throw new IllegalStateException("Unknown player with UUID " + uniqueId);
        }
        return playerName;
    }

    protected List<AuctionItemDTO> getAuctionItems(List<AuctionItemDTO> auctionItemDTOS, int itemId) {
        return auctionItemDTOS.stream().filter(e -> e.item_id() == itemId).toList();
    }

    protected List<String> getIDS(List<ItemDTO> itemDTOS, ItemType itemType) {
        return itemDTOS.stream().filter(e -> e.item_type() == itemType).map(ItemDTO::id).map(String::valueOf).toList();
    }

    protected AuctionItem createAuctionItem(AuctionPlugin plugin, ItemDTO dto, String sellerName, List<AuctionItemDTO> currentAuctionItems, AuctionEconomy auctionEconomy) {
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

    protected Result createItems(AuctionPlugin plugin, Map<UUID, String> players, List<ItemDTO> items, PerformanceDebug performanceDebug, BiConsumer<StorageType, Item> biConsumer) {

        var categoryManager = plugin.getCategoryManager();
        var storageManager = plugin.getStorageManager();
        var economyManager = plugin.getEconomyManager();

        long auctionItemsStartTime = performanceDebug.start();
        var auctionItems = storageManager.with(AuctionItemRepository.class).select(getIDS(items, ItemType.AUCTION));
        performanceDebug.end("loadItems.loadAuctionItemsFromDB", auctionItemsStartTime, "count=" + auctionItems.size());

        int amount = 0;

        // Process items
        long processStartTime = performanceDebug.start();
        for (ItemDTO dto : items) {

            var sellerName = getPlayerName(players, dto.seller_unique_id());

            String buyerName = null;
            if (dto.buyer_unique_id() != null) {
                buyerName = getPlayerName(players, dto.buyer_unique_id());
            }

            var optional = economyManager.getEconomy(dto.economy_name());
            if (optional.isEmpty()) {
                plugin.getLogger().severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                continue;
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var currentAuctionItems = getAuctionItems(auctionItems, dto.id());
                    var auctionItem = this.createAuctionItem(plugin, dto, sellerName, currentAuctionItems, optional.get());

                    if (buyerName != null) {
                        auctionItem.setBuyer(dto.buyer_unique_id(), buyerName);
                    }

                    categoryManager.applyCategories(auctionItem);

                    biConsumer.accept(dto.storage_type(), auctionItem);
                }
                case BID -> {
                    plugin.getLogger().severe("Bid items not implemented");
                }
                case RENT -> {
                    plugin.getLogger().severe("Rent items not implemented");
                }
            }

            amount++;
        }
        performanceDebug.end("loadItems.processItems", processStartTime, "processed=" + amount);
        return new Result(amount, auctionItems.size(), 0, 0);
    }

    public record Result(int amount, int auctionItems, int bidItems, int rentItems) {

    }

}
