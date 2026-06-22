package fr.maxlego08.zauctionhouse.command.commands.admin.option;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CommandAuctionAdminOptionReset extends VCommand {

    public CommandAuctionAdminOptionReset(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_OPTION_RESET);
        this.setConsoleCanUse(true);

        this.addSubCommand("reset");
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String targetName = argAsString(0);
        if (targetName == null) return CommandType.SYNTAX_ERROR;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            message(plugin, this.sender, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
            return CommandType.DEFAULT;
        }

        this.auctionManager.getOptionService().resetPlayerOptions(target.getUniqueId());
        message(plugin, this.sender, Message.ADMIN_OPTION_RESET, "%player%", target.getName());

        return CommandType.SUCCESS;
    }
}
