package fr.maxlego08.zauctionhouse.listeners;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmHelper;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
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

        // Handle sales notification on join
        this.plugin.getAuctionManager().getHistoryService().handlePlayerJoin(player);

        // Load player options
        this.plugin.getAuctionManager().getOptionService().loadPlayerOptions(player.getUniqueId());

        if (player.getName().equals("Maxlego08")) {
            this.plugin.getScheduler().runLater(task -> {
                if (player.isOnline()) {
                    ComponentMessageHelper.componentMessage.sendMessage(player, "<#24d65d>zAuctionHouse <#656665>• <#e6fff3>Ce serveur utilise <#24d65d>zAuctionHouse <white>v" + this.plugin.getDescription().getVersion());
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();

        // ORDRE SIGNIFICATIF : releaseConfirmation lit PlayerCacheKey.ITEM_SHOW, que
        // removeCache detruit. Ne JAMAIS inserer avant cette ligne du code qui touche au
        // cache du joueur, sous peine de figer a nouveau l'item en IS_*_CONFIRM sur tout
        // le cluster jusqu'au balayage de maintenance.
        ConfirmHelper.releaseConfirmation(this.plugin, player);

        this.plugin.getAuctionManager().removeCache(player);
        this.plugin.getAuctionManager().getOptionService().clearPlayerOptions(player.getUniqueId());
        this.plugin.getCommandManager().clearCooldowns(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // zMenu n'emet pas onInventoryClose quand le joueur meurt fenetre ouverte
        // (VInventoryManager: `if (player.isDead()) return;`), le statut de confirmation
        // resterait donc pose jusqu'au balayage de maintenance.
        ConfirmHelper.releaseConfirmation(this.plugin, event.getEntity());
    }

}
