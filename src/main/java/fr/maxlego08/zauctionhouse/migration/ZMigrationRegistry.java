package fr.maxlego08.zauctionhouse.migration;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import fr.maxlego08.zauctionhouse.api.migration.MigrationRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the MigrationRegistry.
 */
public class ZMigrationRegistry implements MigrationRegistry {

    private final AuctionPlugin plugin;
    private final Map<String, MigrationProvider> providers = new ConcurrentHashMap<>();

    public ZMigrationRegistry(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(MigrationProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }

        String id = provider.getId().toLowerCase();

        if (providers.containsKey(id)) {
            throw new IllegalArgumentException("A migration provider with ID '" + id + "' is already registered");
        }

        providers.put(id, provider);
        plugin.getLogger().info("Registered migration provider: " + provider.getDisplayName() + " (" + id + ")");
    }

    @Override
    public boolean unregister(String providerId) {
        if (providerId == null) {
            return false;
        }

        MigrationProvider removed = providers.remove(providerId.toLowerCase());
        if (removed != null) {
            plugin.getLogger().info("Unregistered migration provider: " + removed.getDisplayName());
            return true;
        }
        return false;
    }

    @Override
    public Optional<MigrationProvider> getProvider(String idOrAlias) {
        if (idOrAlias == null) {
            return Optional.empty();
        }

        // First, try direct lookup by ID
        MigrationProvider direct = providers.get(idOrAlias.toLowerCase());
        if (direct != null) {
            return Optional.of(direct);
        }

        // Then, search through all providers for matching aliases
        for (MigrationProvider provider : providers.values()) {
            if (provider.matches(idOrAlias)) {
                return Optional.of(provider);
            }
        }

        return Optional.empty();
    }

    @Override
    public Collection<MigrationProvider> getProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }

    @Override
    public Collection<String> getProviderIds() {
        return Collections.unmodifiableCollection(providers.keySet());
    }
}
