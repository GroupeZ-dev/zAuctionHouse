package fr.maxlego08.zauctionhouse.command.commands.admin.logs;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;

public class CommandAuctionAdminLogs extends VCommand {

    public CommandAuctionAdminLogs(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_LOGS);
        this.setConsoleCanUse(true);

        this.addSubCommand("logs");
        this.addSubCommand(new CommandAuctionAdminLogsPurge(plugin));
        this.addSubCommand(new CommandAuctionAdminLogsPlayer(plugin));
        this.addSubCommand(new CommandAuctionAdminLogsClearMigrated(plugin));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        this.syntaxMessage(this.sender);
        return CommandType.DEFAULT;
    }
}
