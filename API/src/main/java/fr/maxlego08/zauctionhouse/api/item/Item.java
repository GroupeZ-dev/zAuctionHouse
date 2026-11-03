package fr.maxlego08.zauctionhouse.api.item;

import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.EnumSet;

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
     * Updates the economy associated with this item. This is used during plugin reload
     * to refresh economy references when configuration changes.
     *
     * @param auctionEconomy new economy to associate with this item
     */
    void setAuctionEconomy(AuctionEconomy auctionEconomy);

    /**
     * @return the name of the economy used for this item (used to resolve the economy after reload)
     */
    String getEconomyName();

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
     * Builds the visual {@link ItemStack} representation using custom lore lines,
     * resolving only the specified placeholders for better performance.
     *
     * @param player viewer for whom the item is being rendered
     * @param lore   lore lines to inject into the item display
     * @param needed the set of placeholders that the lore actually references
     * @return item stack with placeholders resolved for the player
     */
    default ItemStack buildItemStack(Player player, List<String> lore, Set<ItemPlaceholder> needed) {
        return buildItemStack(player, lore);
    }

    /**
     * Creates placeholder values for the item so they can be substituted into menus or messages.
     *
     * @param player viewer or actor requesting placeholder data
     * @return placeholder instance containing item-specific tokens
     */
    Placeholders createPlaceholders(Player player);

    /**
     * Creates placeholder values for the item, resolving only the specified placeholders.
     * This avoids unnecessary computation (formatting, date parsing, etc.) for placeholders
     * that are not referenced in the lore template.
     *
     * @param player viewer or actor requesting placeholder data
     * @param needed the set of placeholders to resolve
     * @return placeholder instance containing only the requested tokens
     */
    default Placeholders createPlaceholders(Player player, Set<ItemPlaceholder> needed) {
        return createPlaceholders(player);
    }

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
     * @return price formatted using the specified format
     */
    String getFormattedPrice(PriceFormat priceFormat);

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
     * Indicates whether the item is currently live in the auction listings: available for sale and not expired.
     * This is the single predicate shared by every display chokepoint (main list rendering, incremental updates,
     * category counts, placeholders) so that an expired item is never shown nor counted, even while a cache is stale.
     *
     * @return {@code true} if the item should be displayed/counted in the auction list
     */
    default boolean isActivelyListed() {
        return getStatus() == ItemStatus.AVAILABLE && !isExpired();
    }

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
     * Player who holds the confirmation reservation ({@link ItemStatus#IS_PURCHASE_CONFIRM} or
     * {@link ItemStatus#IS_REMOVE_CONFIRM}) on this server. Only meaningful while the item carries
     * one of these statuses; reset when the item goes back to {@link ItemStatus#AVAILABLE}, so a
     * release coming from another server or from the maintenance sweep invalidates it.
     *
     * @return the reservation owner, or {@code null} if none is known locally
     */
    default java.util.UUID getReservationOwner() {
        return null;
    }

    /**
     * Records the player who holds the confirmation reservation. Must be called right after the
     * confirmation status has been set.
     *
     * @param owner reservation owner
     */
    default void setReservationOwner(java.util.UUID owner) {
    }

    /**
     * Atomically reserves an {@link ItemStatus#AVAILABLE} item: sets the confirmation status and
     * its owner together, or does nothing if the item is no longer available.
     *
     * @param confirmStatus {@link ItemStatus#IS_PURCHASE_CONFIRM} or {@link ItemStatus#IS_REMOVE_CONFIRM}
     * @param owner         player taking the reservation
     * @return {@code true} if the reservation was taken
     */
    default boolean tryReserve(ItemStatus confirmStatus, java.util.UUID owner) {
        if (getStatus() != ItemStatus.AVAILABLE) return false;
        setStatus(confirmStatus);
        setReservationOwner(owner);
        return true;
    }

    /**
     * @param playerUniqueId player to test
     * @return {@code true} if this item carries a confirmation status reserved by this player
     */
    default boolean isReservedBy(java.util.UUID playerUniqueId) {
        var status = getStatus();
        return (status == ItemStatus.IS_PURCHASE_CONFIRM || status == ItemStatus.IS_REMOVE_CONFIRM)
                && playerUniqueId != null && playerUniqueId.equals(getReservationOwner());
    }

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

    /**
     * @return list of categories this item belongs to
     */
    Set<Category> getCategories();

    /**
     * Sets the categories for this item.
     *
     * @param categories list of categories
     */
    void setCategories(Set<Category> categories);

    /**
     * Checks if the item belongs to a specific category.
     *
     * @param category the category to check
     * @return {@code true} if the item belongs to the category
     */
    default boolean hasCategory(Category category) {
        var categories = getCategories();
        return categories != null && categories.contains(category);
    }

    /**
     * Checks if the item belongs to a category with the specified ID.
     * Optimized to use simple iteration instead of stream for better performance.
     *
     * @param categoryId the category ID to check
     * @return {@code true} if the item belongs to the category
     */
    default boolean hasCategory(String categoryId) {
        var categories = getCategories();
        if (categories == null || categoryId == null) return false;
        for (var category : categories) {
            if (categoryId.equals(category.getId())) {
                return true;
            }
        }
        return false;
    }
}
