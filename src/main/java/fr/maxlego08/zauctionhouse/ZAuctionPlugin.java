package fr.maxlego08.zauctionhouse;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ZAuctionPlugin extends JavaPlugin implements AuctionPlugin {

    @Override
    public void onEnable() {

        this.saveDefaultConfig();

    }

    @Override
    public void onDisable() {

    }
}