package fr.maxlego08.zauctionhouse.configuration;

import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandArgumentConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandConfiguration;
import fr.maxlego08.zauctionhouse.api.messages.MessageColor;
import fr.maxlego08.zauctionhouse.utils.YamlLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MainConfiguration extends YamlLoader implements Configuration {

    private final AuctionPlugin plugin;
    private final List<MessageColor> messageColors = new ArrayList<>();
    private boolean enableDebug;

    public MainConfiguration(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void load() {

        // ToDo, add the system to load a config.yml by language,
        //  this allows to have the comments in the user’s language.

        super.loadYamlConfirmation(this.plugin, this.plugin.getConfig());
    }

    @Override
    public boolean isEnableDebug() {
        return this.enableDebug;
    }

    @Override
    public List<MessageColor> getMessageColors() {
        return this.messageColors;
    }

    @Override
    public <T extends Enum<T>> CommandConfiguration<T> loadCommandConfiguration(String path, Class<T> enumClass) {
        var config = plugin.getConfig();

        var aliases = config.getStringList(path + "aliases");
        var arguments = new ArrayList<CommandArgumentConfiguration<T>>();

        for (Map<?, ?> map : config.getMapList(path + "arguments")) {
            TypedMapAccessor accessor = new TypedMapAccessor((Map<String, Object>) map);

            var name = accessor.getString("name");
            if (name == null) {
                this.plugin.getLogger().severe("Missing name for " + path);
                continue;
            }

            T enumValue;
            try {
                enumValue = Enum.valueOf(enumClass, name.toUpperCase());
            } catch (IllegalArgumentException e) {
                var possible = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList();
                this.plugin.getLogger().severe("Invalid enum value '" + name + "' for enum " + enumClass.getSimpleName() + ". Possible values: " + String.join(", ", possible));
                continue;
            }


            var displayName = accessor.getString("display-name", name);
            if (displayName == null) {
                this.plugin.getLogger().severe("Impossible to find an aliases display-name for " + path);
                continue;
            }

            var required = accessor.getBoolean("required", false);
            var autoCompletion = accessor.getList("auto-completion").stream().map(String::valueOf).toList();

            arguments.add(new CommandArgumentConfiguration<>(enumValue, displayName, required, autoCompletion));
        }

        return new CommandConfiguration<>(aliases, arguments);
    }
}
