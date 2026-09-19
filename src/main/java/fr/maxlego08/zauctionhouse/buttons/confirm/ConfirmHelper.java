package fr.maxlego08.zauctionhouse.buttons.confirm;

import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;

public abstract class ConfirmHelper extends Button {

    protected final AuctionPlugin plugin;
    private final ItemStatus previous;
    private final ItemStatus next;

    public ConfirmHelper(AuctionPlugin plugin, ItemStatus previous, ItemStatus next) {
        this.plugin = plugin;
        this.previous = previous;
        this.next = next;
    }

    @Override
    public void onInventoryClose(@NonNull Player player, @NonNull InventoryEngine inventory) {
        super.onInventoryClose(player, inventory);
        releaseConfirmationState(player);
    }

    @Override
    public void onBackClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, @NonNull List<Inventory> oldInventories, @NonNull Inventory toInventory, int slot) {
        super.onBackClick(player, event, inventory, oldInventories, toInventory, slot);
        releaseConfirmationState(player);
    }

    /**
     * Chemin de sortie UNIQUE d'un inventaire de confirmation : fermeture de la fenetre ou
     * clic Retour. Les deux gestes ont exactement la meme semantique (le joueur renonce),
     * ils doivent donc appliquer exactement les memes gardes et la meme purge.
     * <p>
     * C-068 : la garde de statut, jusqu'ici presente uniquement dans {@code onInventoryClose},
     * s'applique desormais aussi au clic Retour. Sans elle, un Retour clique apres que
     * PurchaseService ou RemoveService a pose IS_BEING_*, rediffuse AVAILABLE par-dessus une
     * section critique deja engagee et re-affiche sur tout le reseau un item en cours de vente.
     * <p>
     * C-069 : les deux chemins partagent maintenant la meme purge (ITEMS_LISTED + ITEMS_SEARCH
     * pour TOUS les joueurs). Le clic Retour ne vidait auparavant que ITEMS_LISTED du seul
     * acteur, laissant un identifiant perime dans le cache de recherche des autres joueurs.
     *
     * @param player joueur qui quitte la confirmation
     */
    private void releaseConfirmationState(@NonNull Player player) {
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        // Le statut a bouge depuis l'ouverture de la confirmation : une autre chaine (achat
        // ou retrait, local ou distant) a pris la main sur cet item. Ne rien restaurer et
        // surtout ne rien rediffuser : c'est a cette chaine-la de conclure.
        if (item.getStatus() != this.previous) return;

        // Second filet, independant du statut : ce serveur ne detient plus cette annonce en
        // vente. Voir isStillHeldForSale : on purge la reference morte et on sort sans rien
        // rediffuser.
        if (!isStillHeldForSale(manager, item)) {
            cache.remove(PlayerCacheKey.ITEM_SHOW);
            return;
        }

        // Si l'item a expire pendant que l'inventaire de confirmation etait ouvert, on le
        // deplace vers les items expires au lieu de le rediffuser comme disponible.
        if (processIfExpired(player, item)) return;

        item.setStatus(this.next);
        this.plugin.getAuctionClusterBridge().notifyItemStatusChange(item, this.previous, this.next)
                .exceptionally(throwable -> {
                    this.plugin.getLogger().warning("Failed to notify item status change on confirmation exit: " + throwable.getMessage());
                    return null;
                });

        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
        manager.updateListedItems(item, true, player);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        Item item = manager.getCache(player).get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) {
            manager.openMainAuction(player);
            return;
        }

        // R2: si l'item a expiré pendant que l'inventaire de confirmation était ouvert, on n'exécute pas l'action.
        // On déplace l'item vers les items expirés et on se contente de fermer l'inventaire (pas de réouverture de l'hôtel des ventes).
        if (processIfExpired(player, item)) {
            this.plugin.getScheduler().runAtEntity(player, w -> {
                if (player.isOnline()) player.closeInventory();
            });
            return;
        }

        onPostClick(player, event, inventory, slot, placeholders, manager, item);
    }

    /**
     * If the item's listing has expired, transition it out of the active listings using the lazy expiration path
     * ({@code LISTED -> EXPIRED}) and clear the acting player's list caches. Callers must stop their normal flow when
     * this returns {@code true}: the action must not run and the item must not be re-broadcast to other viewers.
     *
     * @param player the player interacting with the confirmation inventory
     * @param item   the item being confirmed
     * @return {@code true} if the item was expired and has been handled, {@code false} otherwise
     */
    private boolean processIfExpired(@NonNull Player player, @NonNull Item item) {
        if (!item.isExpired()) return false;

        var manager = this.plugin.getAuctionManager();
        if (item.getStatus() != ItemStatus.REMOVED && item.getStatus() != ItemStatus.DELETED) {
            manager.getExpireService().processExpiredItem(item, StorageType.LISTED);
        }
        manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
        return true;
    }

    /**
     * Libere le statut de confirmation encore porte par l'item que le joueur avait ouvert,
     * sans dependre du dispatch d'inventaire de zMenu.
     * <p>
     * zMenu saute {@code onInventoryClose} quand le joueur est mort fenetre ouverte
     * (VInventoryManager coupe le dispatch sur {@code player.isDead()}) et le cache joueur
     * peut avoir ete purge avant la fermeture : ces deux chemins laissaient l'item fige en
     * IS_*_CONFIRM sur tout le cluster.
     * <p>
     * <b>Contrainte d'ordre</b> : cette methode lit {@link PlayerCacheKey#ITEM_SHOW}. Elle DOIT
     * etre appelee AVANT tout {@code removeCache} / {@code clearPlayerCache(ITEM_SHOW)}, sinon
     * la reference vers l'item a liberer est deja perdue.
     *
     * @param plugin instance du plugin
     * @param player joueur qui abandonne la confirmation
     */
    public static void releaseConfirmation(@NonNull AuctionPlugin plugin, @NonNull Player player) {
        var manager = plugin.getAuctionManager();
        var cache = manager.getCache(player);
        Item item = cache.get(PlayerCacheKey.ITEM_SHOW);
        if (item == null) return;

        var status = item.getStatus();
        // On ne libere QUE les deux statuts de confirmation. IS_BEING_PURCHASED /
        // IS_BEING_REMOVED signifient que la section critique est deja engagee (argent
        // debite, ligne DB en cours de mutation) : restaurer AVAILABLE la-dessus
        // remettrait en vente un item deja vendu.
        if (status != ItemStatus.IS_PURCHASE_CONFIRM && status != ItemStatus.IS_REMOVE_CONFIRM) return;

        cache.remove(PlayerCacheKey.ITEM_SHOW);

        // Meme filet que sur le chemin de fermeture d'inventaire : une annonce que ce serveur
        // ne detient plus en vente ne doit jamais etre re-annoncee disponible.
        if (!isStillHeldForSale(manager, item)) return;

        // L'annonce a expire pendant que la confirmation etait ouverte : la router vers les
        // items expires plutot que de la rediffuser comme disponible.
        if (item.isExpired()) {
            manager.getExpireService().processExpiredItem(item, StorageType.LISTED);
            manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_LISTED);
            return;
        }

        item.setStatus(ItemStatus.AVAILABLE);
        plugin.getAuctionClusterBridge().notifyItemStatusChange(item, status, ItemStatus.AVAILABLE)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to release confirmation status for item " + item.getId() + ": " + throwable.getMessage());
                    return null;
                });

        manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
        manager.updateListedItems(item, true, player);
    }

    /**
     * Dit si l'exemplaire memoire retenu par {@link PlayerCacheKey#ITEM_SHOW} est TOUJOURS
     * l'annonce que ce serveur detient en vente.
     * <p>
     * La garde de statut ne suffit pas a elle seule. Une annonce peut sortir du store LISTED
     * sans que le statut de l'exemplaire cache bouge : c'est le cas de
     * {@code removeItem(StorageType, Item)}, emprunte notamment par les listeners de l'addon
     * Redis quand un autre noeud conclut la vente. L'exemplaire reste alors fige sur son statut
     * de confirmation, la garde le juge « dans l'etat attendu », et le repasser en AVAILABLE le
     * REDIFFUSERAIT au cluster puis le reinjecterait dans les hotels des ventes ouverts —
     * exactement le fantome d'annonce vendue que l'on cherche a supprimer.
     * <p>
     * La comparaison est faite par IDENTITE et non par identifiant : si le store contient un
     * autre exemplaire du meme identifiant (rechargement, re-diffusion d'une mise en vente),
     * c'est lui qui fait foi et celui du cache est perime. On prefere alors ne rien diffuser,
     * une non-diffusion etant toujours moins nuisible qu'une remise en vente injustifiee.
     *
     * @param manager le gestionnaire d'hotel des ventes
     * @param item    l'exemplaire retenu par le cache du joueur
     * @return {@code true} si l'annonce est toujours celle detenue en vente par ce serveur
     */
    private static boolean isStillHeldForSale(@NonNull AuctionManager manager, @NonNull Item item) {
        return manager.getItem(StorageType.LISTED, item.getId()) == item;
    }

    protected abstract void onPostClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders, AuctionManager manager, Item item);
}
