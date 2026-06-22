package fr.maxlego08.zauctionhouse.command.commands.admin.logs;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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

            long deleted = plugin.getStorageManager().with(LogRepository.class).deleteByPlayer(uuid);
            message(plugin, this.sender, Message.ADMIN_LOGS_PLAYER_SUCCESS, "%amount%", String.valueOf(deleted), "%player%", targetName);
        });

        return CommandType.SUCCESS;
    }
}
