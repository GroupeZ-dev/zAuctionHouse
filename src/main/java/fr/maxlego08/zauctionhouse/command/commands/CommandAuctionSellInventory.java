package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.arguments.CommandSellInventoryArguments;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommandArgument;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.entity.Player;

import java.util.Optional;

public class CommandAuctionSellInventory extends VCommandArgument<CommandSellInventoryArguments> {

    public CommandAuctionSellInventory(AuctionPlugin plugin) {
        super(plugin, CommandSellInventoryArguments.class);
        this.setPermission(Permission.ZAUCTIONHOUSE_SELL);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_SELL_INVENTORY);
        this.setConsoleCanUse(false);
    }

    @Override
    public void createCommandArguments(AuctionPlugin plugin, Class<CommandSellInventoryArguments> enumClass) {
        forEachArgument("commands.sell-inventory.", commandArgumentConfiguration -> (sender, args) -> commandArgumentConfiguration.autoCompletion());
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        var economyManager = plugin.getEconomyManager();
        var configuration = plugin.getConfiguration();

        String economyName = argAsString(CommandSellInventoryArguments.ECONOMY, economyManager.getDefaultEconomy(ItemType.AUCTION).getName());
        Optional<AuctionEconomy> optional = economyManager.getEconomy(economyName);
        if (optional.isEmpty()) {
            message(plugin, this.sender, Message.SELL_ERROR_ECONOMY, "%name%", economyName);
            return CommandType.DEFAULT;
        }

        AuctionEconomy auctionEconomy = optional.get();

        String priceAsString = argAsString(CommandSellInventoryArguments.PRICE, "100");
        var price = configuration.getNumberMultiplicationConfiguration().parseNumber(priceAsString);
        if (price == null) return CommandType.SYNTAX_ERROR;

        long expiration = configuration.getSellExpiration().getExpiration(player);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        plugin.getAuctionManager().getSellService().openSellInventory((Player) this.sender, price, expiredAt, auctionEconomy);

        return CommandType.SUCCESS;
    }
}
