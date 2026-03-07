package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.RefreshButton;
import org.bukkit.configuration.file.YamlConfiguration;

public class RefreshLoader extends ButtonLoader {

    private final AuctionPlugin plugin;
    private final InventoryManager inventoryManager;

    public RefreshLoader(AuctionPlugin plugin, InventoryManager inventoryManager) {
        super(plugin, "ZAUCTIONHOUSE_REFRESH");
        this.plugin = plugin;
        this.inventoryManager = inventoryManager;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        var menuItemStack = this.inventoryManager.loadItemStack(configuration, path + "loading-item.", defaultButtonValue.getFile());
        return new RefreshButton(this.plugin, menuItemStack);
    }
}
