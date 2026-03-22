package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.categories.CategorySwitcherButton;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

public class CategorySwitcherLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public CategorySwitcherLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_CATEGORY_SWITCHER");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {

        String enableText = configuration.getString(path + "enable-text", "&a%category%");
        String disableText = configuration.getString(path + "disable-text", "&7%category%");
        List<String> categories = configuration.getStringList(path + "categories");

        if (categories.isEmpty()) {
            this.plugin.getLogger().warning("Category switcher button at " + path + " has no categories defined");
        }

        return new CategorySwitcherButton(this.plugin, enableText, disableText, categories);
    }
}
