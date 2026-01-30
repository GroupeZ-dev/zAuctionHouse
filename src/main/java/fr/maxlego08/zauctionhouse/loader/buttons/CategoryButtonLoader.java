package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.CategoryButton;
import org.bukkit.configuration.file.YamlConfiguration;

public class CategoryButtonLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public CategoryButtonLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_CATEGORY");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        String categoryId = configuration.getString(path + "category");

        if (categoryId == null || categoryId.isEmpty()) {
            this.plugin.getLogger().warning("Category button at " + path + " is missing 'category' field");
            categoryId = "misc"; // Fallback to misc
        }

        return new CategoryButton(this.plugin, categoryId);
    }
}
