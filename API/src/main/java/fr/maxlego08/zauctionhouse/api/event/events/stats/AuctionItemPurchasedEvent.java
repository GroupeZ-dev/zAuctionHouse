package fr.maxlego08.zauctionhouse.api.event.events.stats;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

/**
 * Event fired AFTER a purchase has been fully committed: the buyer has been
 * charged, the seller has been credited (or a PENDING transaction created),
 * and the database row has moved out of LISTED.
 * <p>
 * Carries the amounts that were ACTUALLY charged, as computed by the purchase
 * charge resolution. Never recompute taxes from the economy configuration,
 * it may have changed since.
 * <p>
 * This event is not cancellable and is never fired for a failed or cancelled
 * purchase. It is delivered synchronously on the main thread (global region thread
 * on Folia, not the player's region: schedule on the player before touching entity state),
 * exactly once per purchase, on the buyer's server.
 */
public class AuctionItemPurchasedEvent extends AuctionEvent {

    private final Player buyer;
    private final AuctionItem item;
    private final BigDecimal buyerPays;
    private final BigDecimal sellerReceives;
    private final TaxResult taxResult;
    private final TransactionStatus sellerPaymentStatus;

    /**
     * Creates a new purchased event.
     *
     * @param buyer               the player who bought the item
     * @param item                the purchased auction item (seller via getSellerUniqueId())
     * @param buyerPays           the amount actually withdrawn from the buyer
     * @param sellerReceives      the amount credited (or owed) to the seller
     * @param taxResult           the tax breakdown actually applied
     * @param sellerPaymentStatus RETRIEVED when the seller was paid immediately,
     *                            PENDING when the money awaits a claim
     */
    public AuctionItemPurchasedEvent(Player buyer, AuctionItem item, BigDecimal buyerPays,
                                     BigDecimal sellerReceives, TaxResult taxResult,
                                     TransactionStatus sellerPaymentStatus) {
        this.buyer = buyer;
        this.item = item;
        this.buyerPays = buyerPays;
        this.sellerReceives = sellerReceives;
        this.taxResult = taxResult;
        this.sellerPaymentStatus = sellerPaymentStatus;
    }

    /**
     * Gets the player who bought the item.
     *
     * @return the buyer
     */
    public Player getBuyer() {
        return this.buyer;
    }

    /**
     * Gets the purchased auction item.
     *
     * @return the auction item
     */
    public AuctionItem getItem() {
        return this.item;
    }

    /**
     * Gets the amount actually withdrawn from the buyer.
     *
     * @return the amount paid by the buyer
     */
    public BigDecimal getBuyerPays() {
        return this.buyerPays;
    }

    /**
     * Gets the amount credited (or owed) to the seller.
     *
     * @return the amount received by the seller
     */
    public BigDecimal getSellerReceives() {
        return this.sellerReceives;
    }

    /**
     * Gets the tax breakdown actually applied to this purchase.
     *
     * @return the tax result
     */
    public TaxResult getTaxResult() {
        return this.taxResult;
    }

    /**
     * Gets the seller payment status.
     *
     * @return RETRIEVED when the seller was paid immediately, PENDING when the money awaits a claim
     */
    public TransactionStatus getSellerPaymentStatus() {
        return this.sellerPaymentStatus;
    }
}
