package fr.maxlego08.zauctionhouse.api.event.events.stats;

import fr.maxlego08.zauctionhouse.api.event.AuctionEvent;
import org.bukkit.entity.Player;

/**
 * Event fired when a player opens (or is brought back to) the main auction inventory.
 * <p>
 * Observational only, not cancellable. Every navigation back to the main inventory
 * counts as an opening: this is an activity counter, not a session counter.
 * <p>
 * Delivered synchronously on the main thread (global region thread on Folia when
 * the opening is triggered from an asynchronous continuation).
 */
public class AuctionOpenEvent extends AuctionEvent {

    private final Player player;

    /**
     * Creates a new open event.
     *
     * @param player the player opening the main auction inventory
     */
    public AuctionOpenEvent(Player player) {
        this.player = player;
    }

    /**
     * Gets the player opening the main auction inventory.
     *
     * @return the player
     */
    public Player getPlayer() {
        return this.player;
    }
}
