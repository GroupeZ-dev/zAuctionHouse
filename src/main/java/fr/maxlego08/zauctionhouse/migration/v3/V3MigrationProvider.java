package fr.maxlego08.zauctionhouse.migration.v3;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.migration.MigrationCallback;
import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import fr.maxlego08.zauctionhouse.api.migration.MigrationResult;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Migration provider for zAuctionHouse V3.
 * <p>
 * This provider handles migration from the previous version of zAuctionHouse.
 * It supports SQLite, MySQL/MariaDB, and JSON storage formats.
 * </p>
 */
public class V3MigrationProvider implements MigrationProvider {

    @Override
    public String getId() {
        return "zauctionhousev3";
    }

    @Override
    public String getDisplayName() {
        return "zAuctionHouse V3";
    }

    @Override
    public String getDescription() {
        return "zAuctionHouse V3 (previous version)";
    }

    @Override
    public List<String> getAliases() {
        return List.of("zah", "zahv3", "v3", "zauctionhouse");
    }

    @Override
    public String getConfigSection() {
        return "zauctionhouse-v3";
    }

    @Override
    public String getDefaultSqlitePath() {
        return "plugins/zAuctionHouseV3/database.db";
    }

    @Override
    public String getDefaultJsonFolder() {
        return "plugins/zAuctionHouseV3";
    }

    @Override
    public String getDefaultTablePrefix() {
        return "zauctionhouse_";
    }

    @Override
    public String validateConfig(ConfigurationSection config) {
        if (config == null) {
            return "Configuration section is missing";
        }

        String sourceType = config.getString("source-type", "SQLITE").toUpperCase();

        return switch (sourceType) {
            case "SQLITE" -> {
                String path = config.getString("sqlite-path", getDefaultSqlitePath());
                File file = new File(path);
                if (!file.exists()) {
                    yield "SQLite database file not found: " + path;
                }
                yield null;
            }
            case "JSON" -> {
                String path = config.getString("json-folder", getDefaultJsonFolder());
                File folder = new File(path);
                if (!folder.exists() || !folder.isDirectory()) {
                    yield "JSON folder not found: " + path;
                }
                yield null;
            }
            case "MYSQL", "MARIADB" -> {
                String host = config.getString("host");
                if (host == null || host.isEmpty()) {
                    yield "Database host is not configured";
                }
                yield null;
            }
            default -> "Unknown source type: " + sourceType;
        };
    }

    @Override
    public CompletableFuture<MigrationResult> migrate(AuctionPlugin plugin, ConfigurationSection config, MigrationCallback callback) {
        // Load the V3 migration config
        V3MigrationConfig migrationConfig = V3MigrationConfig.fromConfig(config, plugin.getDataFolder(), this);

        // Create the migration service
        V3MigrationService service = new V3MigrationService(plugin);

        // Set up the progress callback
        service.onProgress(callback::onProgress);

        // Execute the migration based on source type
        CompletableFuture<V3MigrationResult> migrationFuture = switch (migrationConfig.getSourceType()) {
            case MYSQL, MARIADB -> service.migrateFromSql(
                    migrationConfig.getHost(),
                    migrationConfig.getPort(),
                    migrationConfig.getDatabase(),
                    migrationConfig.getUsername(),
                    migrationConfig.getPassword(),
                    migrationConfig.getTablePrefix()
            );
            case SQLITE -> service.migrateFromSqlite(
                    migrationConfig.getSqlitePath(),
                    migrationConfig.getTablePrefix()
            );
            case JSON -> service.migrateFromJson(migrationConfig.getJsonFolder());
        };

        return migrationFuture.thenApply(result -> {

            // Le verdict du service DOIT etre propage. Le thenApply d'origine mappait
            // inconditionnellement vers MigrationResult.success : une base V3 vide ou des
            // identifiants errones affichaient un succes vert a l'admin, et la branche
            // Message.MIGRATION_FAILED de CommandAuctionAdminMigrate:94 etait du code mort.
            // Les trois providers freres (CrazyAuctions, DonutAuction, ZelAuction) le font deja.
            if (!result.isSuccess()) {
                plugin.getLogger().severe("[Migration] zAuctionHouse V3 migration FAILED: " + result.getErrorMessage());
                return MigrationResult.failure(result.getErrorMessage());
            }

            // `errors` etait affiche a l'admin sans jamais empecher le message de succes :
            // on ne fait pas echouer la migration pour autant (les lignes valides sont importees),
            // mais l'exploitant doit savoir qu'il ne peut PAS encore supprimer ses donnees V3.
            if (result.getErrors() > 0) {
                plugin.getLogger().warning("[Migration] zAuctionHouse V3 migration completed with " + result.getErrors()
                        + " error(s). Read the SEVERE/WARNING lines above BEFORE deleting your V3 data.");
            }

            return MigrationResult.success(
                    result.getPlayersImported(),
                    result.getItemsImported(),
                    result.getTransactionsImported(),
                    result.getErrors(),
                    result.getDurationMs()
            );
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "[Migration] zAuctionHouse V3 migration threw", throwable);
            return MigrationResult.failure(throwable.getMessage());
        });
    }
}
