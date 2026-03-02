package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.command.commands.admin.cache.CommandAuctionAdminCache;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;

public class CommandAuctionAdmin extends VCommand {

    public CommandAuctionAdmin(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN);
        this.setConsoleCanUse(false);

        this.addSubCommand("admin");
        this.addSubCommand(new CommandAuctionAdminOpen(plugin));
        this.addSubCommand(new CommandAuctionAdminAdd(plugin));
        this.addSubCommand(new CommandAuctionAdminGenerate(plugin));
        this.addSubCommand(new CommandAuctionAdminCache(plugin));
        this.addSubCommand(new CommandAuctionAdminHistory(plugin));
        this.addSubCommand(new CommandAuctionAdminReload(plugin));
        this.addSubCommand(new CommandAuctionAdminMigrate(plugin));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        this.syntaxMessage(this.sender);
        return CommandType.SYNTAX_ERROR;
    }

}
