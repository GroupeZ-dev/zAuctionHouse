package fr.maxlego08.zauctionhouse.migration.v3.reader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.migration.v3.V3StorageType;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads data from zAuctionHouse V3 JSON files.
 */
public class V3JsonDataReader implements V3DataReader {

    private final AuctionPlugin plugin;
    private final File dataFolder;
    private final Gson gson;

    public V3JsonDataReader(AuctionPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.dataFolder = dataFolder;
        this.gson = new Gson();
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            File itemsFile = new File(dataFolder, "items.json");
            return itemsFile.exists() && itemsFile.canRead();
        });
    }

    @Override
    public CompletableFuture<List<V3AuctionItem>> readItems() {
        return CompletableFuture.supplyAsync(() -> {
            List<V3AuctionItem> allItems = new ArrayList<>();

            // V3 stores items in separate files based on storage type
            allItems.addAll(readItemsFromFile("items.json", V3StorageType.STORAGE));
            allItems.addAll(readItemsFromFile("buying_items.json", V3StorageType.BUY));
            allItems.addAll(readItemsFromFile("expired_items.json", V3StorageType.EXPIRE));

            // Also try the single items.json format (some V3 versions)
            if (allItems.isEmpty()) {
                allItems.addAll(readAllItemsFromSingleFile());
            }

            return allItems;
        });
    }

    private List<V3AuctionItem> readItemsFromFile(String fileName, V3StorageType defaultType) {
        List<V3AuctionItem> items = new ArrayList<>();
        File file = new File(dataFolder, fileName);

        if (!file.exists()) {
            return items;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement itemElement : array) {
                    V3AuctionItem item = parseItemFromJson(itemElement.getAsJsonObject(), defaultType);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read V3 JSON file " + fileName + ": " + e.getMessage());
        }

        return items;
    }

    private List<V3AuctionItem> readAllItemsFromSingleFile() {
        List<V3AuctionItem> items = new ArrayList<>();
        File file = new File(dataFolder, "data.json");

        if (!file.exists()) {
            return items;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();

                // Try different possible structures
                if (root.has("items")) {
                    items.addAll(parseItemArray(root.getAsJsonArray("items"), V3StorageType.STORAGE));
                }
                if (root.has("buyingItems")) {
                    items.addAll(parseItemArray(root.getAsJsonArray("buyingItems"), V3StorageType.BUY));
                }
                if (root.has("expiredItems")) {
                    items.addAll(parseItemArray(root.getAsJsonArray("expiredItems"), V3StorageType.EXPIRE));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read V3 data.json: " + e.getMessage());
        }

        return items;
    }

    private List<V3AuctionItem> parseItemArray(JsonArray array, V3StorageType defaultType) {
        List<V3AuctionItem> items = new ArrayList<>();
        if (array == null) return items;

        for (JsonElement element : array) {
            V3AuctionItem item = parseItemFromJson(element.getAsJsonObject(), defaultType);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private V3AuctionItem parseItemFromJson(JsonObject json, V3StorageType defaultType) {
        try {
            UUID id = getUUID(json, "id");
            if (id == null) {
                id = getUUID(json, "uniqueId");
            }
            if (id == null) {
                return null;
            }

            String itemstack = getString(json, "itemstack");
            if (itemstack == null) {
                itemstack = getString(json, "itemStack");
            }

            long price = getLong(json, "price");
            UUID seller = getUUID(json, "seller");
            if (seller == null) {
                seller = getUUID(json, "sellerUniqueId");
            }

            UUID buyer = getUUID(json, "buyer");
            if (buyer == null) {
                buyer = getUUID(json, "buyerUniqueId");
            }

            String economy = getString(json, "economy");
            if (economy == null) {
                economy = "VAULT";
            }

            String auctionType = getString(json, "auctionType");
            if (auctionType == null) {
                auctionType = getString(json, "auction_type");
            }
            if (auctionType == null) {
                auctionType = "DEFAULT";
            }

            long expireAt = getLong(json, "expireAt");
            if (expireAt == 0) {
                expireAt = getLong(json, "expire_at");
            }

            V3StorageType storageType = defaultType;
            String storageTypeStr = getString(json, "storageType");
            if (storageTypeStr == null) {
                storageTypeStr = getString(json, "storage_type");
            }
            if (storageTypeStr != null) {
                storageType = V3StorageType.fromString(storageTypeStr);
            }

            String sellerName = getString(json, "sellerName");
            String serverName = getString(json, "serverName");
            if (serverName == null) {
                serverName = getString(json, "server_name");
            }

            int priority = getInt(json, "priority");

            return new V3AuctionItem(id, itemstack, price, seller, buyer, economy,
                                      auctionType, expireAt, storageType, sellerName, serverName, priority);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse V3 item from JSON: " + e.getMessage());
            return null;
        }
    }

    @Override
    public CompletableFuture<List<V3Transaction>> readTransactions() {
        return CompletableFuture.supplyAsync(() -> {
            List<V3Transaction> transactions = new ArrayList<>();
            File file = new File(dataFolder, "transactions.json");

            if (!file.exists()) {
                return transactions;
            }

            try (FileReader reader = new FileReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);

                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    for (JsonElement transElement : array) {
                        V3Transaction transaction = parseTransactionFromJson(transElement.getAsJsonObject());
                        if (transaction != null) {
                            transactions.add(transaction);
                        }
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to read V3 transactions.json: " + e.getMessage());
            }

            return transactions;
        });
    }

    private V3Transaction parseTransactionFromJson(JsonObject json) {
        try {
            int id = getInt(json, "id");

            UUID seller = getUUID(json, "seller");
            if (seller == null) {
                seller = getUUID(json, "sellerUniqueId");
            }

            UUID buyer = getUUID(json, "buyer");
            if (buyer == null) {
                buyer = getUUID(json, "buyerUniqueId");
            }

            String itemstack = getString(json, "itemstack");
            if (itemstack == null) {
                itemstack = getString(json, "itemStack");
            }

            long transactionDate = getLong(json, "transactionDate");
            if (transactionDate == 0) {
                transactionDate = getLong(json, "transaction_date");
            }
            if (transactionDate == 0) {
                transactionDate = getLong(json, "date");
            }

            long price = getLong(json, "price");

            String economy = getString(json, "economy");
            if (economy == null) {
                economy = "VAULT";
            }

            boolean isRead = getBoolean(json, "isRead");
            if (!isRead) {
                isRead = getBoolean(json, "is_read");
            }

            boolean needMoney = getBoolean(json, "needMoney");
            if (!needMoney) {
                needMoney = getBoolean(json, "need_money");
            }

            return new V3Transaction(id, seller, buyer, itemstack, transactionDate,
                                      price, economy, isRead, needMoney);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse V3 transaction from JSON: " + e.getMessage());
            return null;
        }
    }

    @Override
    public CompletableFuture<Integer> getItemCount() {
        return readItems().thenApply(List::size);
    }

    @Override
    public CompletableFuture<Integer> getTransactionCount() {
        return readTransactions().thenApply(List::size);
    }

    @Override
    public void close() {
        // No resources to close for JSON reader
    }

    // Helper methods for JSON parsing

    private String getString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }

    private long getLong(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsLong();
        }
        return 0;
    }

    private int getInt(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsInt();
        }
        return 0;
    }

    private boolean getBoolean(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsBoolean();
        }
        return false;
    }

    private UUID getUUID(JsonObject json, String key) {
        String str = getString(json, key);
        if (str == null || str.isEmpty() || str.equals("null")) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
