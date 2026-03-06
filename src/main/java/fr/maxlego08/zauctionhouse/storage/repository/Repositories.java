package fr.maxlego08.zauctionhouse.storage.repository;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.storage.Repository;

import java.util.HashMap;
import java.util.Map;

public class Repositories {

    private final AuctionPlugin plugin;
    private final DatabaseConnection connection;

    private final Map<Class<? extends Repository>, Repository> tables = new HashMap<>();

    public Repositories(AuctionPlugin plugin, DatabaseConnection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    public void register(Class<? extends Repository> tableClass) {
        try {
            Repository repository = tableClass.getConstructor(AuctionPlugin.class, DatabaseConnection.class).newInstance(this.plugin, this.connection);
            this.tables.put(tableClass, repository);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to register repository " + tableClass.getSimpleName() + ": " + exception.getMessage());
        }
    }

    public <T extends Repository> T getTable(Class<T> module) {
        return (T) this.tables.get(module);
    }

}
