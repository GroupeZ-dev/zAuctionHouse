package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.command.commands.admin.CommandAuctionAdmin;
import fr.maxlego08.zauctionhouse.command.commands.admin.CommandAuctionAdminReload;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.entity.Player;

public class CommandAuction extends VCommand {

    public CommandAuction(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_USE);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION);

        this.addSubCommand(new CommandAuctionSell(plugin));
        this.addSubCommand(new CommandAuctionClaim(plugin));
        this.addSubCommand(new CommandAuctionPage(plugin));
        this.addSubCommand(new CommandAuctionAdmin(plugin));

        plugin.getConfiguration().getInventoryCommands().forEach((configuration) -> this.addSubCommand(new CommandAuctionInventories(plugin, configuration)));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        if (this.sender instanceof Player playerSender) {
            this.auctionManager.openMainAuction(playerSender);
        } else {
            this.syntaxMessage(this.sender);
        }

        return CommandType.SUCCESS;
    }
}
