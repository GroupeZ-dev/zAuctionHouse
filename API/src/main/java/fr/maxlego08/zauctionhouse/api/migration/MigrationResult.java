package fr.maxlego08.zauctionhouse.api.migration;

/**
 * Represents the result of a migration operation.
 */
public interface MigrationResult {

    /**
     * Whether the migration was successful.
     *
     * @return true if successful
     */
    boolean isSuccess();

    /**
     * Gets the number of players imported.
     *
     * @return Player count
     */
    int getPlayersImported();

    /**
     * Gets the number of items imported.
     *
     * @return Item count
     */
    int getItemsImported();

    /**
     * Gets the number of transactions imported.
     *
     * @return Transaction count
     */
    int getTransactionsImported();

    /**
     * Gets the number of errors encountered.
     *
     * @return Error count
     */
    int getErrors();

    /**
     * Gets the duration of the migration in milliseconds.
     *
     * @return Duration in ms
     */
    long getDurationMs();

    /**
     * Gets the error message if the migration failed.
     *
     * @return Error message, or null if successful
     */
    String getErrorMessage();

    /**
     * Creates a successful migration result.
     *
     * @param playersImported      Number of players imported
     * @param itemsImported        Number of items imported
     * @param transactionsImported Number of transactions imported
     * @param errors               Number of errors
     * @param durationMs           Duration in milliseconds
     * @return A successful result
     */
    static MigrationResult success(int playersImported, int itemsImported, int transactionsImported, int errors, long durationMs) {
        return new SimpleMigrationResult(true, playersImported, itemsImported, transactionsImported, errors, durationMs, null);
    }

    /**
     * Creates a failed migration result.
     *
     * @param errorMessage The error message
     * @return A failed result
     */
    static MigrationResult failure(String errorMessage) {
        return new SimpleMigrationResult(false, 0, 0, 0, 0, 0, errorMessage);
    }

    /**
     * Simple implementation of MigrationResult.
     */
    record SimpleMigrationResult(
            boolean isSuccess,
            int playersImported,
            int itemsImported,
            int transactionsImported,
            int errors,
            long durationMs,
            String errorMessage
    ) implements MigrationResult {

        @Override
        public boolean isSuccess() {
            return isSuccess;
        }

        @Override
        public int getPlayersImported() {
            return playersImported;
        }

        @Override
        public int getItemsImported() {
            return itemsImported;
        }

        @Override
        public int getTransactionsImported() {
            return transactionsImported;
        }

        @Override
        public int getErrors() {
            return errors;
        }

        @Override
        public long getDurationMs() {
            return durationMs;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (!isSuccess) {
                return "Migration failed: " + errorMessage;
            }
            return String.format(
                    "Migration completed in %dms - Players: %d, Items: %d, Transactions: %d, Errors: %d",
                    durationMs, playersImported, itemsImported, transactionsImported, errors
            );
        }
    }
}
