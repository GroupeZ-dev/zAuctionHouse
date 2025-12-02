package fr.maxlego08.zauctionhouse.economy;

import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.economy.NumberFormatReduction;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionLoadEconomyEvent;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.traqueur.currencies.Currencies;
import fr.traqueur.currencies.CurrencyProvider;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ZEconomyManager implements EconomyManager {

    private final AuctionPlugin plugin;
    private final Set<AuctionEconomy> economies = new HashSet<>();
    private final Map<ItemType, AuctionEconomy> defaultEconomies = new HashMap<>();
    private final List<NumberFormatReduction> priceReductions = new ArrayList<>();
    private DecimalFormat priceDecimalFormat;

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
        event.callEvent();

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : configuration.getMapList("economies")) {
            loadEconomy(file, new TypedMapAccessor((Map<String, Object>) map));
        }

        this.loadDefaultEconomies(configuration);
        this.loadConfiguration(configuration);
    }

    private void loadConfiguration(FileConfiguration configuration) {

        var decimalFormat = configuration.getString("price-decimal-format", "#,###.#");
        if (decimalFormat.isEmpty()) {
            this.plugin.getLogger().severe("Price decimal format is not set, skip it...");
            return;
        }

        this.priceDecimalFormat = new DecimalFormat(decimalFormat);

        this.priceReductions.clear();

        for (Map<?, ?> map : configuration.getMapList("price-reductions")) {
            TypedMapAccessor accessor = new TypedMapAccessor((Map<String, Object>) map);
            var format = accessor.getString("format");
            var maxAmount = accessor.getString("max-amount");
            var display = accessor.getString("display");

            if (format == null || format.isEmpty()) {
                this.plugin.getLogger().severe("Price reduction format is not set, skip it...");
                continue;
            }

            if (maxAmount == null || maxAmount.isEmpty()) {
                this.plugin.getLogger().severe("Price reduction max amount is not set, skip it...");
                continue;
            }

            this.priceReductions.add(new NumberFormatReduction(format, new BigDecimal(maxAmount), display));
        }
    }

    private void loadDefaultEconomies(FileConfiguration configuration) {
        this.defaultEconomies.clear();
        for (ItemType value : ItemType.values()) {
            var economyName = configuration.getString("default-economy." + value.name().toLowerCase(), null);
            if (economyName == null) {
                this.plugin.getLogger().severe("Default economy for " + value.name() + " is not set, skip it...");
                continue;
            }
            var economy = getEconomy(economyName);
            if (economy.isEmpty()) {
                this.plugin.getLogger().severe("Default economy for " + value.name() + " is not set, skip it...");
                continue;
            }
            this.defaultEconomies.put(value, economy.get());
        }
    }

    @Override
    public AuctionEconomy getDefaultEconomy(ItemType itemType) {
        return this.defaultEconomies.get(itemType);
    }

    @Override
    public DecimalFormat getPriceDecimalFormat() {
        return this.priceDecimalFormat;
    }

    @Override
    public List<NumberFormatReduction> getPriceReductions() {
        return this.priceReductions;
    }

    @Override
    public String format(PriceFormat priceFormat, Number number) {
        return switch (priceFormat) {
            case PRICE_WITH_REDUCTION -> getDisplayBalance(number);
            case PRICE_WITH_DECIMAL_FORMAT -> this.priceDecimalFormat.format(number);
            default -> number.toString();
        };
    }

    protected String getDisplayBalance(Number number) {
        if (number == null) {
            throw new IllegalArgumentException("number cannot be null");
        }

        BigDecimal numValue = toBigDecimal(number);

        for (NumberFormatReduction config : this.priceReductions) {
            if (config == null || config.maxAmount() == null) {
                continue;
            }

            BigDecimal maxAmount = config.maxAmount();

            if (numValue.compareTo(maxAmount) >= 0) {
                continue;
            }

            String displayText = config.display();
            String format = config.format();

            if (displayText == null || displayText.isEmpty()) {
                this.plugin.getLogger().severe("Display text is null or empty for format '" + format + "' in economy module config.yml");
                continue;
            }

            String formattedAmount = formatAmount(numValue, maxAmount, format);
            return displayText.replace("%amount%", formattedAmount);
        }

        return numValue.toPlainString();
    }

    private BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal bd) {
            return bd;
        }
        if (number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte) {
            return BigDecimal.valueOf(number.longValue());
        }
        return BigDecimal.valueOf(number.doubleValue());
    }

    private String formatAmount(BigDecimal value, BigDecimal maxAmount, String format) {
        if (format == null || format.isEmpty()) {
            return value.toPlainString();
        }

        if (format.indexOf('#') >= 0) {
            DecimalFormat decimalFormat = new DecimalFormat(format);
            return decimalFormat.format(value);
        }

        BigDecimal thousand = BigDecimal.valueOf(1000);
        BigDecimal divisor;

        if (maxAmount.compareTo(thousand) == 0) {
            divisor = thousand;
        } else {
            divisor = maxAmount.divide(thousand, 2, RoundingMode.HALF_UP);
        }

        BigDecimal reduced = value.divide(divisor, 2, RoundingMode.HALF_UP);
        return String.format(format, reduced);
    }


    @Override
    public String format(AuctionEconomy economy, Number number) {
        var result = economy.format(format(economy.getPriceFormat(), number), number.longValue());
        if (result.contains(":")) {
            result = this.plugin.getInventoriesLoader().getInventoryManager().getFontImage().replace(result);
        }
        return result;
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

        boolean autoClaim = accessor.getBoolean("auto-claim", true);

        var priceFormatName = accessor.getString("price-format", PriceFormat.PRICE_RAW.name());
        if (priceFormatName == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a price format, please correct that!");
            return;
        }

        PriceFormat priceFormat = findPriceFormat(priceFormatName);
        if (priceFormat == null) {
            this.plugin.getLogger().severe("Economy '" + name + "' is active but doesn’t have a valid price format, please correct that!");
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

        EnumMap<ItemType, BigDecimal> maxPrices = new EnumMap<>(ItemType.class);
        if (accessor.contains("max-prices")) {
            maxPrices = loadPrices(accessor.getObject("max-prices"), "max-prices for economy '" + name + "'");
        }
        EnumMap<ItemType, BigDecimal> minPrices = new EnumMap<>(ItemType.class);
        if (accessor.contains("min-prices")) {
            minPrices = loadPrices(accessor.getObject("min-prices"), "min-prices for economy '" + name + "'");
        }

        var auctionEconomy = new ZAuctionEconomy(this.plugin, currencyProvider, name, displayName, format, symbol, permission, depositReason, withdrawReason, priceFormat, minPrices, maxPrices, autoClaim);
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

    /**
     * Attempts to find a {@link PriceFormat} enum value based on the given price format name.
     *
     * @param priceFormatName the name of the price format
     * @return the corresponding {@link PriceFormat} enum value, or null if no match is found
     */
    private PriceFormat findPriceFormat(String priceFormatName) {
        try {
            return PriceFormat.valueOf(priceFormatName.toUpperCase());
        } catch (Exception exception) {
            return null;
        }
    }

    private EnumMap<ItemType, BigDecimal> loadPrices(Object object, String name) {

        EnumMap<ItemType, BigDecimal> values = new EnumMap<>(ItemType.class);

        if (object instanceof Number number) {
            for (ItemType value : ItemType.values()) {
                values.put(value, toBigDecimal(number));
            }
        } else if (object instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key instanceof String string && value instanceof Number number) {
                    ItemType itemType = ItemType.valueOf(string.toUpperCase());
                    values.put(itemType, toBigDecimal(number));
                } else {
                    this.plugin.getLogger().severe("Impossible to find the price format for " + name + " (map)");
                }
            });
        } else {
            this.plugin.getLogger().severe("Impossible to find the price format for " + name + " (type)");
        }

        return values;
    }
}
