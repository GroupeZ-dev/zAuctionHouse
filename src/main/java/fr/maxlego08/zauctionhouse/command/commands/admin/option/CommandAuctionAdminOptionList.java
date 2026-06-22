package fr.maxlego08.zauctionhouse.command.commands.admin.option;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public class CommandAuctionAdminOptionList extends VCommand {

    public CommandAuctionAdminOptionList(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_OPTION_LIST);
        this.setConsoleCanUse(true);

        this.addSubCommand("list");
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

        Map<PlayerOption, String> options = this.auctionManager.getOptionService().getPlayerOptions(target.getUniqueId());
        message(plugin, this.sender, Message.ADMIN_OPTION_LIST_HEADER, "%player%", target.getName());

        for (PlayerOption option : PlayerOption.values()) {
            String value = options.getOrDefault(option, option.getDefaultValue());
            message(plugin, this.sender, Message.OPTION_LIST_ENTRY, "%option%", option.getKey(), "%value%", value);
        }

        return CommandType.SUCCESS;
    }
}
