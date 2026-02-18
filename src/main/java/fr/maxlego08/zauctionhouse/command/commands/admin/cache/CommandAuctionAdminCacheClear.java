package fr.maxlego08.zauctionhouse.command.commands.admin.cache;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandAuctionAdminCacheClear extends VCommand {

    public CommandAuctionAdminCacheClear(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_CLEAR);
        this.setConsoleCanUse(false);

        this.addSubCommand("clear");
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.addOptionalArg("key", (sender, args) -> {
            List<String> keys = new ArrayList<>();
            keys.add("all");
            Arrays.stream(PlayerCacheKey.values()).map(PlayerCacheKey::name).forEach(keys::add);
            return keys;
        });
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

        String keyName = argAsString(1);

        if (keyName == null || keyName.equalsIgnoreCase("all")) {
            this.auctionManager.clearPlayerCache(target, PlayerCacheKey.values());
            message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED_ALL, "%player%", target.getName());
        } else {
            PlayerCacheKey key;
            try {
                key = PlayerCacheKey.valueOf(keyName.toUpperCase());
            } catch (IllegalArgumentException e) {
                message(this.plugin, this.sender, Message.ADMIN_CACHE_INVALID_KEY, "%key%", keyName);
                return CommandType.DEFAULT;
            }
            this.auctionManager.clearPlayerCache(target, key);
            message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED, "%key%", key.name(), "%player%", target.getName());
        }

        return CommandType.SUCCESS;
    }
}