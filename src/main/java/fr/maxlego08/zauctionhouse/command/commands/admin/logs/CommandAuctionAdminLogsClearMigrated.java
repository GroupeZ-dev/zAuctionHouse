package fr.maxlego08.zauctionhouse.command.commands.admin.logs;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;

import java.util.logging.Level;

public class CommandAuctionAdminLogsClearMigrated extends VCommand {

    public CommandAuctionAdminLogsClearMigrated(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_LOGS_CLEAR_MIGRATED);
        this.setConsoleCanUse(true);

        this.addSubCommand("clear-migrated");
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        plugin.getScheduler().runAsync(wrappedTask -> {
            long deleted;
            try {
                deleted = plugin.getStorageManager().with(LogRepository.class).deleteMigrated();
            } catch (RuntimeException exception) {
                // Sarah leve une DatabaseException (RuntimeException) : sans ce catch,
                // elle s'echapperait du bloc async et l'admin n'aurait AUCUN retour.
                plugin.getLogger().log(Level.SEVERE, "Failed to clear the migrated logs", exception);
                // Et sans ce retour a l'emetteur, une purge RATEE s'afficherait comme un succes
                // a zero ligne : l'admin croirait les logs migres deja supprimes.
                this.sender.sendMessage("Failed to clear the migrated logs: " + exception.getMessage() + ". See the server console for details.");
                return;
            }
            message(plugin, this.sender, Message.ADMIN_LOGS_CLEAR_MIGRATED_SUCCESS, "%amount%", String.valueOf(deleted));
        });

        return CommandType.SUCCESS;
    }
}
