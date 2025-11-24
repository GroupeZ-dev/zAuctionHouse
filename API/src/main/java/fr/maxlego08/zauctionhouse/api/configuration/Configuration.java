package fr.maxlego08.zauctionhouse.api.configuration;

import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ActionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemLoreConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.NumberMultiplicationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SortConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.TimeConfiguration;
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

    <T extends Enum<T>> CommandConfiguration<T> loadCommandConfiguration(String path, Class<T> enumClass);
}
