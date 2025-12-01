package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

public class CommandAuctionAdminOpen extends VCommand {

    public CommandAuctionAdminOpen(AuctionPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS);
        this.setDescription(Message.ADMIN_OPEN_INVENTORY);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.addRequireArg("type", (sender, args) -> java.util.List.of("listed", "expired", "purchased"));
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

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getName() == null) {
            this.auctionManager.message(admin, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
            return CommandType.DEFAULT;
        }

        String type = argAsString(1, "listed");
        Inventories inventories = switch (type.toLowerCase(Locale.ENGLISH)) {
            case "expired" -> Inventories.ADMIN_EXPIRED_ITEMS;
            case "purchased" -> Inventories.ADMIN_PURCHASED_ITEMS;
            default -> Inventories.ADMIN_OWNED_ITEMS;
        };

        var cache = this.auctionManager.getCache(admin);
        cache.set(PlayerCacheKey.ADMIN_TARGET, target.getUniqueId());
        cache.set(PlayerCacheKey.ADMIN_TARGET_NAME, target.getName());

        this.plugin.getInventoriesLoader().openInventory(admin, inventories);
        this.auctionManager.message(admin, Message.ADMIN_OPEN_INVENTORY, "%target%", target.getName(), "%type%", type);
        return CommandType.SUCCESS;
    }
}
