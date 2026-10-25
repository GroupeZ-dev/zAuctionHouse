package fr.maxlego08.zauctionhouse.api.event.events.stats;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import org.bukkit.entity.Player;

/**
 * Event fired AFTER an auction item has been successfully listed for sale.
 * <p>
 * The listing is already committed to storage and announced: this event is
 * observational only and is not cancellable. It covers every sell path
 * (command, sell inventory and GUI).
 * <p>
 * This event is delivered synchronously on the main thread (global region thread on Folia,
 * not the player's region: schedule on the player before touching entity state).
 */
public class AuctionItemListedEvent extends AuctionEvent {

    private final Player seller;
    private final AuctionItem item;
    private final TaxResult sellTax;

    /**
     * Creates a new listed event.
     *
     * @param seller  the player who listed the item
     * @param item    the committed auction item (id, price, economy, stacks, categories)
     * @param sellTax the sell tax applied at listing time, or {@code null} when none applied
     */
    public AuctionItemListedEvent(Player seller, AuctionItem item, TaxResult sellTax) {
        this.seller = seller;
        this.item = item;
        this.sellTax = sellTax;
    }

    /**
     * Gets the player who listed the item.
     *
     * @return the seller
     */
    public Player getSeller() {
        return this.seller;
    }

    /**
     * Gets the committed auction item.
     *
     * @return the listed auction item
     */
    public AuctionItem getItem() {
        return this.item;
    }

    /**
     * Gets the sell tax applied at listing time.
     *
     * @return the sell tax, or {@code null} when no tax was applied
     */
    public TaxResult getSellTax() {
        return this.sellTax;
    }
}
