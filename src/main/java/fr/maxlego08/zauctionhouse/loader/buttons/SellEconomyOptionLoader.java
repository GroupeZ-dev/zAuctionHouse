package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.function.BiFunction;

/**
 * Loader for the sell inventory buttons that accept an optional {@code economy} option
 * ({@code ZAUCTIONHOUSE_SELL_CONFIRM}, {@code ZAUCTIONHOUSE_SELL_ECONOMY}).
 * <p>
 * Without the option the button works with the economy selected in the sell inventory. With it,
 * the button uses that economy: the confirm button sells with it in one click, the economy
 * button selects it instead of cycling.
 *
 * Example configuration:
 * <pre>
 * confirm-level:
 *   type: ZAUCTIONHOUSE_SELL_CONFIRM
 *   slot: 24
 *   economy: level
 * </pre>
 */
public class SellEconomyOptionLoader extends ButtonLoader {

    private final AuctionPlugin plugin;
    private final BiFunction<Plugin, String, Button> factory;

    public SellEconomyOptionLoader(AuctionPlugin plugin, String name, BiFunction<Plugin, String, Button> factory) {
        super(plugin, name);
        this.plugin = plugin;
        this.factory = factory;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        String economyName = configuration.getString(path + "economy", null);
        if (economyName != null && economyName.isBlank()) economyName = null;

        // L'economie est relue a chaque clic (les economies peuvent etre rechargees) : ici on
        // previent seulement l'administrateur d'une faute de frappe.
        if (economyName != null && this.plugin.getEconomyManager().getEconomy(economyName).isEmpty()) {
            this.plugin.getLogger().warning("Unknown economy '" + economyName + "' for the button " + path
                    + " in " + configuration.getName() + ", the button will refuse to sell until it exists.");
        }

        return this.factory.apply(this.plugin, economyName);
    }
}
