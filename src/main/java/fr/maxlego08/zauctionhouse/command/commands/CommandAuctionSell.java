package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.arguments.CommandSellArguments;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommandArgument;
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
        // Le cooldown n'est pose qu'une fois la vente reussie (ici ou dans SellConfirmButton) :
        // une vente annulee ou refusee ne doit pas le consommer.
        this.setDeferredCooldown(true);
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

        AuctionEconomy defaultEconomy = economyManager.getDefaultEconomy(ItemType.AUCTION);
        if (defaultEconomy == null) {
            message(plugin, this.player, Message.SELL_ERROR_DEFAULT_ECONOMY);
            return CommandType.DEFAULT;
        }

        String economyName = argAsString(CommandSellArguments.ECONOMY, defaultEconomy.getName());
        if (economyName == null) return CommandType.DEFAULT;

        Optional<AuctionEconomy> optional = economyManager.getEconomy(economyName);
        if (optional.isEmpty()) {
            message(plugin, this.sender, Message.SELL_ERROR_ECONOMY, "%name%", economyName);
            return CommandType.DEFAULT;
        }

        AuctionEconomy auctionEconomy = optional.get();

        var price = configuration.getNumberMultiplicationConfiguration().parseNumber(priceAsString);
        if (price == null) return CommandType.SYNTAX_ERROR;

        if (plugin.getConfiguration().isSellInventoryEnabled()) {
            // La quantite n'est appliquee qu'a la confirmation, sur l'item alors en main : on
            // transmet donc la valeur demandee brute (0 = tout le stack), plafonnee par SellConfirmButton.
            int sellAmount = argAsString(CommandSellArguments.AMOUNT, null) == null ? 0 : Math.max(1, argAsInteger(CommandSellArguments.AMOUNT, 1));
            auctionManager.getSellService().openSellCommandInventory(player, price, auctionEconomy, sellAmount);
            return CommandType.SUCCESS;
        }

        long expiration = configuration.getSellExpiration().getExpiration(player);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        // AuctionPreSellEvent est tire par SellService.sellAuctionItems (funnel commun a tous les
        // chemins de vente) : surtout pas ici, sinon double declenchement.

        // Clone the item with the correct amount for selling
        var clonedItemStack = itemStack.clone();
        clonedItemStack.setAmount(amount);

        // Use MAIN_HAND_SLOT to indicate the item is in the player's main hand
        Map<Integer, ItemStack> slotItems = Map.of(AuctionSellService.MAIN_HAND_SLOT, clonedItemStack);
        var seller = this.player;
        auctionManager.getSellService().sellAuctionItems(seller, price, expiredAt, slotItems, auctionEconomy).thenAccept(result -> {
            if (result != null && result.success()) plugin.getCommandManager().applyCooldown(seller, this);
        });

        return CommandType.SUCCESS;
    }
}
