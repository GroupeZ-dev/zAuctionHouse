package fr.maxlego08.zauctionhouse.command.commands.admin.option;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class CommandAuctionAdminOptionSet extends VCommand {

    public CommandAuctionAdminOptionSet(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_OPTION_SET);
        this.setConsoleCanUse(true);

        this.addSubCommand("set");
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.addRequireArg("option", (sender, args) -> Arrays.stream(PlayerOption.values()).map(PlayerOption::getKey).toList());
        this.addRequireArg("value", (sender, args) -> List.of("true", "false"));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String targetName = argAsString(0);
        if (targetName == null) return CommandType.SYNTAX_ERROR;

        String optionName = argAsString(1);
        if (optionName == null) return CommandType.SYNTAX_ERROR;

        String valueStr = argAsString(2);
        if (valueStr == null) return CommandType.SYNTAX_ERROR;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            message(plugin, this.sender, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
            return CommandType.DEFAULT;
        }

        PlayerOption option = PlayerOption.fromKey(optionName);
        if (option == null) {
            message(plugin, this.sender, Message.ADMIN_OPTION_SET, "%option%", optionName, "%value%", "unknown", "%player%", targetName);
            return CommandType.DEFAULT;
        }

        boolean value = Boolean.parseBoolean(valueStr);
        this.auctionManager.getOptionService().setOption(target.getUniqueId(), option, value);
        message(plugin, this.sender, Message.ADMIN_OPTION_SET, "%option%", option.getKey(), "%value%", String.valueOf(value), "%player%", target.getName());

        return CommandType.SUCCESS;
    }
}
