package fr.maxlego08.zauctionhouse.api.economy;

import fr.maxlego08.zauctionhouse.api.item.ItemType;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Central registry for every economy available to the auction house. Implementations coordinate
 * registration, lookups, formatting utilities, and default economy selection depending on the
 * auction item type being handled.
 */
public interface EconomyManager {

    /**
     * @return all economies currently registered with the plugin
     */
    Collection<AuctionEconomy> getEconomies();

    /**
     * Registers a new economy provider. Duplicate names should be rejected.
     *
     * @param economy economy implementation to register
     * @return {@code true} if the economy was added, {@code false} if it already existed
     */
    boolean registerEconomy(AuctionEconomy economy);

    /**
     * Removes the specified economy provider from the registry.
     *
     * @param economy economy implementation to remove
     * @return {@code true} if the economy was removed
     */
    boolean removeEconomy(AuctionEconomy economy);

    /**
     * Retrieves an economy by its internal name.
     *
     * @param economyName unique name of the economy
     * @return optional containing the economy when found
     */
    Optional<AuctionEconomy> getEconomy(String economyName);

    /**
     * Reloads and registers economies from configuration or detected hooks.
     */
    void loadEconomies();

    /**
     * Returns the economy that should be used when listing or purchasing the provided item type.
     *
     * @param itemType type of auction item being processed
     * @return default economy configured for the item type
     */
    AuctionEconomy getDefaultEconomy(ItemType itemType);

    /**
     * @return decimal format applied when converting prices to human readable strings
     */
    DecimalFormat getPriceDecimalFormat();

    /**
     * @return collection of number format reductions used to simplify large values (e.g., K, M)
     */
    List<NumberFormatReduction> getPriceReductions();

    /**
     * Formats a number using a specific price format, applying reductions when necessary.
     *
     * @param priceFormat configuration describing how numbers should be represented
     * @param number      numeric value to format
     * @return formatted price string
     */
    String format(PriceFormat priceFormat, Number number);

    /**
     * Formats a number using the rules defined by the provided economy instance.
     *
     * @param economy economy that owns the currency representation
     * @param number  numeric value to format
     * @return formatted price string
     */
    String format(AuctionEconomy economy, Number number);
}
