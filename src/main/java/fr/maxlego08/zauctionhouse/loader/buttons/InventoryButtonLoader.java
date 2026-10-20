package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.function.BiFunction;

/**
 * Loader for the buttons that open a player section (expired, purchased, selling items).
 * <p>
 * By default the button is only active when the section contains items, otherwise its
 * {@code else} button is shown. With {@code open-when-empty: true} it stays clickable and opens
 * the section even when it is empty.
 *
 * Example configuration:
 * <pre>
 * expired-items:
 *   type: ZAUCTIONHOUSE_EXPIRED_INVENTORY
 *   slot: 45
 *   open-when-empty: true
 * </pre>
 */
public class InventoryButtonLoader extends ButtonLoader {

    private final AuctionPlugin plugin;
    private final BiFunction<Plugin, Boolean, Button> factory;

    public InventoryButtonLoader(AuctionPlugin plugin, String name, BiFunction<Plugin, Boolean, Button> factory) {
        super(plugin, name);
        this.plugin = plugin;
        this.factory = factory;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        return this.factory.apply(this.plugin, configuration.getBoolean(path + "open-when-empty", false));
    }
}
