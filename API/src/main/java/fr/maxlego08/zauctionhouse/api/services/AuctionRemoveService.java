package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveResult;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for removing items from the auction house.
 * <p>
 * This service handles item removal from different storage buckets (listed, expired, purchased)
 * and ensures proper item delivery back to the player's inventory.
 */
public interface AuctionRemoveService {

    /**
     * Removes an item that the player has listed for sale.
     * <p>
     * The item is returned to the player's inventory and removed from active listings.
     * Fires {@link fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveListedItemEvent}
     * before removal which can be cancelled.
     *
     * @param player the player removing their listing
     * @param item   the listed item to remove
     * @return a future containing the result of the remove operation
     */
    CompletableFuture<RemoveResult> removeListedItem(Player player, Item item);

    /**
     * Removes an item that the player is currently selling.
     * <p>
     * Similar to {@link #removeListedItem(Player, Item)} but specifically for items
     * in the selling state within the player's personal inventory view.
     *
     * @param player the player removing their item
     * @param item   the selling item to remove
     * @return a future containing the result of the remove operation
     */
    CompletableFuture<RemoveResult> removeSellingItem(Player player, Item item);

    /**
     * Removes an expired item and returns it to the player's inventory.
     * <p>
     * Fires {@link fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveExpiredItemEvent}
     * before removal which can be cancelled.
     *
     * @param player the player claiming their expired item
     * @param item   the expired item to remove
     * @return a future containing the result of the remove operation
     */
    CompletableFuture<RemoveResult> removeExpiredItem(Player player, Item item);

    /**
     * Removes a purchased item and delivers it to the player's inventory.
     * <p>
     * Fires {@link fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemovePurchasedItemEvent}
     * before removal which can be cancelled.
     *
     * @param player the player claiming their purchased item
     * @param item   the purchased item to remove
     * @return a future containing the result of the remove operation
     */
    CompletableFuture<RemoveResult> removePurchasedItem(Player player, Item item);

}
