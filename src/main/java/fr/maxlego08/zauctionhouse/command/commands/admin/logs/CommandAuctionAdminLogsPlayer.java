package fr.maxlego08.zauctionhouse.command.commands.admin.logs;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class CommandAuctionAdminLogsPlayer extends VCommand {

    public CommandAuctionAdminLogsPlayer(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_LOGS_PLAYER);
        this.setConsoleCanUse(true);

        this.addSubCommand("player");
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String targetName = argAsString(0);
        if (targetName == null) return CommandType.SYNTAX_ERROR;

        plugin.getStorageManager().findUniqueId(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                message(plugin, this.sender, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
                return;
            }

            long deleted;
            try {
                deleted = plugin.getStorageManager().with(LogRepository.class).deleteByPlayer(uuid);
            } catch (RuntimeException exception) {
                // Sarah leve une DatabaseException (RuntimeException) : sans ce catch,
                // elle s'echapperait du bloc asynchrone et l'admin n'aurait AUCUN retour.
                plugin.getLogger().log(Level.SEVERE, "Failed to purge the logs of " + targetName, exception);
                // Et sans ce retour a l'emetteur, une purge RATEE s'afficherait comme un succes
                // a zero ligne : l'admin croirait les logs du joueur deja supprimes.
                this.sender.sendMessage("Failed to purge the logs of " + targetName + ": " + exception.getMessage() + ". See the server console for details.");
                return;
            }
            message(plugin, this.sender, Message.ADMIN_LOGS_PLAYER_SUCCESS, "%amount%", String.valueOf(deleted), "%player%", targetName);
        });

        return CommandType.SUCCESS;
    }
}
