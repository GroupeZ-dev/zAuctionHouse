package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.migration.MigrationCallback;
import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import fr.maxlego08.zauctionhouse.api.migration.MigrationResult;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Command to migrate data from other auction plugins to zAuctionHouse V4.
 * <p>
 * Usage: /ah admin migrate <source> [confirm]
 * <p>
 * Available sources are dynamically loaded from the {@link fr.maxlego08.zauctionhouse.api.migration.MigrationRegistry}.
 * External plugins can register their own migration providers.
 */
public class CommandAuctionAdminMigrate extends VCommand {

    private static final String CONFIRM_ARG = "confirm";

    public CommandAuctionAdminMigrate(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("migrate");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_MIGRATE);
        this.addRequireArg("source", (sender, args) -> new ArrayList<>(plugin.getMigrationRegistry().getProviderIds()));
        this.addOptionalArg("confirm", (sender, args) -> List.of("confirm"));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String sourceArg = this.argAsString(0, "");
        String confirmArg = this.argAsString(1, "");

        // Parse migration source from registry
        Optional<MigrationProvider> providerOptional = plugin.getMigrationRegistry().getProvider(sourceArg);
        if (providerOptional.isEmpty()) {
            message(plugin, sender, Message.MIGRATION_INVALID_SOURCE, "%source%", sourceArg);
            message(plugin, sender, Message.MIGRATION_AVAILABLE_SOURCES, "%sources%", plugin.getMigrationRegistry().getProviderIds().stream().map(id -> "&f" + id).collect(Collectors.joining("&7, ")));
            return CommandType.SUCCESS;
        }

        MigrationProvider provider = providerOptional.get();

        // Check if migration is configured for this source
        ConfigurationSection migrationSection = plugin.getConfig().getConfigurationSection("migration." + provider.getConfigSection());

        if (migrationSection == null) {
            message(plugin, sender, Message.MIGRATION_NOT_CONFIGURED, "%source%", provider.getDisplayName());
            return CommandType.SUCCESS;
        }

        // Validate configuration
        String validationError = provider.validateConfig(migrationSection);
        if (validationError != null) {
            message(plugin, sender, Message.MIGRATION_FAILED, "%error%", validationError);
            return CommandType.SUCCESS;
        }

        // Check for confirm argument
        if (!confirmArg.equalsIgnoreCase(CONFIRM_ARG)) {
            // Show migration info and ask for confirmation
            message(plugin, sender, Message.MIGRATION_INFO, "%source%", provider.getDisplayName(), "%details%", provider.getDescription());
            message(plugin, sender, Message.MIGRATION_CONFIRM, "%source%", provider.getId());
            return CommandType.SUCCESS;
        }

        // Start migration
        message(plugin, sender, Message.MIGRATION_STARTED, "%source%", provider.getDisplayName());

        // Create progress callback
        MigrationCallback callback = progress -> {
            if (sender != null) {
                message(plugin, sender, Message.MIGRATION_PROGRESS, "%progress%", progress);
            }
        };

        // Execute migration
        CompletableFuture<MigrationResult> migrationFuture = provider.migrate(plugin, migrationSection, callback);

        migrationFuture.thenAccept(result -> {
            plugin.getScheduler().runNextTick(wrappedTask -> {
                if (result.isSuccess()) {
                    message(plugin, sender, Message.MIGRATION_SUCCESS, "%source%", provider.getDisplayName(), "%players%", String.valueOf(result.getPlayersImported()), "%items%", String.valueOf(result.getItemsImported()), "%transactions%", String.valueOf(result.getTransactionsImported()), "%errors%", String.valueOf(result.getErrors()), "%duration%", String.valueOf(result.getDurationMs()));

                    // Reload items after migration
                    plugin.getStorageManager().loadItems();
                } else {
                    message(plugin, sender, Message.MIGRATION_FAILED, "%error%", result.getErrorMessage());
                }
            });
        }).exceptionally(throwable -> {
            plugin.getScheduler().runNextTick(wrappedTask -> {
                message(plugin, sender, Message.MIGRATION_FAILED, "%error%", throwable.getMessage());
            });
            return null;
        });

        return CommandType.SUCCESS;
    }
}
