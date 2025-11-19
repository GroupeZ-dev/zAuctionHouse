package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandArgumentConfiguration;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CollectionBiConsumer;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.entity.Player;

public class CommandAuctionSell extends VCommand {

    public CommandAuctionSell(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_SELL);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_SELL);

        var commandArgument = plugin.getConfiguration().loadCommandConfiguration("commands.sell.");
        this.addSubCommand(commandArgument.aliases());

        for (CommandArgumentConfiguration commandArgumentConfiguration : commandArgument.arguments()) {

            CollectionBiConsumer autoCompletion = (sender, args) -> commandArgumentConfiguration.autoCompletion().stream().map(line -> {
                if (line.contains("%max-stack-size%") && sender instanceof Player playerSender) {
                    var itemStack = playerSender.getInventory().getItemInMainHand();
                    return line.replace("%max-stack-size%", String.valueOf(itemStack.getType().isAir() ? 0 : itemStack.getMaxStackSize()));
                }
                return line;
            }).distinct().toList();

            if (commandArgumentConfiguration.required()) {
                this.addRequireArg(commandArgumentConfiguration.name(), autoCompletion);
            } else {
                this.addOptionalArg(commandArgumentConfiguration.name(), autoCompletion);
            }
        }
        this.setConsoleCanUse(false);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        System.out.println("Sell");

        return CommandType.SUCCESS;
    }
}
