package fr.maxlego08.zauctionhouse.api.button;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;

public abstract class LoadingButton extends PaginateButton {

    protected final AuctionPlugin plugin;
    protected final int loadingSlot;

    public LoadingButton(AuctionPlugin plugin, int loadingSlot) {
        this.plugin = plugin;
        this.loadingSlot = loadingSlot;
    }

    public AuctionPlugin getPlugin() {
        return plugin;
    }

    public int getLoadingSlot() {
        return loadingSlot;
    }
}
