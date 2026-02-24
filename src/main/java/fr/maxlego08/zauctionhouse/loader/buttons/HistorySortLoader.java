package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.history.HistorySortType;
import fr.maxlego08.zauctionhouse.buttons.history.HistorySortButton;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HistorySortLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public HistorySortLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_HISTORY_SORT");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        var enableText = configuration.getString(path + "enable-text", "&a%sorting%");
        var disableText = configuration.getString(path + "disable-text", "&7%sorting%");

        List<HistorySortType> sortTypes = new ArrayList<>();
        for (String sort : configuration.getStringList(path + "sorts")) {
            try {
                sortTypes.add(HistorySortType.valueOf(sort.toUpperCase()));
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Invalid history sort type: " + sort + ", skipping...");
            }
        }

        // Default to all sort types if none specified
        if (sortTypes.isEmpty()) {
            sortTypes.addAll(List.of(HistorySortType.values()));
        }

        // Load custom sort names
        Map<HistorySortType, String> sortNames = new EnumMap<>(HistorySortType.class);
        ConfigurationSection namesSection = configuration.getConfigurationSection(path + "sort-names");
        if (namesSection != null) {
            for (String key : namesSection.getKeys(false)) {
                try {
                    HistorySortType sortType = HistorySortType.valueOf(key.toUpperCase());
                    sortNames.put(sortType, namesSection.getString(key));
                } catch (Exception ignored) {
                }
            }
        }

        // Use default names for any not specified
        for (HistorySortType sortType : HistorySortType.values()) {
            sortNames.putIfAbsent(sortType, sortType.getDefaultDisplayName());
        }

        return new HistorySortButton(plugin, enableText, disableText, sortTypes, sortNames);
    }
}
