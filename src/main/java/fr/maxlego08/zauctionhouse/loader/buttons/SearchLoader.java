package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.SearchButton;
import org.bukkit.configuration.file.YamlConfiguration;

public class SearchLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public SearchLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_SEARCH");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        String noneValue = configuration.getString(path + "none-value", "None");
        String activeValue = configuration.getString(path + "active-value", "true");
        String inactiveValue = configuration.getString(path + "inactive-value", "false");
        boolean dialogEnabled = configuration.getBoolean(path + "dialog.enabled", false);
        return new SearchButton(this.plugin, noneValue, activeValue, inactiveValue, dialogEnabled);
    }
}
