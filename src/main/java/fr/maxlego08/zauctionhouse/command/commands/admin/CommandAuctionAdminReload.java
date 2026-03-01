package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;

public class CommandAuctionAdminReload extends VCommand {

    public CommandAuctionAdminReload(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("reload", "rl");
        this.setPermission(Permission.ZAUCTIONHOUSE_RELOAD);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_RELOAD);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        plugin.reload();
        message(plugin, sender, Message.RELOAD_SUCCESS);

        return CommandType.SUCCESS;
    }
}
