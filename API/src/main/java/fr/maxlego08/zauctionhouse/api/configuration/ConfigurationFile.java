package fr.maxlego08.zauctionhouse.api.configuration;

/**
 * Represents a configuration file.
 * Implementations of this interface are responsible for loading configuration settings from a file.
 */
public interface ConfigurationFile {

    /**
     * Loads configuration settings from a file.
     */
    void load();

}

