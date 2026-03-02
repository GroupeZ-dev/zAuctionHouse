package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.arguments.CommandSellArguments;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.event.events.sell.AuctionPreSellEvent;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommandArgument;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

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

        var economyManager = plugin.getEconomyManager();
        var configuration = plugin.getConfiguration();

        // If no price argument is provided and sell inventory is enabled, open the sell command inventory
        String priceAsString = argAsString(CommandSellArguments.PRICE);

        var itemStack = this.player.getInventory().getItemInMainHand();
        if (itemStack.getType().isAir() && !plugin.getConfiguration().isSellInventoryEnabled()) {
            message(plugin, this.player, Message.SELL_ERROR_AIR);
            return CommandType.DEFAULT;
        }

        int amount = argAsInteger(CommandSellArguments.AMOUNT, itemStack.getAmount());
        amount = amount > itemStack.getAmount() ? itemStack.getAmount() : amount <= 0 ? 1 : amount;

        String economyName = argAsString(CommandSellArguments.ECONOMY, economyManager.getDefaultEconomy(ItemType.AUCTION).getName());
        Optional<AuctionEconomy> optional = economyManager.getEconomy(economyName);
        if (optional.isEmpty()) {
            message(plugin, this.sender, Message.SELL_ERROR_ECONOMY, "%name%", economyName);
            return CommandType.DEFAULT;
        }

        AuctionEconomy auctionEconomy = optional.get();

        var price = configuration.getNumberMultiplicationConfiguration().parseNumber(priceAsString);
        if (price == null) return CommandType.SYNTAX_ERROR;

        if (plugin.getConfiguration().isSellInventoryEnabled()) {
            auctionManager.getSellService().openSellCommandInventory(player, price, auctionEconomy);
            return CommandType.SUCCESS;
        }

        long expiration = configuration.getSellExpiration().getExpiration(player);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        var event = new AuctionPreSellEvent(this.player, amount, expiredAt, itemStack, auctionEconomy, price);
        if (!event.callEvent()) return CommandType.DEFAULT;

        price = event.getPrice();
        amount = event.getAmount();
        expiredAt = event.getExpiredAt();
        auctionEconomy = event.getAuctionEconomy();
        itemStack = event.getItemStack();

        // Clone the item with the correct amount for selling
        var clonedItemStack = itemStack.clone();
        clonedItemStack.setAmount(amount);

        // Use MAIN_HAND_SLOT to indicate the item is in the player's main hand
        Map<Integer, ItemStack> slotItems = Map.of(AuctionSellService.MAIN_HAND_SLOT, clonedItemStack);
        auctionManager.getSellService().sellAuctionItems(player, price, expiredAt, slotItems, auctionEconomy);

        return CommandType.SUCCESS;
    }
}
