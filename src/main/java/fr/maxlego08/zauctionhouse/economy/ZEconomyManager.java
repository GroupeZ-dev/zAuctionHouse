package fr.maxlego08.zauctionhouse.economy;

import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionLoadEconomyEvent;
import fr.traqueur.currencies.Currencies;
import fr.traqueur.currencies.CurrencyProvider;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ZEconomyManager implements EconomyManager {

    private final AuctionPlugin plugin;
    private final Set<AuctionEconomy> economies = new HashSet<>();

    public ZEconomyManager(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<AuctionEconomy> getEconomies() {
        return Collections.unmodifiableCollection(this.economies);
    }

    @Override
    public boolean registerEconomy(AuctionEconomy economy) {
        var optional = getEconomy(economy.getName());
        return optional.isEmpty() && this.economies.add(economy);
    }

    @Override
    public boolean removeEconomy(AuctionEconomy economy) {
        return this.economies.remove(economy);
    }

    @Override
    public Optional<AuctionEconomy> getEconomy(String economyName) {
        return this.economies.stream().filter(auctionEconomy -> auctionEconomy.getName().equalsIgnoreCase(economyName)).findFirst();
    }

    @Override
    public void loadEconomies() {

        File file = new File(this.plugin.getDataFolder(), "economies.yml");
        if (!file.exists()) {
            this.plugin.saveFile("economies.yml", false);
        }

        AuctionLoadEconomyEvent event = new AuctionLoadEconomyEvent(this.plugin, this);
        event.call();

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : configuration.getMapList("economies")) {
            loadEconomy(file, new TypedMapAccessor((Map<String, Object>) map));
        }
    }

    /**
     * Load an economy from the configuration file.
     *
     * @param file     the configuration file containing the economy configuration
     * @param accessor the accessor to the economy configuration
     */
    private void loadEconomy(File file, TypedMapAccessor accessor) {

        var name = accessor.getString("name", null);

        if (name == null) {
            this.plugin.getLogger().severe("An economy present in economies.yml is active but doesn’t have a name, please correct that!");
            return;
        }

        if (!accessor.getBoolean("is-enable", false)) {
            this.plugin.getLogger().info("Economy '" + name + "' is not active, skip it...");
            return;
        }

        String displayName = accessor.getString("display-name", name);
        if (displayName == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a display name, please correct that!");
            return;
        }

        String symbol = accessor.getString("symbol", "$");
        if (symbol == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a symbol, please correct that!");
            return;
        }

        String format = accessor.getString("format", "%price%$");
        if (format == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a format, please correct that!");
            return;
        }

        String permission = accessor.getString("permission", null);

        String depositReason = accessor.getString("deposit-reason", "Sale of x%amount% %item% (zAuctionHouse)");
        if (depositReason == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a deposit reason, please correct that!");
            return;
        }

        String withdrawReason = accessor.getString("withdraw-reason", "Purchase of x%amount% %item% (zAuctionHouse)");
        if (withdrawReason == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a withdraw reason, please correct that!");
            return;
        }

        var type = accessor.getString("type", "VAULT");
        if (type == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a type, please correct that!");
            return;
        }

        Currencies currencies = findCurrencies(type);
        if (currencies == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a valid type, please correct that!");
            return;
        }

        CurrencyProvider currencyProvider = switch (currencies) {
            case ITEM, ZMENUITEMS -> {
                var itemStackMap = accessor.getObject("item");
                if (itemStackMap == null) {
                    this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have an item, please correct that!");
                    yield null;
                }
                var menuItemStack = this.plugin.getInventoriesLoader().getInventoryManager().loadItemStack(file, "economies", (Map<String, Object>) itemStackMap);
                // ToDo, rework currencies engine for using an menuItemStack in the constructor
                yield null;
            }
            case ZESSENTIALS, ECOBITS, COINSENGINE, REDISECONOMY -> {
                String currencyName = accessor.getString("currency-name", null);
                if (currencyName == null) {
                    this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a currency name, please correct that!");
                    yield null;
                }
                yield currencies.createProvider(currencyName);
            }
            default -> currencies.createProvider();
        };

        if (currencyProvider == null) {
            this.plugin.getLogger().severe("Impossible to create the currency provider for the economy '" + name + "'.");
            return;
        }

        var auctionEconomy = new ZAuctionEconomy(this.plugin, currencyProvider, name, displayName, format, symbol, permission, depositReason, withdrawReason);
        this.economies.add(auctionEconomy);
        this.plugin.getLogger().info("Economy '" + name + "' loaded successfully!");
    }

    /**
     * Attempts to find a {@link Currencies} enum value based on the given currency name.
     *
     * @param currencyName the name of the currency
     * @return the corresponding {@link Currencies} enum value, or null if no match is found
     */
    private Currencies findCurrencies(String currencyName) {
        try {
            return Currencies.valueOf(currencyName.toUpperCase());
        } catch (Exception exception) {
            return null;
        }
    }
}
