package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CommandAuctionAdminHistory extends VCommand {

    public CommandAuctionAdminHistory(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("history");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_HISTORY);
        this.addOptionalArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.setConsoleCanUse(false);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        String targetName = argAsString(0);
        if (targetName == null) {
            this.auctionManager.getCache(this.player).remove(PlayerCacheKey.ADMIN_PLAYERS_DATA);
            plugin.getInventoriesLoader().openInventory(this.player, Inventories.ADMIN_MAIN);
            return CommandType.SUCCESS;
        }

        plugin.getStorageManager().findUniqueId(targetName).thenAccept(uuid -> {

            // Check if player is still online after async operation
            if (!this.player.isOnline()) {
                return;
            }

            if (uuid == null) {
                this.auctionManager.message(this.player, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
                return;
            }

            var cache = this.auctionManager.getCache(this.player);
            cache.set(PlayerCacheKey.ADMIN_TARGET_UUID, uuid);
            cache.set(PlayerCacheKey.ADMIN_TARGET_NAME, targetName);

            this.plugin.getScheduler().runAtEntity(this.player, w -> {
                if (this.player.isOnline()) {
                    this.plugin.getInventoriesLoader().openInventory(this.player, Inventories.ADMIN_HISTORY_MAIN);
                }
            });
            this.auctionManager.message(this.player, Message.ADMIN_OPEN_HISTORY, "%target%", targetName);
        });
        return CommandType.SUCCESS;
    }
}
