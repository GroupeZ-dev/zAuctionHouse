package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;

public class CommandAuctionClaim extends VCommand {

    public CommandAuctionClaim(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_USE);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_CLAIM);
        this.addSubCommand("claim", "c");
        this.setConsoleCanUse(false);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        plugin.getAuctionManager().getClaimService().claimMoney(this.player);
        return CommandType.SUCCESS;
    }
}
