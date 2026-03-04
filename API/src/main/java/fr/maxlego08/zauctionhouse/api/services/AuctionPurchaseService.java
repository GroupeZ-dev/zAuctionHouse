package fr.maxlego08.zauctionhouse.api.services;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseResult;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for handling item purchases in the auction house.
 * <p>
 * This service manages the complete purchase flow including:
 * <ul>
 *     <li>Availability verification across cluster nodes</li>
 *     <li>Item locking to prevent concurrent purchases</li>
 *     <li>Economy transaction processing</li>
 *     <li>Item delivery to the buyer</li>
 *     <li>Seller notification and payment</li>
 * </ul>
 */
public interface AuctionPurchaseService {

    /**
     * Processes the purchase of an item by a player.
     * <p>
     * This method performs all necessary validations and transactions asynchronously,
     * including cluster-wide locking to prevent race conditions when multiple players
     * attempt to purchase the same item simultaneously.
     *
     * @param player the player purchasing the item
     * @param item   the item being purchased
     * @return a future containing the result of the purchase operation
     */
    CompletableFuture<PurchaseResult> purchaseItem(Player player, Item item);
}
