package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.filter.SearchField;
import fr.maxlego08.zauctionhouse.api.filter.SearchFilterType;
import fr.maxlego08.zauctionhouse.api.filter.SearchQuery;
import fr.maxlego08.zauctionhouse.api.filter.SearchTextMatcher;
import fr.maxlego08.zauctionhouse.api.filter.SearchTextNormalizer;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.utils.IntArrayList;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.utils.cache.SortedItemsCache;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SearchService {

    private final AuctionPlugin plugin;

    public SearchService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Searches through sorted items and returns IDs matching the query.
     *
     * @param sortedItemsCache the sorted items cache
     * @param query            the raw query string
     * @param sort             the current sort order
     * @param category         the current category filter (can be null)
     * @return list of matching item IDs in sorted order
     */
    public IntList search(SortedItemsCache sortedItemsCache, String query, SortItem sort, Category category) {

        SearchQuery parsedQuery = SearchQuery.parse(query, this.plugin.getConfiguration().getSearchFilter());
        if (parsedQuery.value().isEmpty()) {
            return sortedItemsCache.getSortedIds(category, sort);
        }

        IntList allIds = sortedItemsCache.getSortedIds(category, sort);
        IntList results = new IntArrayList();

        var storage = this.plugin.getAuctionManager().getItems(StorageType.LISTED);
        var itemMap = new java.util.HashMap<Integer, Item>(storage.size());
        for (Item item : storage) {
            itemMap.put(item.getId(), item);
        }

        for (int id : allIds) {
            Item item = itemMap.get(id);
            if (item == null) continue;

            if (parsedQuery.isDefault()) {
                if (matchDefault(item, parsedQuery.value())) {
                    results.add(id);
                }
            } else {
                if (matchWithFilter(item, parsedQuery.field(), parsedQuery.type(), parsedQuery.value())) {
                    results.add(id);
                }
            }
        }

        return results;
    }

    /**
     * Default match: substring case-insensitive on material name, display name, lore, and seller name.
     */
    private boolean matchDefault(Item item, String query) {
        String normalizedQuery = SearchTextNormalizer.normalize(query);

        // Material name
        if (item.getTranslationKey() != null && SearchTextNormalizer.normalize(item.getTranslationKey()).contains(normalizedQuery)) {
            return true;
        }

        // Check material type name
        if (item instanceof AuctionItem auctionItem) {
            ItemStack itemStack = auctionItem.getItemStack();
            if (itemStack != null && SearchTextNormalizer.normalize(itemStack.getType().name()).contains(normalizedQuery)) {
                return true;
            }
        }

        // Display name
        String displayName = getDisplayName(item);
        if (displayName != null && SearchTextNormalizer.normalize(displayName).contains(normalizedQuery)) {
            return true;
        }

        // Lore
        List<String> lore = getLore(item);
        if (lore != null) {
            for (String line : lore) {
                if (SearchTextNormalizer.normalize(line).contains(normalizedQuery)) {
                    return true;
                }
            }
        }

        // Seller name (exact case-insensitive)
        if (item.getSellerName() != null && SearchTextNormalizer.normalize(item.getSellerName()).equals(normalizedQuery)) {
            return true;
        }

        return false;
    }

    /**
     * Targeted match with a specific field and filter type.
     */
    private boolean matchWithFilter(Item item, SearchField field, SearchFilterType type, String value) {
        if (field == SearchField.ALL) {
            return matchAllFields(item, type, value);
        }

        String fieldValue = getFieldValue(item, field);
        if (fieldValue == null) {
            // For the LORE field, check all lines
            if (field == SearchField.LORE) {
                List<String> lore = getLore(item);
                if (lore == null) return false;
                for (String line : lore) {
                    if (matchValue(line, type, value)) return true;
                }
                return false;
            }
            return false;
        }

        return matchValue(fieldValue, type, value);
    }

    private boolean matchValue(String fieldValue, SearchFilterType type, String queryValue) {
        return SearchTextMatcher.matches(fieldValue, type, queryValue);
    }

    private boolean matchAllFields(Item item, SearchFilterType type, String value) {
        String displayName = getDisplayName(item);
        if (displayName != null && matchValue(displayName, type, value)) {
            return true;
        }

        if (item.getSellerName() != null && matchValue(item.getSellerName(), type, value)) {
            return true;
        }

        String translationKey = item.getTranslationKey();
        if (translationKey != null && matchValue(translationKey, type, value)) {
            return true;
        }

        if (item instanceof AuctionItem auctionItem) {
            ItemStack itemStack = auctionItem.getItemStack();
            if (itemStack != null && matchValue(itemStack.getType().name(), type, value)) {
                return true;
            }
        }

        List<String> lore = getLore(item);
        if (lore == null) {
            return false;
        }

        for (String line : lore) {
            if (matchValue(line, type, value)) {
                return true;
            }
        }
        return false;
    }

    private String getFieldValue(Item item, SearchField field) {
        return switch (field) {
            case ALL -> null;
            case NAME -> getDisplayName(item);
            case MATERIAL -> {
                if (item instanceof AuctionItem auctionItem) {
                    ItemStack itemStack = auctionItem.getItemStack();
                    yield itemStack != null ? itemStack.getType().name() : null;
                }
                yield null;
            }
            case SELLER -> item.getSellerName();
            case LORE -> null; // Handled separately (multi-line)
        };
    }

    private String getDisplayName(Item item) {
        if (item instanceof AuctionItem auctionItem) {
            ItemStack itemStack = auctionItem.getItemStack();
            if (itemStack != null && itemStack.hasItemMeta()) {
                ItemMeta meta = itemStack.getItemMeta();
                var displayName = meta.displayName();
                if (displayName != null) {
                    return PlainTextComponentSerializer.plainText().serialize(displayName);
                }
            }
        }
        return null;
    }

    private List<String> getLore(Item item) {
        if (item instanceof AuctionItem auctionItem) {
            ItemStack itemStack = auctionItem.getItemStack();
            if (itemStack != null && itemStack.hasItemMeta()) {
                ItemMeta meta = itemStack.getItemMeta();
                var lore = meta.lore();
                if (lore != null) {
                    return lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
                }
            }
        }
        return null;
    }
}
