package fr.maxlego08.zauctionhouse.command.commands.admin.logs;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.LogRepository;

public class CommandAuctionAdminLogsClearMigrated extends VCommand {

    public CommandAuctionAdminLogsClearMigrated(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_LOGS_CLEAR_MIGRATED);
        this.setConsoleCanUse(true);

        this.addSubCommand("clear-migrated");
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        plugin.getScheduler().runAsync(wrappedTask -> {
            long deleted = plugin.getStorageManager().with(LogRepository.class).deleteMigrated();
            message(plugin, this.sender, Message.ADMIN_LOGS_CLEAR_MIGRATED_SUCCESS, "%amount%", String.valueOf(deleted));
        });

        return CommandType.SUCCESS;
    }
}
