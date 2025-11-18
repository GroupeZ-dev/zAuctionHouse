package fr.maxlego08.zauctionhouse.api.event.events;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;

public class AuctionLoadEconomyEvent extends AuctionEvent {

    private final AuctionPlugin plugin;
    private final EconomyManager economyManager;

    public AuctionLoadEconomyEvent(AuctionPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    public AuctionPlugin getPlugin() {
        return plugin;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
