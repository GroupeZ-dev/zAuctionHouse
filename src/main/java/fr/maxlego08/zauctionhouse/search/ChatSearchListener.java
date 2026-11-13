package fr.maxlego08.zauctionhouse.search;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSearchListener implements Listener {

    private final AuctionPlugin plugin;
    private final SearchDialog searchDialog;
    private final Set<UUID> waitingForInput = ConcurrentHashMap.newKeySet();

    public ChatSearchListener(AuctionPlugin plugin) {
        this.plugin = plugin;
        // L'API Dialog de Paper n'existe qu'a partir de 1.21.6 : sur une version plus ancienne,
        // charger SearchDialog leve un NoClassDefFoundError, on retombe donc sur la saisie tchat.
        this.searchDialog = plugin instanceof ZAuctionPlugin zAuctionPlugin && isDialogApiAvailable() ? new SearchDialog(zAuctionPlugin) : null;
    }

    private static boolean isDialogApiAvailable() {
        try {
            Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction", false, ChatSearchListener.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    public void startDialogSearch(Player player) {
        this.waitingForInput.remove(player.getUniqueId());
        if (this.searchDialog == null || !this.searchDialog.open(player)) startSearch(player);
    }

    public void reloadDialog() {
        if (this.searchDialog != null) this.searchDialog.reload();
    }

    public void clear() {
        this.waitingForInput.clear();
        if (this.searchDialog != null) this.searchDialog.clear();
    }

    public void startSearch(Player player) {
        if (this.searchDialog != null) this.searchDialog.cancel(player);
        waitingForInput.add(player.getUniqueId());
        player.closeInventory();
        this.plugin.sendMessage(player, Message.SEARCH_START);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!waitingForInput.remove(event.getPlayer().getUniqueId())) return;

        event.setCancelled(true);

        String query = PlainTextComponentSerializer.plainText().serialize(event.message());
        Player player = event.getPlayer();

        this.plugin.getScheduler().runAtEntity(player, w -> {
            if (player.isOnline()) {
                this.plugin.getAuctionManager().startSearch(player, query);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waitingForInput.remove(event.getPlayer().getUniqueId());
        if (this.searchDialog != null) this.searchDialog.cancel(event.getPlayer());
    }
}
