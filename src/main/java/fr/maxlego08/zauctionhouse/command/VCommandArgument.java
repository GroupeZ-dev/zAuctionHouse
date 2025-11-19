package fr.maxlego08.zauctionhouse.command;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandArgumentConfiguration;
import fr.maxlego08.zauctionhouse.utils.commands.CollectionBiConsumer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class VCommandArgument<T extends Enum<T>> extends VCommand {

    protected final Map<T, Integer> argumentIndexes = new HashMap<>();
    protected final Class<T> enumClass;

    public VCommandArgument(AuctionPlugin plugin, Class<T> enumClass) {
        super(plugin);
        this.enumClass = enumClass;
        this.createCommandArguments(plugin, enumClass);
        populateIndexes();
    }

    public abstract void createCommandArguments(AuctionPlugin plugin, Class<T> enumClass);

    protected void populateIndexes() {
        for (T enumConstant : this.enumClass.getEnumConstants()) {
            this.argumentIndexes.putIfAbsent(enumConstant, -1);
        }
    }

    protected void forEachArgument(String path, Function<CommandArgumentConfiguration<T>, CollectionBiConsumer> consumer) {
        var commandArgument = this.plugin.getConfiguration().loadCommandConfiguration(path, enumClass);

        var arguments = commandArgument.arguments();
        for (int index = 0; index != arguments.size(); index++) {
            var commandArgumentConfiguration = arguments.get(index);
            this.argumentIndexes.put(commandArgumentConfiguration.name(), index);

            var autoCompletion = consumer.apply(commandArgumentConfiguration);
            if (commandArgumentConfiguration.required()) {
                this.addRequireArg(commandArgumentConfiguration.displayName(), autoCompletion);
            } else {
                this.addOptionalArg(commandArgumentConfiguration.displayName(), autoCompletion);
            }
        }
    }

    protected String argAsString(T value) {
        return argAsString(this.argumentIndexes.get(value));
    }

    protected String argAsString(T value, String defaultValue) {
        return argAsString(this.argumentIndexes.get(value), defaultValue);
    }

    protected boolean argAsBoolean(T value) {
        return argAsBoolean(this.argumentIndexes.get(value));
    }

    protected boolean argAsBoolean(T value, boolean defaultValue) {
        return argAsBoolean(this.argumentIndexes.get(value), defaultValue);
    }

    protected int argAsInteger(T value) {
        return argAsInteger(this.argumentIndexes.get(value));
    }

    protected int argAsInteger(T value, int defaultValue) {
        return argAsInteger(this.argumentIndexes.get(value), defaultValue);
    }

    protected double argAsDouble(T value) {
        return argAsDouble(this.argumentIndexes.get(value));
    }

    protected double argAsDouble(T value, double defaultValue) {
        return argAsDouble(this.argumentIndexes.get(value), defaultValue);
    }

    protected long argAsLong(T value) {
        return argAsLong(this.argumentIndexes.get(value));
    }

    protected long argAsLong(T value, long defaultValue) {
        return argAsLong(this.argumentIndexes.get(value), defaultValue);
    }

    protected Player argAsPlayer(T value) {
        return argAsPlayer(this.argumentIndexes.get(value));
    }

    protected Player argAsPlayer(T value, Player defaultValue) {
        return argAsPlayer(this.argumentIndexes.get(value), defaultValue);
    }

    protected OfflinePlayer argAsOfflinePlayer(T value) {
        return argAsOfflinePlayer(this.argumentIndexes.get(value));
    }

    protected OfflinePlayer argAsOfflinePlayer(T value, OfflinePlayer defaultValue) {
        return argAsOfflinePlayer(this.argumentIndexes.get(value), defaultValue);
    }
}
