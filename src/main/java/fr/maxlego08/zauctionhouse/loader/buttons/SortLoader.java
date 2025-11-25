package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.buttons.SortButton;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;

public class SortLoader extends ButtonLoader {

    private final AuctionPlugin plugin;
    private final InventoryManager inventoryManager;

    public SortLoader(AuctionPlugin plugin, InventoryManager inventoryManager) {
        super(plugin, "ZAUCTIONHOUSE_CHANGE_SORT");
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {

        var enableText = configuration.getString(path + "enable-text");
        var disableText = configuration.getString(path + "disable-text");
        var sorts = new ArrayList<SortItem>();

        for (String sort : configuration.getStringList(path + "sorts")) {
            try {
                sorts.add(SortItem.valueOf(sort));
            } catch (Exception exception) {
                this.plugin.getLogger().severe("Impossible to find the sort type : " + sort + ", skip it...");
            }
        }

        var menuItemStack = this.inventoryManager.loadItemStack(configuration, path + "loading-item", defaultButtonValue.getFile());

        return new SortButton(this.plugin, enableText, disableText, menuItemStack, sorts);
    }
}
