package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.AuctionItemType;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;

public class CommandAuctionAdminAdd extends VCommand {

    public CommandAuctionAdminAdd(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("add");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS);
        this.setDescription(Message.ADMIN_ITEM_ADDED);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.addRequireArg("type", (sender, args) -> java.util.List.of("listed", "expired", "purchased"));
        this.addOptionalArg("price", (sender, args) -> java.util.List.of("1000", "0"));
        this.setConsoleCanUse(false);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        if (!(this.sender instanceof Player admin)) {
            return CommandType.DEFAULT;
        }

        String targetName = argAsString(0);
        if (targetName == null) {
            this.auctionManager.message(admin, Message.ADMIN_TARGET_REQUIRED);
            return CommandType.SYNTAX_ERROR;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            this.auctionManager.message(admin, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
            return CommandType.DEFAULT;
        }

        ItemStack inHand = admin.getInventory().getItemInMainHand();
        if (inHand.getType().isAir()) {
            message(plugin, admin, Message.SELL_ERROR_AIR);
            return CommandType.DEFAULT;
        }

        String type = argAsString(1, "listed");
        String priceAsString = argAsString(2, "0");

        var number = plugin.getConfiguration().getNumberMultiplicationConfiguration().parseNumber(priceAsString);
        if (number == null) {
            return CommandType.SYNTAX_ERROR;
        }

        BigDecimal price = number;
        AuctionEconomy economy = plugin.getEconomyManager().getDefaultEconomy(AuctionItemType.SELL);
        ItemStack cloned = inHand.clone();

        removeItemInHand(admin, cloned.getAmount());

        switch (type.toLowerCase(Locale.ENGLISH)) {
            case "expired" -> this.addExpired(target, cloned, price, economy, admin);
            case "purchased" -> this.addPurchased(target, cloned, price, economy, admin);
            default -> this.addListed(target, cloned, price, economy, admin);
        }

        return CommandType.SUCCESS;
    }

    private void addListed(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        long expiredAt = plugin.getConfiguration().getSellExpiration().getExpiration(target);
        expiredAt = expiredAt > 0 ? System.currentTimeMillis() + (expiredAt * 1000) : 0;

        this.plugin.getStorageManager().createAuctionItem(target, price, expiredAt, cloned, economy)
                .thenAccept(item -> {
                    this.auctionManager.addItem(StorageType.LISTED, item);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_OWNED);
                    this.auctionManager.updateListedItems(item, true, target);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%item%", item.getTranslationKey(), "%target%", target.getName(), "%type%", "listed");
                });
    }

    private void addExpired(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        this.plugin.getStorageManager().createAuctionItem(target, price, System.currentTimeMillis(), cloned, economy)
                .thenAccept(item -> {
                    item.setStatus(ItemStatus.REMOVED);
                    item.setExpiredAt(new Date());
                    this.auctionManager.addItem(StorageType.EXPIRED, item);
                    this.plugin.getStorageManager().updateItem(item, StorageType.EXPIRED);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_OWNED);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%item%", item.getTranslationKey(), "%target%", target.getName(), "%type%", "expired");
                });
    }

    private void addPurchased(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        this.plugin.getStorageManager().createAuctionItem(admin, price, System.currentTimeMillis(), cloned, economy)
                .thenAccept(item -> {
                    item.setBuyer(target);
                    item.setStatus(ItemStatus.PURCHASED);
                    this.auctionManager.addItem(StorageType.PURCHASED, item);
                    this.plugin.getStorageManager().updateItem(item, StorageType.PURCHASED);
                    this.auctionManager.clearPlayerCache(target, PlayerCacheKey.ITEMS_PURCHASED);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%item%", item.getTranslationKey(), "%target%", target.getName(), "%type%", "purchased");
                });
    }
}
