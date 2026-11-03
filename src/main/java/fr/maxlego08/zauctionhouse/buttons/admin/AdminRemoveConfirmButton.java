package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.util.logging.Level;

/**
 * Confirmation du retrait d'une annonce par un administrateur.
 * <p>
 * Meme cycle de vie que la confirmation d'achat : a l'ouverture l'annonce passe en
 * {@link ItemStatus#IS_REMOVE_CONFIRM} (diffuse au cluster), ce qui la retire des hotels des
 * ventes ouverts et bloque achat comme retrait par son proprietaire. Si l'administrateur ferme
 * la fenetre ou clique sur Retour sans confirmer, {@link ConfirmHelper} la remet en
 * {@link ItemStatus#AVAILABLE} et la rediffuse.
 */
public class AdminRemoveConfirmButton extends ConfirmHelper {

    public AdminRemoveConfirmButton(Plugin plugin) {
        super((AuctionPlugin) plugin, ItemStatus.IS_REMOVE_CONFIRM, ItemStatus.AVAILABLE);
    }

    /**
     * Reserve l'annonce et ouvre l'inventaire de confirmation admin. Meme ordre que
     * {@code ListedItemsButton#openConfirmation} : garde compare-and-set, statut LOCAL, caches
     * et GUI de maniere synchrone sur le thread du joueur, PUIS diffusion au cluster.
     *
     * @param plugin instance du plugin
     * @param player administrateur
     * @param item   annonce visee
     */
    public static void open(AuctionPlugin plugin, Player player, Item item) {

        var scheduler = plugin.getScheduler();
        if (!scheduler.isOwnedByCurrentRegion(player)) {
            scheduler.runAtEntity(player, wrappedTask -> open(plugin, player, item));
            return;
        }

        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission())) return;

        var manager = plugin.getAuctionManager();
        var inventoryManager = plugin.getInventoriesLoader().getInventoryManager();

        // Ne jamais ecraser une reference vers une annonce encore en confirmation : elle
        // resterait figee en IS_*_CONFIRM sur tout le cluster.
        ConfirmHelper.releaseConfirmation(plugin, player);

        var cache = manager.getCache(player);

        // Garde compare-and-set : l'annonce doit etre libre (ni en confirmation d'achat ou de
        // retrait, ni en cours de traitement, ni expiree) et etre l'exemplaire detenu par le store.
        // tryReserve pose statut ET detenteur atomiquement, uniquement si l'annonce est AVAILABLE.
        if (item.isExpired() || manager.getItem(StorageType.LISTED, item.getId()) != item || !item.tryReserve(ItemStatus.IS_REMOVE_CONFIRM, player.getUniqueId())) {
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            inventoryManager.updateInventory(player);
            return;
        }

        cache.set(PlayerCacheKey.ITEM_SHOW, item);
        cache.set(PlayerCacheKey.CURRENT_PAGE, inventoryManager.getPage(player));
        cache.set(PlayerCacheKey.ADMIN_REMOVE_NOTIFY, true);
        cache.set(PlayerCacheKey.ADMIN_REMOVE_EXPIRE, true);
        cache.set(PlayerCacheKey.ADMIN_REMOVE_COPY, false);

        // Retire l'annonce des hotels des ventes deja ouverts des autres joueurs.
        manager.updateListedItems(item, false, player);

        plugin.getInventoriesLoader().openInventory(player, Inventories.ADMIN_REMOVE_CONFIRM);

        // Diffusion APRES l'engagement local, comme pour la confirmation d'achat.
        plugin.getAuctionClusterBridge()
                .notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_REMOVE_CONFIRM)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to broadcast " + ItemStatus.IS_REMOVE_CONFIRM + " for item " + item.getId() + " (admin removal)", throwable);
                    return null;
                });
    }

    @Override
    protected void onPostClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders, AuctionManager manager, Item item) {

        // Permission retiree pendant que la fenetre etait ouverte : la fermeture rendra
        // l'annonce via ConfirmHelper.
        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission())) {
            this.plugin.getScheduler().runAtEntity(player, w -> {
                if (player.isOnline()) player.closeInventory();
            });
            return;
        }

        var cache = manager.getCache(player);
        boolean held = manager.getItem(StorageType.LISTED, item.getId()) == item;

        // Reservation perdue pendant que la fenetre etait ouverte (balayage de maintenance, ici
        // ou sur un autre serveur) mais annonce toujours libre : on la reprend, comme le ferait
        // une reouverture de l'inventaire, plutot que d'ignorer le clic de l'administrateur.
        if (held && !item.isReservedBy(player.getUniqueId()) && !item.isExpired()
                && item.tryReserve(ItemStatus.IS_REMOVE_CONFIRM, player.getUniqueId())) {
            manager.updateListedItems(item, false, player);
            this.plugin.getAuctionClusterBridge()
                    .notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_REMOVE_CONFIRM)
                    .exceptionally(throwable -> {
                        this.plugin.getLogger().log(Level.WARNING, "Failed to broadcast " + ItemStatus.IS_REMOVE_CONFIRM + " for item " + item.getId() + " (admin removal)", throwable);
                        return null;
                    });
        }

        // L'annonce est reservee par quelqu'un d'autre (achat ou retrait en cours) ou a quitte
        // la vente : on ne touche a rien, mais on le dit a l'administrateur.
        if (!held || !item.isReservedBy(player.getUniqueId()) || item.getStatus() != ItemStatus.IS_REMOVE_CONFIRM) {
            manager.message(player, Message.ADMIN_ITEM_NOT_AVAILABLE, "%items%", item.getItemDisplay());
            clearState(cache);
            manager.openMainAuction(player);
            return;
        }

        // adminRemoveItem accepte une annonce en IS_REMOVE_CONFIRM uniquement si CET
        // administrateur en est le detenteur. Sur echec, il rend lui-meme l'annonce disponible.
        manager.adminRemoveItem(player, item.getSellerUniqueId(), item, StorageType.LISTED, cache.get(PlayerCacheKey.ADMIN_REMOVE_NOTIFY), cache.get(PlayerCacheKey.ADMIN_REMOVE_EXPIRE), cache.get(PlayerCacheKey.ADMIN_REMOVE_COPY), false);

        clearState(cache);
        manager.openMainAuction(player);
    }

    private static void clearState(fr.maxlego08.zauctionhouse.api.cache.PlayerCache cache) {
        cache.remove(PlayerCacheKey.ITEM_SHOW);
        cache.remove(PlayerCacheKey.ADMIN_REMOVE_NOTIFY, PlayerCacheKey.ADMIN_REMOVE_EXPIRE, PlayerCacheKey.ADMIN_REMOVE_COPY);
    }
}
