package fr.maxlego08.zauctionhouse.economy;

import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.economy.NumberFormatReduction;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionLoadEconomyEvent;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.tax.*;
import fr.maxlego08.zauctionhouse.tax.ZItemTaxRule;
import fr.maxlego08.zauctionhouse.tax.ZTaxConfiguration;
import fr.traqueur.currencies.Currencies;
import fr.traqueur.currencies.CurrencyProvider;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

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

        // Clear existing economies before reload
        this.economies.clear();

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
            var display = accessor.getString("display", "%amount%");

            if (format == null || format.isEmpty()) {
                this.plugin.getLogger().severe("Price reduction format is not set, skip it...");
                continue;
            }

            if (maxAmount == null || maxAmount.isEmpty()) {
                this.plugin.getLogger().severe("Price reduction max amount is not set, skip it...");
                continue;
            }

            try {
                this.priceReductions.add(new NumberFormatReduction(format, new BigDecimal(maxAmount), display));
            } catch (NumberFormatException e) {
                this.plugin.getLogger().warning("Invalid price reduction max-amount '" + maxAmount + "' for format '" + format + "', skipping...");
            }
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
        boolean mustBeOnline = accessor.getBoolean("must-be-online", false);

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

        // Load tax configuration
        TaxConfiguration taxConfiguration = loadTaxConfiguration(name, accessor);

        var auctionEconomy = new ZAuctionEconomy(this.plugin, currencyProvider, name, displayName, format, symbol, permission, depositReason, withdrawReason, priceFormat, minPrices, maxPrices, autoClaim, mustBeOnline, taxConfiguration);
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

    /**
     * Loads the tax configuration from the economy configuration.
     *
     * @param economyName the name of the economy (for logging)
     * @param accessor    the accessor to the economy configuration
     * @return the loaded tax configuration, or a disabled configuration if not present
     */
    private TaxConfiguration loadTaxConfiguration(String economyName, TypedMapAccessor accessor) {
        if (!accessor.contains("tax")) {
            return ZTaxConfiguration.disabled();
        }

        Object taxObject = accessor.getObject("tax");
        if (!(taxObject instanceof Map<?, ?> taxMap)) {
            return ZTaxConfiguration.disabled();
        }

        TypedMapAccessor taxAccessor = new TypedMapAccessor((Map<String, Object>) taxMap);

        boolean enabled = taxAccessor.getBoolean("enabled", false);
        if (!enabled) {
            return ZTaxConfiguration.disabled();
        }

        // Parse tax type
        String taxTypeName = taxAccessor.getString("type", "SELL");
        TaxType taxType;
        try {
            taxType = TaxType.valueOf(taxTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("Invalid tax type '" + taxTypeName + "' for economy '" + economyName + "', defaulting to SELL");
            taxType = TaxType.SELL;
        }

        // Parse amount type
        String amountTypeName = taxAccessor.getString("amount-type", "PERCENTAGE");
        TaxAmountType amountType;
        try {
            amountType = TaxAmountType.valueOf(amountTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("Invalid tax amount type '" + amountTypeName + "' for economy '" + economyName + "', defaulting to PERCENTAGE");
            amountType = TaxAmountType.PERCENTAGE;
        }

        double amount = taxAccessor.getDouble("amount", 0);
        String bypassPermission = taxAccessor.getString("bypass-permission", null);

        // Load reductions
        List<TaxReduction> reductions = new ArrayList<>();
        if (taxAccessor.contains("reductions")) {
            Object reductionsObject = taxAccessor.getObject("reductions");
            if (reductionsObject instanceof List<?> reductionsList) {
                for (Object reductionObj : reductionsList) {
                    if (reductionObj instanceof Map<?, ?> reductionMap) {
                        TypedMapAccessor reductionAccessor = new TypedMapAccessor((Map<String, Object>) reductionMap);
                        String permission = reductionAccessor.getString("permission", null);
                        double percentage = reductionAccessor.getDouble("percentage", 0);
                        if (permission != null && !permission.isEmpty() && percentage > 0) {
                            reductions.add(new TaxReduction(permission, percentage));
                        }
                    }
                }
            }
        }

        // Load item rules
        boolean itemRulesEnabled = false;
        List<ItemTaxRule> itemRules = new ArrayList<>();

        if (taxAccessor.contains("item-rules")) {
            Object itemRulesObject = taxAccessor.getObject("item-rules");
            if (itemRulesObject instanceof Map<?, ?> itemRulesMap) {
                TypedMapAccessor itemRulesAccessor = new TypedMapAccessor((Map<String, Object>) itemRulesMap);
                itemRulesEnabled = itemRulesAccessor.getBoolean("enabled", false);

                if (itemRulesEnabled && itemRulesAccessor.contains("rules")) {
                    Object rulesObject = itemRulesAccessor.getObject("rules");
                    if (rulesObject instanceof List<?> rulesList) {
                        for (Object ruleObj : rulesList) {
                            if (ruleObj instanceof Map<?, ?> ruleMap) {
                                ItemTaxRule itemTaxRule = loadItemTaxRule(economyName, (Map<String, Object>) ruleMap);
                                if (itemTaxRule != null) {
                                    itemRules.add(itemTaxRule);
                                }
                            }
                        }
                    }
                }
            }
        }

        this.plugin.getLogger().info("Tax configuration loaded for economy '" + economyName + "': " +
                "type=" + taxType + ", amountType=" + amountType + ", amount=" + amount +
                ", reductions=" + reductions.size() + ", itemRules=" + itemRules.size());

        return new ZTaxConfiguration(enabled, taxType, amountType, amount, bypassPermission, reductions, itemRulesEnabled, itemRules);
    }

    /**
     * Loads an item tax rule from a configuration map.
     *
     * @param economyName the name of the economy (for logging)
     * @param ruleMap     the rule configuration map
     * @return the loaded item tax rule, or null if invalid
     */
    private ItemTaxRule loadItemTaxRule(String economyName, Map<String, Object> ruleMap) {
        TypedMapAccessor ruleAccessor = new TypedMapAccessor(ruleMap);

        String name = ruleAccessor.getString("name", null);
        if (name == null) {
            this.plugin.getLogger().warning("Item tax rule for economy '" + economyName + "' has no name, skipping");
            return null;
        }

        int priority = ruleAccessor.getInt("priority", 0);

        // Parse tax type
        String taxTypeName = ruleAccessor.getString("type", "SELL");
        TaxType taxType;
        try {
            taxType = TaxType.valueOf(taxTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("Invalid tax type '" + taxTypeName + "' for item rule '" + name + "', defaulting to SELL");
            taxType = TaxType.SELL;
        }

        // Parse amount type
        String amountTypeName = ruleAccessor.getString("amount-type", "PERCENTAGE");
        TaxAmountType amountType;
        try {
            amountType = TaxAmountType.valueOf(amountTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("Invalid tax amount type '" + amountTypeName + "' for item rule '" + name + "', defaulting to PERCENTAGE");
            amountType = TaxAmountType.PERCENTAGE;
        }

        double amount = ruleAccessor.getDouble("amount", 0);

        // Load the matching rule using the rule loader registry
        Object ruleObject = ruleAccessor.getObject("rule");
        if (!(ruleObject instanceof Map<?, ?> matchRuleMap)) {
            this.plugin.getLogger().warning("Item tax rule '" + name + "' has no valid rule configuration, skipping");
            return null;
        }

        Rule matchRule = this.plugin.getRuleLoaderRegistry().loadRule(matchRuleMap);
        if (matchRule == null) {
            this.plugin.getLogger().warning("Item tax rule '" + name + "' has an invalid rule configuration, skipping");
            return null;
        }

        return new ZItemTaxRule(name, priority, taxType, amountType, amount, matchRule);
    }
}
