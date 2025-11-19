package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.arguments.CommandSellArguments;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.AuctionItemType;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommandArgument;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.entity.Player;

public class CommandAuctionSell extends VCommandArgument<CommandSellArguments> {

    public CommandAuctionSell(AuctionPlugin plugin) {
        super(plugin, CommandSellArguments.class);
        this.setPermission(Permission.ZAUCTIONHOUSE_SELL);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_SELL);
        this.setConsoleCanUse(false);
    }

    @Override
    public void createCommandArguments(AuctionPlugin plugin, Class<CommandSellArguments> enumClass) {
        forEachArgument("commands.sell.", commandArgumentConfiguration -> (sender, args) -> commandArgumentConfiguration.autoCompletion().stream().map(line -> {
            if (line.contains("%max-stack-size%") && sender instanceof Player playerSender) {
                var itemStack = playerSender.getInventory().getItemInMainHand();
                return line.replace("%max-stack-size%", String.valueOf(itemStack.getType().isAir() ? 0 : itemStack.getMaxStackSize()));
            }
            return line;
        }).distinct().toList());
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        var itemStack = this.player.getInventory().getItemInMainHand();
        if (itemStack.getType().isAir()) {
            message(plugin, this.player, Message.SELL_ERROR_AIR);
            return CommandType.DEFAULT;
        }

        int amount = argAsInteger(CommandSellArguments.AMOUNT, itemStack.getAmount());
        amount = amount > itemStack.getAmount() ? itemStack.getAmount() : amount <= 0 ? 1 : amount;

        var economyManager = plugin.getEconomyManager();
        String economyName = argAsString(CommandSellArguments.ECONOMY, economyManager.getDefaultEconomy(AuctionItemType.SELL).getName());

        return CommandType.SUCCESS;
    }
}
