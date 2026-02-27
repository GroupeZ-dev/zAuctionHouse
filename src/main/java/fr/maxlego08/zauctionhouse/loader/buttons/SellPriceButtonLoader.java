package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.sell.SellPriceButton;
import org.bukkit.configuration.file.YamlConfiguration;

import java.math.BigDecimal;

/**
 * Loader for SellPriceButton that reads configurable price change amounts.
 *
 * Example configuration:
 * <pre>
 * price-increase:
 *   type: ZAUCTIONHOUSE_SELL_PRICE
 *   slot: 14
 *   amounts:
 *     left-click: 100
 *     right-click: -100
 *     shift-left-click: 1000
 *     shift-right-click: -1000
 * </pre>
 */
public class SellPriceButtonLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public SellPriceButtonLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_SELL_PRICE");
        this.plugin = plugin;
    }

    @Override
    public Button load(YamlConfiguration configuration, String path, DefaultButtonValue defaultButtonValue) {
        String amountsPath = path + "amounts.";

        BigDecimal leftClick = getBigDecimal(configuration, amountsPath + "left-click", BigDecimal.valueOf(100));
        BigDecimal rightClick = getBigDecimal(configuration, amountsPath + "right-click", BigDecimal.valueOf(-100));
        BigDecimal shiftLeftClick = getBigDecimal(configuration, amountsPath + "shift-left-click", BigDecimal.valueOf(1000));
        BigDecimal shiftRightClick = getBigDecimal(configuration, amountsPath + "shift-right-click", BigDecimal.valueOf(-1000));

        return new SellPriceButton(this.plugin, leftClick, rightClick, shiftLeftClick, shiftRightClick);
    }

    private BigDecimal getBigDecimal(YamlConfiguration config, String path, BigDecimal defaultValue) {
        if (!config.contains(path)) {
            return defaultValue;
        }

        Object value = config.get(path);
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            this.plugin.getLogger().warning("Invalid number at " + path + ": " + value + ", using default: " + defaultValue);
            return defaultValue;
        }
    }
}
