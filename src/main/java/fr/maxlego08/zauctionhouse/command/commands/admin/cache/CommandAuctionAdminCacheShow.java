package fr.maxlego08.zauctionhouse.command.commands.admin.cache;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CommandAuctionAdminCacheShow extends VCommand {

    public CommandAuctionAdminCacheShow(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_SHOW);
        this.setConsoleCanUse(false);

        this.addSubCommand("show");
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String targetName = argAsString(0);
        if (targetName == null) {
            return CommandType.SYNTAX_ERROR;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_PLAYER_NOT_ONLINE, "%player%", targetName);
            return CommandType.DEFAULT;
        }

        PlayerCache cache = this.auctionManager.getCache(target);
        message(this.plugin, this.sender, Message.ADMIN_CACHE_SHOW_HEADER, "%player%", target.getName());

        boolean hasEntry = false;
        for (PlayerCacheKey key : PlayerCacheKey.values()) {
            if (cache.has(key)) {
                hasEntry = true;
                String valueStr = formatValue(cache.get(key));
                message(this.plugin, this.sender, Message.ADMIN_CACHE_SHOW_ENTRY, "%key%", key.name(), "%value%", valueStr.length() > 75 ? valueStr.substring(0, 75) + "..." : valueStr);
            }
        }

        if (!hasEntry) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_SHOW_EMPTY);
        }

        return CommandType.SUCCESS;
    }

    private String formatValue(Object value) {
        return switch (value) {
            case null -> "not set";
            case IntList intList -> "IntList (size=" + intList.size() + ")";
            case Item item -> item.getItemDisplay();
            default -> value.toString();
        };
    }
}