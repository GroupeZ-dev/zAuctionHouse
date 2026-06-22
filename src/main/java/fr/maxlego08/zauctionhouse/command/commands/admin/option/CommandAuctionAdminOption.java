package fr.maxlego08.zauctionhouse.command.commands.admin.option;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;

public class CommandAuctionAdminOption extends VCommand {

    public CommandAuctionAdminOption(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_OPTION);
        this.setConsoleCanUse(true);

        this.addSubCommand("option");
        this.addSubCommand(new CommandAuctionAdminOptionSet(plugin));
        this.addSubCommand(new CommandAuctionAdminOptionList(plugin));
        this.addSubCommand(new CommandAuctionAdminOptionReset(plugin));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        this.syntaxMessage(this.sender);
        return CommandType.DEFAULT;
    }
}
