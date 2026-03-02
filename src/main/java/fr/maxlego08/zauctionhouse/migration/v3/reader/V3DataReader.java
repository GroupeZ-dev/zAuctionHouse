package fr.maxlego08.zauctionhouse.migration.v3.reader;

import fr.maxlego08.zauctionhouse.migration.v3.items.V3AuctionItem;
import fr.maxlego08.zauctionhouse.migration.v3.items.V3Transaction;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for reading data from zAuctionHouse V3 storage.
 */
public interface V3DataReader {

    /**
     * Tests the connection to the V3 data source.
     *
     * @return true if connection is successful
     */
    CompletableFuture<Boolean> testConnection();

    /**
     * Reads all auction items from V3 storage.
     *
     * @return List of V3 auction items
     */
    CompletableFuture<List<V3AuctionItem>> readItems();

    /**
     * Reads all transactions from V3 storage.
     *
     * @return List of V3 transactions
     */
    CompletableFuture<List<V3Transaction>> readTransactions();

    /**
     * Gets the total count of items to migrate.
     *
     * @return Item count
     */
    CompletableFuture<Integer> getItemCount();

    /**
     * Gets the total count of transactions to migrate.
     *
     * @return Transaction count
     */
    CompletableFuture<Integer> getTransactionCount();

    /**
     * Closes any open connections.
     */
    void close();
}
