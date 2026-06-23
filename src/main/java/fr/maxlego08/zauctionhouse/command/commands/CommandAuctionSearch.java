package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;

public class CommandAuctionSearch extends VCommand {

    public CommandAuctionSearch(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_USE);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_SEARCH);
        this.setConsoleCanUse(false);
        this.setIgnoreArgs(true);

        var commandConfig = plugin.getConfiguration().loadSimpleCommandConfiguration("commands.search.");
        this.addSubCommand(commandConfig.aliases());

        for (var argument : commandConfig.arguments()) {
            if (argument.required()) {
                this.addRequireArg(argument.displayName(), (sender, args) -> argument.autoCompletion());
            } else {
                this.addOptionalArg(argument.displayName(), (sender, args) -> argument.autoCompletion());
            }
        }
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 1; i < this.args.length; i++) {
            if (i > 1) queryBuilder.append(" ");
            queryBuilder.append(this.args[i]);
        }

        String query = queryBuilder.toString();
        if (plugin instanceof ZAuctionPlugin zAuctionPlugin) {
            zAuctionPlugin.getSearchDialogService().openSearchDialog(this.player, query);
        }
        return CommandType.SUCCESS;
    }
}
