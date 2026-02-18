package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Arrays;
import java.util.Locale;

public class CommandAuctionAdminOpen extends VCommand {

    public CommandAuctionAdminOpen(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("open");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_OPEN);
        this.addRequireArg("player", (sender, args) -> Arrays.stream(Bukkit.getOfflinePlayers()).limit(50).map(OfflinePlayer::getName).toList());
        this.addRequireArg("type", (sender, args) -> java.util.List.of("listed", "expired", "purchased"));
        this.setConsoleCanUse(false);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        String targetName = argAsString(0);
        if (targetName == null) {
            this.auctionManager.message(this.player, Message.ADMIN_TARGET_REQUIRED);
            return CommandType.SYNTAX_ERROR;
        }

        plugin.getStorageManager().findUniqueId(targetName).thenAccept(uuid -> {

            if (uuid == null) {
                this.auctionManager.message(this.player, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
                return;
            }

            String type = argAsString(1, "listed");
            Inventories inventories = switch (type.toLowerCase(Locale.ENGLISH)) {
                case "expired" -> Inventories.ADMIN_EXPIRED_ITEMS;
                case "purchased" -> Inventories.ADMIN_PURCHASED_ITEMS;
                default -> Inventories.ADMIN_OWNED_ITEMS;
            };

            var cache = this.auctionManager.getCache(this.player);
            cache.set(PlayerCacheKey.ADMIN_TARGET_UUID, uuid);
            cache.set(PlayerCacheKey.ADMIN_TARGET_NAME, targetName);

            this.plugin.getScheduler().runNextTick(w -> this.plugin.getInventoriesLoader().openInventory(this.player, inventories));
            this.auctionManager.message(this.player, Message.ADMIN_OPEN_INVENTORY, "%target%", targetName, "%type%", type);
        });
        return CommandType.SUCCESS;
    }
}
