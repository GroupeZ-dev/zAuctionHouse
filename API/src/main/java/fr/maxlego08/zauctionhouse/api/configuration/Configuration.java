package fr.maxlego08.zauctionhouse.api.configuration;

import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.InventoryCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.SimpleCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ActionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.AutoClaimConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.BroadcastConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.CooldownConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.HistoryConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SalesNotificationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemDisplayConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemLoreConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.NumberMultiplicationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceDebugConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PermissionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SortConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SpecialItemsConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.TimeConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.WorldConfiguration;
import fr.maxlego08.zauctionhouse.api.messages.MessageColor;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Represents the configuration of the plugin.
 * This interface provides methods to access various configuration settings, such as debug mode,
 * command cooldowns, trash size, compact materials, storage type, database configuration,
 * server type, and Redis configuration.
 *
 * @see ConfigurationFile
 */
public interface Configuration extends ConfigurationFile {

    /**
     * Checks if debug mode is enabled in the plugin configuration.
     *
     * @return true if debug mode is enabled, false otherwise.
     */
    boolean isEnableDebug();

    /**
     * Checks if performance debug mode is enabled in the plugin configuration.
     * When enabled, the plugin will log execution times for heavy operations.
     *
     * @return true if performance debug is enabled, false otherwise.
     */
    boolean isEnablePerformanceDebug();

    /**
     * Gets the name of this server instance.
     * Used for cluster identification in multi-server setups.
     *
     * @return the server name
     */
    String getServerName();

    /**
     * Gets the message color configurations for formatting messages.
     *
     * @return list of message colors
     */
    List<MessageColor> getMessageColors();

    /**
     * Gets the number multiplication configuration for the sell command.
     *
     * @return the number multiplication configuration
     */
    NumberMultiplicationConfiguration getNumberMultiplicationConfiguration();

    /**
     * Gets the expiration configuration for sell listings.
     *
     * @return the sell expiration configuration
     */
    ExpirationConfiguration getSellExpiration();

    /**
     * Gets the expiration configuration for rent listings.
     *
     * @return the rent expiration configuration
     */
    ExpirationConfiguration getRentExpiration();

    /**
     * Gets the expiration configuration for bid listings.
     *
     * @return the bid expiration configuration
     */
    ExpirationConfiguration getBidExpiration();

    /**
     * Gets the expiration configuration for purchased items.
     *
     * @return the purchase expiration configuration
     */
    ExpirationConfiguration getPurchaseExpiration();

    /**
     * Gets the expiration configuration for expired items.
     *
     * @return the expire expiration configuration
     */
    ExpirationConfiguration getExpireExpiration();

    /**
     * Gets the item lore configuration for auction item display.
     *
     * @return the item lore configuration
     */
    ItemLoreConfiguration getItemLore();

    /**
     * Gets the time formatting configuration.
     *
     * @return the time configuration
     */
    TimeConfiguration getTime();

    /**
     * Gets the action configuration for click actions.
     *
     * @return the action configuration
     */
    ActionConfiguration getActions();

    /**
     * Gets the date format used for displaying dates.
     *
     * @return the date format
     */
    SimpleDateFormat getDateFormat();

    /**
     * Gets the sort configuration.
     *
     * @return the sort configuration
     */
    SortConfiguration getSort();

    /**
     * Gets the permission configuration.
     *
     * @return the permission configuration
     */
    PermissionConfiguration getPermission();

    /**
     * Gets the world configuration for world restrictions.
     *
     * @return the world configuration
     */
    WorldConfiguration getWorld();

    /**
     * Gets the special items configuration.
     *
     * @return the special items configuration
     */
    SpecialItemsConfiguration getSpecialItems();

    /**
     * Gets the item display configuration.
     *
     * @return the item display configuration
     */
    ItemDisplayConfiguration getItemDisplay();

    /**
     * Gets the performance debug configuration.
     *
     * @return the performance debug configuration
     */
    PerformanceDebugConfiguration getPerformanceDebug();

    /**
     * Gets the auto-claim configuration.
     *
     * @return the auto-claim configuration
     */
    AutoClaimConfiguration getAutoClaimConfiguration();

    /**
     * Gets the sales notification configuration.
     *
     * @return the sales notification configuration
     */
    SalesNotificationConfiguration getSalesNotificationConfiguration();

    /**
     * Gets the performance configuration including cache thresholds and cluster timeouts.
     *
     * @return the performance configuration
     */
    PerformanceConfiguration getPerformance();

    /**
     * Gets the search filter configuration containing operator mappings.
     *
     * @return the search filter configuration
     */
    SearchFilterConfiguration getSearchFilter();

    /**
     * Gets the broadcast configuration.
     *
     * @return the broadcast configuration
     */
    BroadcastConfiguration getBroadcast();

    /**
     * Checks if the sell inventory is enabled.
     * When enabled, /ah sell without price argument opens a sell inventory
     * where players can select items, adjust price and choose economy.
     *
     * @return true if sell inventory is enabled, false otherwise
     */
    boolean isSellInventoryEnabled();

    /**
     * Gets the list of inventory-based command configurations.
     *
     * @return list of inventory command configurations
     */
    List<InventoryCommandConfiguration> getInventoryCommands();

    /**
     * Gets the history configuration.
     *
     * @return the history configuration
     */
    HistoryConfiguration getHistory();

    /**
     * Gets the command cooldown configuration.
     *
     * @return the cooldown configuration
     */
    CooldownConfiguration getCooldown();

    /**
     * Loads a command configuration from the specified path.
     *
     * @param path      the configuration path
     * @param enumClass the enum class for command arguments
     * @param <T>       the enum type
     * @return the loaded command configuration
     */
    <T extends Enum<T>> CommandConfiguration<T> loadCommandConfiguration(String path, Class<T> enumClass);

    /**
     * Loads command aliases from the specified path.
     *
     * @param path the configuration path
     * @return list of command aliases
     */
    List<String> loadCommandAliases(String path);

    /**
     * Loads a simple command configuration from the specified path.
     *
     * @param path the configuration path
     * @return the loaded simple command configuration
     */
    SimpleCommandConfiguration loadSimpleCommandConfiguration(String path);
}
