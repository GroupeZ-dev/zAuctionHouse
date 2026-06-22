package fr.maxlego08.zauctionhouse.command.commands.admin.logs;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;

import java.util.List;

public class CommandAuctionAdminLogsPurge extends VCommand {

    public CommandAuctionAdminLogsPurge(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_LOGS_PURGE);
        this.setConsoleCanUse(true);

        this.addSubCommand("purge");
        this.addRequireArg("days", (sender, args) -> List.of("7", "30", "60", "90", "180", "365"));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        int days = argAsInteger(0, 0);
        if (days <= 0) {
            message(plugin, this.sender, Message.ADMIN_LOGS_INVALID_DAYS);
            return CommandType.DEFAULT;
        }

        long olderThanMs = days * 86_400_000L;

        plugin.getScheduler().runAsync(wrappedTask -> {
            long deleted = plugin.getStorageManager().with(LogRepository.class).deleteOlderThan(olderThanMs);
            message(plugin, this.sender, Message.ADMIN_LOGS_PURGE_SUCCESS, "%amount%", String.valueOf(deleted), "%days%", String.valueOf(days));
        });

        return CommandType.SUCCESS;
    }
}
