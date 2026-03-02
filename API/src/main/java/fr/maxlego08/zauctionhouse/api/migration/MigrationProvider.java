package fr.maxlego08.zauctionhouse.api.migration;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for migration providers.
 * <p>
 * Implement this interface to add support for migrating data from another auction plugin.
 * Register your implementation using {@link MigrationRegistry#register(MigrationProvider)}.
 * </p>
 *
 * <h2>Example Implementation:</h2>
 * <pre>{@code
 * public class AxAuctionsMigrationProvider implements MigrationProvider {
 *
 *     @Override
 *     public String getId() {
 *         return "axauctions";
 *     }
 *
 *     @Override
 *     public String getDisplayName() {
 *         return "AxAuctions";
 *     }
 *
 *     @Override
 *     public List<String> getAliases() {
 *         return List.of("axa", "axauction");
 *     }
 *
 *     @Override
 *     public CompletableFuture<MigrationResult> migrate(AuctionPlugin plugin, ConfigurationSection config, MigrationCallback callback) {
 *         // Your migration logic here
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration:</h2>
 * <pre>{@code
 * // In your plugin's onEnable()
 * AuctionPlugin auctionPlugin = (AuctionPlugin) Bukkit.getPluginManager().getPlugin("zAuctionHouse");
 * auctionPlugin.getMigrationRegistry().register(new AxAuctionsMigrationProvider());
 * }</pre>
 */
public interface MigrationProvider {

    /**
     * Gets the unique identifier for this migration provider.
     * This is used in commands and configuration.
     *
     * @return The provider ID (lowercase, no spaces)
     */
    String getId();

    /**
     * Gets the display name for this migration provider.
     * This is shown in messages to users.
     *
     * @return The display name
     */
    String getDisplayName();

    /**
     * Gets the description of this migration provider.
     *
     * @return A short description
     */
    String getDescription();

    /**
     * Gets alternative names/aliases for this provider.
     * Users can use any of these in the migrate command.
     *
     * @return List of aliases
     */
    List<String> getAliases();

    /**
     * Gets the configuration section name for this provider.
     * The configuration will be read from "migration.<config-section>".
     *
     * @return The configuration section name
     */
    default String getConfigSection() {
        return getId();
    }

    /**
     * Gets the default SQLite database path for this provider.
     *
     * @return Default SQLite path
     */
    String getDefaultSqlitePath();

    /**
     * Gets the default JSON folder path for this provider.
     *
     * @return Default JSON folder path
     */
    String getDefaultJsonFolder();

    /**
     * Gets the default table prefix for this provider.
     *
     * @return Default table prefix
     */
    String getDefaultTablePrefix();

    /**
     * Checks if this provider matches the given input string.
     *
     * @param input The input to check (ID or alias)
     * @return true if this provider matches
     */
    default boolean matches(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return getId().equals(lower) ||
               getDisplayName().equalsIgnoreCase(input) ||
               getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(lower));
    }

    /**
     * Validates the configuration for this migration.
     *
     * @param config The configuration section
     * @return null if valid, or an error message if invalid
     */
    default String validateConfig(ConfigurationSection config) {
        return null; // Default: no validation errors
    }

    /**
     * Executes the migration process.
     *
     * @param plugin   The zAuctionHouse plugin instance
     * @param config   The configuration section for this migration
     * @param callback Callback for progress updates
     * @return A CompletableFuture that completes with the migration result
     */
    CompletableFuture<MigrationResult> migrate(AuctionPlugin plugin, ConfigurationSection config, MigrationCallback callback);
}
