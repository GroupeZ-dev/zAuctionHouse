package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.InventoryCommandConfiguration;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;

public class CommandAuctionInventories extends VCommand {

    private final InventoryCommandConfiguration configuration;

    public CommandAuctionInventories(AuctionPlugin plugin, InventoryCommandConfiguration configuration) {
        super(plugin);
        this.configuration = configuration;

        if (configuration.permission() != null) this.setPermission(configuration.permission());
        if (configuration.description() != null) this.setDescription(configuration.description());

        this.addSubCommand(configuration.aliases());
        this.setConsoleCanUse(false);

        if (configuration.enablePage()) this.addOptionalArg(configuration.pageName());
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        int page = this.argAsInteger(0, 1);
        if (page <= 1) page = 1;

        this.plugin.getInventoriesLoader().openInventory(player, this.configuration.inventories(), page);

        return CommandType.SUCCESS;
    }
}
