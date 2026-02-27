package fr.maxlego08.zauctionhouse.api.configuration;

import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.InventoryCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.SimpleCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ActionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.AutoClaimConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SalesNotificationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemDisplayConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemLoreConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.NumberMultiplicationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceDebugConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PermissionConfiguration;
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

    String getServerName();

    List<MessageColor> getMessageColors();

    NumberMultiplicationConfiguration getNumberMultiplicationConfiguration();

    ExpirationConfiguration getSellExpiration();

    ExpirationConfiguration getRentExpiration();

    ExpirationConfiguration getBidExpiration();

    ExpirationConfiguration getPurchaseExpiration();

    ExpirationConfiguration getExpireExpiration();

    ItemLoreConfiguration getItemLore();

    TimeConfiguration getTime();

    ActionConfiguration getActions();

    SimpleDateFormat getDateFormat();

    SortConfiguration getSort();

    PermissionConfiguration getPermission();

    WorldConfiguration getWorld();

    SpecialItemsConfiguration getSpecialItems();

    ItemDisplayConfiguration getItemDisplay();

    PerformanceDebugConfiguration getPerformanceDebug();

    AutoClaimConfiguration getAutoClaimConfiguration();

    SalesNotificationConfiguration getSalesNotificationConfiguration();

    /**
     * Checks if the sell inventory is enabled.
     * When enabled, /ah sell without price argument opens a sell inventory
     * where players can select items, adjust price and choose economy.
     *
     * @return true if sell inventory is enabled, false otherwise
     */
    boolean isSellInventoryEnabled();

    List<InventoryCommandConfiguration> getInventoryCommands();

    <T extends Enum<T>> CommandConfiguration<T> loadCommandConfiguration(String path, Class<T> enumClass);

    List<String> loadCommandAliases(String path);

    SimpleCommandConfiguration loadSimpleCommandConfiguration(String path);
}
