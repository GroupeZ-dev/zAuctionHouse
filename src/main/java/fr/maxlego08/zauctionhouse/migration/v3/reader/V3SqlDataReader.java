package fr.maxlego08.zauctionhouse.migration.v3.reader;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.migration.v3.V3StorageType;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads data from zAuctionHouse V3 SQL database (MySQL/MariaDB/SQLite).
 */
public class V3SqlDataReader implements V3DataReader {

    private final AuctionPlugin plugin;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String tablePrefix;
    private Connection connection;

    /**
     * Creates a reader for MySQL/MariaDB database.
     */
    public V3SqlDataReader(AuctionPlugin plugin, String host, int port, String database, String username, String password, String tablePrefix) {
        this.plugin = plugin;
        this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix != null ? tablePrefix : "zauctionhouse_";
    }

    /**
     * Creates a reader for SQLite database.
     */
    public V3SqlDataReader(AuctionPlugin plugin, String sqlitePath, String tablePrefix) {
        this.plugin = plugin;
        this.jdbcUrl = "jdbc:sqlite:" + sqlitePath;
        this.username = null;
        this.password = null;
        this.tablePrefix = tablePrefix != null ? tablePrefix : "zauctionhouse_";
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            if (username != null) {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
            } else {
                connection = DriverManager.getConnection(jdbcUrl);
            }
        }
        return connection;
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Connection conn = getConnection();
                return conn != null && !conn.isClosed();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to connect to V3 database: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<V3AuctionItem>> readItems() {
        return CompletableFuture.supplyAsync(() -> {
            List<V3AuctionItem> items = new ArrayList<>();
            String tableName = tablePrefix + "items";

            String sql = "SELECT id, itemstack, price, seller, buyer, economy, auction_type, " + "expire_at, storage_type, sellerName, server_name, priority FROM " + tableName;

            try (PreparedStatement stmt = getConnection().prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    try {
                        V3AuctionItem item = parseItem(rs);
                        if (item != null) {
                            items.add(item);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse V3 item: " + e.getMessage());
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to read V3 items: " + e.getMessage());
            }

            return items;
        });
    }

    private V3AuctionItem parseItem(ResultSet rs) throws SQLException {
        String idStr = rs.getString("id");
        UUID id = parseUUID(idStr);
        if (id == null) {
            plugin.getLogger().warning("Invalid UUID for item: " + idStr);
            return null;
        }

        String itemstack = rs.getString("itemstack");
        long price = rs.getLong("price");
        UUID seller = parseUUID(rs.getString("seller"));
        UUID buyer = parseUUID(rs.getString("buyer"));
        String economy = rs.getString("economy");
        String auctionType = rs.getString("auction_type");
        long expireAt = rs.getLong("expire_at");
        V3StorageType storageType = V3StorageType.fromString(rs.getString("storage_type"));
        String sellerName = rs.getString("sellerName");
        String serverName = rs.getString("server_name");

        int priority = 0;
        try {
            priority = rs.getInt("priority");
        } catch (SQLException ignored) {
            // Column might not exist in older V3 versions
        }

        return new V3AuctionItem(id, itemstack, price, seller, buyer, economy, auctionType, expireAt, storageType, sellerName, serverName, priority);
    }

    @Override
    public CompletableFuture<List<V3Transaction>> readTransactions() {
        return CompletableFuture.supplyAsync(() -> {
            List<V3Transaction> transactions = new ArrayList<>();
            String tableName = tablePrefix + "transactions";

            String sql = "SELECT id, seller, buyer, itemstack, transaction_date, price, " + "economy, is_read, need_money FROM " + tableName;

            try (PreparedStatement stmt = getConnection().prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    try {
                        V3Transaction transaction = parseTransaction(rs);
                        if (transaction != null) {
                            transactions.add(transaction);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse V3 transaction: " + e.getMessage());
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to read V3 transactions: " + e.getMessage());
            }

            return transactions;
        });
    }

    private V3Transaction parseTransaction(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        UUID seller = parseUUID(rs.getString("seller"));
        UUID buyer = parseUUID(rs.getString("buyer"));
        String itemstack = rs.getString("itemstack");
        long transactionDate = rs.getLong("transaction_date");
        long price = rs.getLong("price");
        String economy = rs.getString("economy");
        boolean isRead = rs.getBoolean("is_read");
        boolean needMoney = rs.getBoolean("need_money");

        return new V3Transaction(id, seller, buyer, itemstack, transactionDate, price, economy, isRead, needMoney);
    }

    @Override
    public CompletableFuture<Integer> getItemCount() {
        return CompletableFuture.supplyAsync(() -> {
            String tableName = tablePrefix + "items";
            String sql = "SELECT COUNT(*) FROM " + tableName;

            try (PreparedStatement stmt = getConnection().prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to count V3 items: " + e.getMessage());
            }
            return 0;
        });
    }

    @Override
    public CompletableFuture<Integer> getTransactionCount() {
        return CompletableFuture.supplyAsync(() -> {
            String tableName = tablePrefix + "transactions";
            String sql = "SELECT COUNT(*) FROM " + tableName;

            try (PreparedStatement stmt = getConnection().prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to count V3 transactions: " + e.getMessage());
            }
            return 0;
        });
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close V3 database connection: " + e.getMessage());
            }
        }
    }

    private UUID parseUUID(String str) {
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
