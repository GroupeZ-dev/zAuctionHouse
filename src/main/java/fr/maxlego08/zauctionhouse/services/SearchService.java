package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.filter.SearchField;
import fr.maxlego08.zauctionhouse.api.filter.SearchFilterType;
import fr.maxlego08.zauctionhouse.api.filter.SearchQuery;
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

        // Acces O(1) direct dans le conteneur LISTED. L'ancien code copiait TOUT le store
        // (getItems alloue une ArrayList complete) puis reconstruisait une HashMap de n
        // entrees, a chaque frappe de recherche et pour chaque joueur (C-055).
        var manager = this.plugin.getAuctionManager();
        String lowerValue = parsedQuery.value().toLowerCase();

        for (int id : allIds) {
            Item item = manager.getItem(StorageType.LISTED, id);
            if (item == null) continue;

            if (parsedQuery.isDefault()) {
                if (matchDefault(item, lowerValue)) {
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
    private boolean matchDefault(Item item, String lowerQuery) {
        // Material name
        if (item.getTranslationKey() != null && item.getTranslationKey().toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Check material type name
        if (item instanceof AuctionItem auctionItem) {
            ItemStack itemStack = auctionItem.getItemStack();
            if (itemStack != null && itemStack.getType().name().toLowerCase().contains(lowerQuery)) {
                return true;
            }
        }

        // Display name
        String displayName = getDisplayName(item);
        if (displayName != null && displayName.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Lore
        List<String> lore = getLore(item);
        if (lore != null) {
            for (String line : lore) {
                if (line.toLowerCase().contains(lowerQuery)) {
                    return true;
                }
            }
        }

        // Seller name (exact case-insensitive)
        if (item.getSellerName() != null && item.getSellerName().equalsIgnoreCase(lowerQuery)) {
            return true;
        }

        return false;
    }

    /**
     * Targeted match with a specific field and filter type.
     */
    private boolean matchWithFilter(Item item, SearchField field, SearchFilterType type, String value) {
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
        return switch (type) {
            case CONTAINS -> fieldValue.contains(queryValue);
            case EQUALS -> fieldValue.equals(queryValue);
            case CONTAINS_IGNORE_CASE -> fieldValue.toLowerCase().contains(queryValue.toLowerCase());
            case EQUALS_IGNORE_CASE -> fieldValue.equalsIgnoreCase(queryValue);
        };
    }

    private String getFieldValue(Item item, SearchField field) {
        return switch (field) {
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
