package fr.maxlego08.zauctionhouse.command.commands.admin.logs;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;

import java.util.List;
import java.util.logging.Level;

public class CommandAuctionAdminLogsPurge extends VCommand {

    public CommandAuctionAdminLogsPurge(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_LOGS_PURGE);
        this.setConsoleCanUse(true);

        this.addSubCommand("purge");
        this.addRequireArg("days", (sender, args) -> List.of("7", "30", "60", "90", "180", "365"));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        int days = argAsInteger(0, 0);
        if (days <= 0) {
            message(plugin, this.sender, Message.ADMIN_LOGS_INVALID_DAYS);
            return CommandType.DEFAULT;
        }

        long olderThanMs = days * 86_400_000L;

        plugin.getScheduler().runAsync(wrappedTask -> {
            long deleted;
            try {
                deleted = plugin.getStorageManager().with(LogRepository.class).deleteOlderThan(olderThanMs);
            } catch (RuntimeException exception) {
                // Sarah leve une DatabaseException (RuntimeException) : sans ce catch,
                // elle s'echapperait du bloc async et l'admin n'aurait AUCUN retour.
                plugin.getLogger().log(Level.SEVERE, "Failed to purge logs older than " + days + " days", exception);
                // Et sans ce retour a l'emetteur, une purge RATEE s'afficherait comme un succes
                // a zero ligne : l'admin croirait la table deja propre.
                this.sender.sendMessage("Failed to purge the logs: " + exception.getMessage() + ". See the server console for details.");
                return;
            }
            message(plugin, this.sender, Message.ADMIN_LOGS_PURGE_SUCCESS, "%amount%", String.valueOf(deleted), "%days%", String.valueOf(days));
        });

        return CommandType.SUCCESS;
    }
}
