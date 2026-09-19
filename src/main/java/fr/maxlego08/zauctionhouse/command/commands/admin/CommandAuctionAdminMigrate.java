package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.migration.MigrationCallback;
import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import fr.maxlego08.zauctionhouse.api.migration.MigrationResult;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
import org.bukkit.configuration.ConfigurationSection;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class CommandAuctionAdminMigrate extends VCommand {

    private static final String CONFIRM_ARG = "confirm";
    private static final String FORCE_ARG = "force";

    /**
     * Garde de re-entrance, volontairement STATIQUE : les quatre providers de migration (V3,
     * CrazyAuctions, DonutAuction, ZelAuction) ecrivent tous dans les memes tables. Elle est
     * LOCALE a la JVM : elle ne protege pas du lancement simultane sur deux noeuds — seule la
     * sentinelle en base de C-034 le fait. Ne pas la presenter comme telle dans le changelog.
     */
    private static final AtomicBoolean MIGRATION_RUNNING = new AtomicBoolean(false);

    public CommandAuctionAdminMigrate(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("migrate");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_MIGRATE);
        this.addRequireArg("source", (sender, args) -> new ArrayList<>(plugin.getMigrationRegistry().getProviderIds()));
        this.addOptionalArg("confirm", (sender, args) -> List.of("confirm"));
        this.addOptionalArg("force", (sender, args) -> List.of("force"));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String sourceArg = this.argAsString(0, "");
        String confirmArg = this.argAsString(1, "");
        String forceArg = this.argAsString(2, "");

        // Parse migration source from registry
        Optional<MigrationProvider> providerOptional = plugin.getMigrationRegistry().getProvider(sourceArg);
        if (providerOptional.isEmpty()) {
            message(plugin, sender, Message.MIGRATION_INVALID_SOURCE, "%source%", sourceArg);
            message(plugin, sender, Message.MIGRATION_AVAILABLE_SOURCES, "%sources%", plugin.getMigrationRegistry().getProviderIds().stream().map(id -> "&f" + id).collect(Collectors.joining("&7, ")));
            return CommandType.SUCCESS;
        }

        MigrationProvider provider = providerOptional.get();

        // Check if migration is configured for this source
        ConfigurationSection migrationSection = provider.getConfigSection() != null
                ? plugin.getConfig().getConfigurationSection("migration." + provider.getConfigSection())
                : null;

        if (migrationSection == null && provider.getConfigSection() != null) {
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

        // IDEMPOTENCE. La migration n'a AUCUNE cle d'unicite cote V4 : la rejouer duplique tous
        // les items ET tout l'argent PENDING (C-034). La sentinelle vit en base PARTAGEE, elle
        // bloque donc aussi le scenario deux-noeuds, ce qu'un drapeau memoire ne peut pas faire.
        // Elle ne desamorce pas deux commandes lancees a la meme seconde : le premier a committer
        // gagne. Le serveur est vide a cet instant (garde ci-dessous), la lecture bloquante sur le
        // thread principal est donc sans consequence.
        boolean force = forceArg.equalsIgnoreCase(FORCE_ARG);
        Optional<MigrationStateRepository.MigrationStateDTO> migrationState = plugin.getStorageManager().with(MigrationStateRepository.class).select(provider.getId());
        if (migrationState.isPresent() && !force) {
            MigrationStateRepository.MigrationStateDTO state = migrationState.get();
            // TODO : basculer sur Message.MIGRATION_ALREADY_DONE des que la cle existe dans l'enum
            // Message et dans les six messages.yml. MIGRATION_FAILED porte le meme verdict sans
            // rien inventer cote API.
            message(plugin, sender, Message.MIGRATION_FAILED, "%error%",
                    "Migration from " + provider.getDisplayName() + " has already been run on "
                            + state.server_name() + " (" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(state.migrated_at()))
                            + ", " + state.items_imported() + " items). Running it again would duplicate every item and every pending payment. "
                            + "If you really know what you are doing: /ah admin migrate " + provider.getId() + " confirm force");
            return CommandType.SUCCESS;
        }

        // La purge/reconstruction des storages memoire par loadItems() ne rend le rechargement
        // a chaud COHERENT que si personne n'agit pendant : les futures d'achat et de retrait
        // deja en vol detiennent des references vers les objets purges et continueraient de
        // muter des orphelins. On refuse donc la migration tant qu'un joueur est connecte
        // (C-044).
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        if (onlinePlayers > 0) {
            message(plugin, sender, Message.MIGRATION_FAILED, "%error%",
                    onlinePlayers + " player(s) are connected. Run the migration on an empty server.");
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

        if (!MIGRATION_RUNNING.compareAndSet(false, true)) {
            message(plugin, sender, Message.MIGRATION_FAILED, "%error%", "A migration is already running.");
            return CommandType.SUCCESS;
        }

        // Executor mono-thread dedie : la migration monopolisait l'asyncExecutor du plugin,
        // partage avec tout le gameplay (achats, ventes, retraits) — C-087. Daemon pour ne
        // jamais retenir l'arret du serveur.
        ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "zAuctionHouse-Migration");
            thread.setDaemon(true);
            return thread;
        });

        CompletableFuture<MigrationResult> migrationFuture;
        try {
            migrationFuture = CompletableFuture
                    .supplyAsync(() -> provider.migrate(plugin, migrationSection, callback), migrationExecutor)
                    .thenCompose(future -> future);
        } catch (RuntimeException exception) {
            // Un echec SYNCHRONE de provider.migrate laisserait sinon le drapeau coince a true
            // jusqu'au prochain redemarrage.
            MIGRATION_RUNNING.set(false);
            migrationExecutor.shutdown();
            throw exception;
        }

        // whenComplete attache AVANT le thenAccept existant : il doit s'executer quel que soit
        // le sort de la chaine avale.
        migrationFuture = migrationFuture.whenComplete((result, throwable) -> {
            MIGRATION_RUNNING.set(false);
            migrationExecutor.shutdown();
        });

        migrationFuture.thenAccept(result -> {

            // Ecriture de la sentinelle UNIQUEMENT en cas de succes : posee apres un echec, elle
            // bloquerait definitivement la reprise et l'argent V3 serait perdu pour toujours.
            // Volontairement hors du runNextTick : c'est un acces JDBC bloquant.
            if (result.isSuccess()) {
                try {
                    plugin.getStorageManager().with(MigrationStateRepository.class).markMigrated(
                            provider.getId(), plugin.getConfiguration().getServerName(),
                            result.getPlayersImported(), result.getItemsImported(), result.getTransactionsImported());
                } catch (Exception exception) {
                    plugin.getLogger().severe("Migration succeeded but the idempotency sentinel could NOT be written: "
                            + exception.getMessage() + ". Running the command again would duplicate everything.");
                }
            }

            plugin.getScheduler().runNextTick(wrappedTask -> {
                if (result.isSuccess()) {
                    message(plugin, sender, Message.MIGRATION_SUCCESS, "%source%", provider.getDisplayName(), "%players%", String.valueOf(result.getPlayersImported()), "%items%", String.valueOf(result.getItemsImported()), "%transactions%", String.valueOf(result.getTransactionsImported()), "%errors%", String.valueOf(result.getErrors()), "%duration%", String.valueOf(result.getDurationMs()));

                    // Reload items after migration
                    plugin.getStorageManager().loadItems();
                } else {
                    // Depuis 6.1 le verdict remonte est le VRAI verdict du provider : une base V3
                    // vide ou injoignable affiche desormais MIGRATION_FAILED la ou un succes vert
                    // etait affiche. C'est l'effet voulu.
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
