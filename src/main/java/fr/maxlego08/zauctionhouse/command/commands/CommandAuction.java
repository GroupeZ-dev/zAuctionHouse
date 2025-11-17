package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;

public class CommandAuction extends VCommand {

    public CommandAuction(AuctionPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZAUCTIONHOUSE_USE);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        // ToDo

        return CommandType.SUCCESS;
    }
}
