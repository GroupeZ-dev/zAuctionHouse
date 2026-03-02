package fr.maxlego08.zauctionhouse.api.migration;

/**
 * Callback interface for migration progress updates.
 */
@FunctionalInterface
public interface MigrationCallback {

    /**
     * Called when the migration progress is updated.
     *
     * @param message The progress message
     */
    void onProgress(String message);

    /**
     * Creates a no-op callback that ignores all progress updates.
     *
     * @return A no-op callback
     */
    static MigrationCallback empty() {
        return message -> {};
    }
}
