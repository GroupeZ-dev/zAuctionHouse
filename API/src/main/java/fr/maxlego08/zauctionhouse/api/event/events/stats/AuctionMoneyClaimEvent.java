package fr.maxlego08.zauctionhouse.api.event.events.stats;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * Event fired AFTER a player has successfully claimed pending money.
 * <p>
 * Only the amounts that were actually deposited are reported, per economy name.
 * This event is not cancellable and is delivered synchronously on the main thread
 * (global region thread on Folia, not the player's region: schedule on the player
 * before touching entity state).
 */
public class AuctionMoneyClaimEvent extends AuctionEvent {

    private final Player player;
    private final Map<String, BigDecimal> amountsByEconomy;

    /**
     * Creates a new money claim event.
     *
     * @param player           the player who claimed
     * @param amountsByEconomy economy name to amount actually deposited (positive values only)
     */
    public AuctionMoneyClaimEvent(Player player, Map<String, BigDecimal> amountsByEconomy) {
        this.player = player;
        this.amountsByEconomy = Collections.unmodifiableMap(amountsByEconomy);
    }

    /**
     * Gets the player who claimed the money.
     *
     * @return the player
     */
    public Player getPlayer() {
        return this.player;
    }

    /**
     * Gets the amounts actually deposited, keyed by economy name.
     *
     * @return an unmodifiable map of economy name to deposited amount
     */
    public Map<String, BigDecimal> getAmountsByEconomy() {
        return this.amountsByEconomy;
    }
}
