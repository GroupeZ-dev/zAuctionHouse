package fr.maxlego08.zauctionhouse.api.migration;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for migration providers.
 * <p>
 * This registry manages all available migration providers.
 * External plugins can register their own providers to add support
 * for migrating from their auction plugin format.
 * </p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Get the registry from zAuctionHouse
 * MigrationRegistry registry = auctionPlugin.getMigrationRegistry();
 *
 * // Register a custom provider
 * registry.register(new MyCustomMigrationProvider());
 *
 * // Check if a provider exists
 * Optional<MigrationProvider> provider = registry.getProvider("mycustom");
 * }</pre>
 */
public interface MigrationRegistry {

    /**
     * Registers a migration provider.
     *
     * @param provider The provider to register
     * @throws IllegalArgumentException if a provider with the same ID already exists
     */
    void register(MigrationProvider provider);

    /**
     * Unregisters a migration provider.
     *
     * @param providerId The ID of the provider to unregister
     * @return true if the provider was unregistered, false if it didn't exist
     */
    boolean unregister(String providerId);

    /**
     * Gets a provider by its ID or alias.
     *
     * @param idOrAlias The ID or alias to search for
     * @return The provider, or empty if not found
     */
    Optional<MigrationProvider> getProvider(String idOrAlias);

    /**
     * Gets all registered providers.
     *
     * @return Collection of all providers
     */
    Collection<MigrationProvider> getProviders();

    /**
     * Gets all provider IDs for tab completion.
     *
     * @return Collection of provider IDs
     */
    Collection<String> getProviderIds();

    /**
     * Checks if a provider with the given ID exists.
     *
     * @param providerId The provider ID
     * @return true if the provider exists
     */
    default boolean hasProvider(String providerId) {
        return getProvider(providerId).isPresent();
    }
}
