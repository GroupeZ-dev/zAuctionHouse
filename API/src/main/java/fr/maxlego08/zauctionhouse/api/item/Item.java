package fr.maxlego08.zauctionhouse.api.item;

import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Abstraction representing an item tracked by the auction house, including metadata about the
 * seller, buyer, pricing, and display state. Implementations should remain immutable wherever
 * possible to avoid synchronization issues when items are viewed concurrently.
 */
public interface Item {

    /**
     * @return unique identifier of the item in storage
     */
    int getId();

    /**
     * @return name of the server where the item originated (useful for clustered deployments)
     */
    String getServerName();

    /**
     * @return UUID of the player who listed the item
     */
    UUID getSellerUniqueId();

    /**
     * @return last known username of the seller
     */
    String getSellerName();

    /**
     * @return listing price for the item
     */
    BigDecimal getPrice();

    /**
     * @return economy used to process the transaction for this item
     */
    AuctionEconomy getAuctionEconomy();

    /**
     * @return {@link OfflinePlayer} representation of the seller for compatibility with hooks
     */
    OfflinePlayer getSeller();

    /**
     * @return timestamp when the item should expire from listings
     */
    Date getExpiredAt();

    /**
     * Updates the expiration timestamp, typically when extending or reducing listing duration.
     *
     * @param expiredAt new expiration date
     */
    void setExpiredAt(Date expiredAt);

    /**
     * @return timestamp when the item was originally listed
     */
    Date getCreatedAt();

    /**
     * Builds the visual {@link ItemStack} representation tailored to the viewer.
     *
     * @param player viewer for whom the item is being rendered
     * @return item stack with placeholders resolved for the player
     */
    ItemStack buildItemStack(Player player);

    /**
     * Builds the visual {@link ItemStack} representation using custom lore lines.
     *
     * @param player viewer for whom the item is being rendered
     * @param lore   lore lines to inject into the item display
     * @return item stack with placeholders resolved for the player
     */
    ItemStack buildItemStack(Player player, List<String> lore);

    /**
     * Creates placeholder values for the item so they can be substituted into menus or messages.
     *
     * @param player viewer or actor requesting placeholder data
     * @return placeholder instance containing item-specific tokens
     */
    Placeholders createPlaceholders(Player player);

    /**
     * Generates the textual status of the item (e.g., listed, expired) personalized for the player.
     *
     * @param player viewer requesting the status
     * @return human-readable status string
     */
    String createStatus(Player player);

    /**
     * @return price formatted using the associated economy
     */
    String getFormattedPrice();

    /**
     * @return formatted expiration date string respecting the plugin's date format
     */
    String getFormattedExpireDate();

    /**
     * @return human friendly representation of remaining time until expiration
     */
    String getRemainingTime();

    /**
     * @return {@code true} if the current time is after the expiration date
     */
    boolean isExpired();

    /**
     * @return current lifecycle status of the item
     */
    ItemStatus getStatus();

    /**
     * Updates the lifecycle status (listed, expired, purchased, etc.).
     *
     * @param status new status to set
     */
    void setStatus(ItemStatus status);

    /**
     * Indicates whether the player is allowed to receive the physical item stack (e.g., has
     * inventory space or permissions).
     *
     * @param player player attempting to receive the item
     * @return {@code true} if the player can receive the item
     */
    boolean canReceiveItem(Player player);

    /**
     * @return quantity of items represented by this listing
     */
    int getAmount();

    /**
     * @return translation key used to fetch localized display strings
     */
    String getTranslationKey();

    /**
     * @return UUID of the buyer when the item has been purchased, otherwise {@code null}
     */
    UUID getBuyerUniqueId();

    /**
     * @return last known username of the buyer
     */
    String getBuyerName();

    /**
     * Records the buyer using a live {@link Player} reference.
     *
     * @param player buyer who purchased the item
     */
    void setBuyer(Player player);

    /**
     * Records the buyer using pre-fetched identifiers, useful for offline transactions.
     *
     * @param buyerUniqueId UUID of the buyer
     * @param buyerName     username of the buyer
     */
    void setBuyer(UUID buyerUniqueId, String buyerName);

    /**
     * @return human-friendly display name of the item for menus and messages
     */
    String getItemDisplay();
}
