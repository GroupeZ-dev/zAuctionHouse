package fr.maxlego08.zauctionhouse.listeners;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final AuctionPlugin plugin;

    public PlayerListener(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConnect(PlayerJoinEvent event) {
        var player = event.getPlayer();
        this.plugin.getStorageManager().upsertPlayer(player);

        // Handle pending money claim on join
        this.plugin.getAuctionManager().getClaimService().handlePlayerJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getAuctionManager().removeCache(event.getPlayer());
    }

}
