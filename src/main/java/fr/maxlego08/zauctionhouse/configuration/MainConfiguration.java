package fr.maxlego08.zauctionhouse.configuration;

import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandArgumentConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.InventoryCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.SimpleArgumentConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.SimpleCommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ActionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.AutoClaimConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.BroadcastConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.CooldownConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ExpirationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.HistoryConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SalesNotificationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemDisplayConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemLoreConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.NumberMultiplicationConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceDebugConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PermissionConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SearchFilterConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SortConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SpecialItemsConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.TimeConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.WorldConfiguration;
import fr.maxlego08.zauctionhouse.api.messages.MessageColor;
import fr.maxlego08.zauctionhouse.utils.YamlLoader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class MainConfiguration extends YamlLoader implements Configuration {

    private final AuctionPlugin plugin;
    private final List<MessageColor> messageColors = new ArrayList<>();

    private boolean enableDebug;
    private boolean enablePerformanceDebug;
    private String serverName;
    private SimpleDateFormat dateFormat;

    private NumberMultiplicationConfiguration numberMultiplicationConfiguration;
    private ExpirationConfiguration sellExpiration;
    private ExpirationConfiguration rentExpiration;
    private ExpirationConfiguration bidExpiration;
    private ExpirationConfiguration purchaseExpiration;
    private ExpirationConfiguration expireExpiration;
    private ItemLoreConfiguration itemLoreConfiguration;
    private TimeConfiguration timeConfiguration;
    private ActionConfiguration actionConfiguration;
    private SortConfiguration sortConfiguration;
    private PermissionConfiguration permissionConfiguration;
    private WorldConfiguration worldConfiguration;
    private SpecialItemsConfiguration specialItemsConfiguration;
    private ItemDisplayConfiguration itemDisplayConfiguration;
    private PerformanceDebugConfiguration performanceDebugConfiguration;
    private AutoClaimConfiguration autoClaimConfiguration;
    private SalesNotificationConfiguration salesNotificationConfiguration;
    private BroadcastConfiguration broadcastConfiguration;
    private PerformanceConfiguration performanceConfiguration;
    private SearchFilterConfiguration searchFilterConfiguration;
    private HistoryConfiguration historyConfiguration;
    private CooldownConfiguration cooldownConfiguration;
    private List<InventoryCommandConfiguration> inventoryCommandConfigurations;
    private boolean sellInventoryEnabled;

    public MainConfiguration(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void load() {
        var config = this.plugin.getConfig();
        super.loadYamlConfirmation(this.plugin, config);

        this.numberMultiplicationConfiguration = NumberMultiplicationConfiguration.of(this.plugin, config);
        this.sellExpiration = ExpirationConfiguration.of(plugin, config, "expiration.auction.");
        this.rentExpiration = ExpirationConfiguration.of(plugin, config, "expiration.rent.");
        this.bidExpiration = ExpirationConfiguration.of(plugin, config, "expiration.bid.");
        this.purchaseExpiration = ExpirationConfiguration.of(plugin, config, "expiration.purchase.");
        this.expireExpiration = ExpirationConfiguration.of(plugin, config, "expiration.expire.");
        this.itemLoreConfiguration = ItemLoreConfiguration.of(plugin, config);
        this.timeConfiguration = TimeConfiguration.of(plugin, config);
        this.actionConfiguration = ActionConfiguration.of(plugin, config);
        this.sortConfiguration = SortConfiguration.of(plugin, config);
        this.permissionConfiguration = PermissionConfiguration.of(plugin, config);
        this.worldConfiguration = WorldConfiguration.of(plugin, config);
        this.specialItemsConfiguration = SpecialItemsConfiguration.of(plugin, config);
        this.itemDisplayConfiguration = ItemDisplayConfiguration.of(plugin, config);
        this.performanceDebugConfiguration = PerformanceDebugConfiguration.of(plugin, config);
        this.autoClaimConfiguration = AutoClaimConfiguration.of(plugin, config);
        this.salesNotificationConfiguration = SalesNotificationConfiguration.of(plugin, config);
        this.broadcastConfiguration = BroadcastConfiguration.of(plugin, config);
        this.performanceConfiguration = PerformanceConfiguration.of(plugin, config);
        this.searchFilterConfiguration = SearchFilterConfiguration.of(plugin, config);
        this.historyConfiguration = HistoryConfiguration.of(plugin, config);
        this.cooldownConfiguration = CooldownConfiguration.of(plugin, config);
        this.inventoryCommandConfigurations = InventoryCommandConfiguration.of(plugin, config);
        this.dateFormat = new SimpleDateFormat(config.getString("date-format", "dd/MM/yyyy HH:mm:ss"));
        String timezone = config.getString("timezone", "auto");
        if (timezone != null && !timezone.equalsIgnoreCase("auto")) {
            this.dateFormat.setTimeZone(TimeZone.getTimeZone(timezone));
        }
        this.sellInventoryEnabled = config.getBoolean("commands.sell.enable-sell-inventory", false);

        // Validate critical configurations
        validateConfigurations();
    }

    private void validateConfigurations() {
        if (this.sellExpiration == null) {
            this.plugin.getLogger().severe("Failed to load sell expiration configuration, plugin may not work correctly");
        }
        if (this.expireExpiration == null) {
            this.plugin.getLogger().severe("Failed to load expire expiration configuration, plugin may not work correctly");
        }
        if (this.actionConfiguration == null) {
            this.plugin.getLogger().severe("Failed to load action configuration, plugin may not work correctly");
        }
        if (this.sortConfiguration == null) {
            this.plugin.getLogger().severe("Failed to load sort configuration, plugin may not work correctly");
        }
        if (this.performanceConfiguration == null) {
            this.plugin.getLogger().severe("Failed to load performance configuration, plugin may not work correctly");
        }
    }

    @Override
    public boolean isEnableDebug() {
        return this.enableDebug;
    }

    @Override
    public boolean isEnablePerformanceDebug() {
        return this.enablePerformanceDebug;
    }

    @Override
    public String getServerName() {
        return this.serverName;
    }

    @Override
    public List<MessageColor> getMessageColors() {
        return this.messageColors;
    }

    @Override
    public NumberMultiplicationConfiguration getNumberMultiplicationConfiguration() {
        return this.numberMultiplicationConfiguration;
    }

    @Override
    public ExpirationConfiguration getSellExpiration() {
        return this.sellExpiration;
    }

    @Override
    public ExpirationConfiguration getRentExpiration() {
        return this.rentExpiration;
    }

    @Override
    public ExpirationConfiguration getBidExpiration() {
        return this.bidExpiration;
    }

    @Override
    public ExpirationConfiguration getPurchaseExpiration() {
        return this.purchaseExpiration;
    }

    @Override
    public ExpirationConfiguration getExpireExpiration() {
        return this.expireExpiration;
    }

    @Override
    public ItemLoreConfiguration getItemLore() {
        return this.itemLoreConfiguration;
    }

    @Override
    public TimeConfiguration getTime() {
        return this.timeConfiguration;
    }

    @Override
    public ActionConfiguration getActions() {
        return this.actionConfiguration;
    }

    @Override
    public SimpleDateFormat getDateFormat() {
        return this.dateFormat;
    }

    @Override
    public SortConfiguration getSort() {
        return this.sortConfiguration;
    }

    @Override
    public PermissionConfiguration getPermission() {
        return this.permissionConfiguration;
    }

    @Override
    public WorldConfiguration getWorld() {
        return this.worldConfiguration;
    }

    @Override
    public SpecialItemsConfiguration getSpecialItems() {
        return this.specialItemsConfiguration;
    }

    @Override
    public ItemDisplayConfiguration getItemDisplay() {
        return this.itemDisplayConfiguration;
    }

    @Override
    public PerformanceDebugConfiguration getPerformanceDebug() {
        return this.performanceDebugConfiguration;
    }

    @Override
    public AutoClaimConfiguration getAutoClaimConfiguration() {
        return this.autoClaimConfiguration;
    }

    @Override
    public SalesNotificationConfiguration getSalesNotificationConfiguration() {
        return this.salesNotificationConfiguration;
    }

    @Override
    public BroadcastConfiguration getBroadcast() {
        return this.broadcastConfiguration;
    }

    @Override
    public PerformanceConfiguration getPerformance() {
        return this.performanceConfiguration;
    }

    @Override
    public SearchFilterConfiguration getSearchFilter() {
        return this.searchFilterConfiguration;
    }

    @Override
    public HistoryConfiguration getHistory() {
        return this.historyConfiguration;
    }

    @Override
    public CooldownConfiguration getCooldown() {
        return this.cooldownConfiguration;
    }

    @Override
    public List<InventoryCommandConfiguration> getInventoryCommands() {
        return this.inventoryCommandConfigurations;
    }

    @Override
    public boolean isSellInventoryEnabled() {
        return this.sellInventoryEnabled;
    }

    @Override
    public <T extends Enum<T>> CommandConfiguration<T> loadCommandConfiguration(String path, Class<T> enumClass) {
        var config = plugin.getConfig();

        var aliases = config.getStringList(path + "aliases");
        var arguments = new ArrayList<CommandArgumentConfiguration<T>>();

        for (Map<?, ?> map : config.getMapList(path + "arguments")) {
            TypedMapAccessor accessor = new TypedMapAccessor((Map<String, Object>) map);

            var name = accessor.getString("name");
            if (name == null) {
                this.plugin.getLogger().severe("Missing name for " + path);
                continue;
            }

            T enumValue;
            try {
                enumValue = Enum.valueOf(enumClass, name.toUpperCase());
            } catch (IllegalArgumentException e) {
                var possible = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList();
                this.plugin.getLogger().severe("Invalid enum value '" + name + "' for enum " + enumClass.getSimpleName() + ". Possible values: " + String.join(", ", possible));
                continue;
            }


            var displayName = accessor.getString("display-name", name);
            if (displayName == null) {
                this.plugin.getLogger().severe("Impossible to find an aliases display-name for " + path);
                continue;
            }

            var required = accessor.getBoolean("required", false);
            var autoCompletion = accessor.getList("auto-completion").stream().map(String::valueOf).toList();
            var defaultValue = accessor.getString("default-value", null);

            arguments.add(new CommandArgumentConfiguration<>(enumValue, displayName, required, autoCompletion, defaultValue));
        }

        return new CommandConfiguration<>(aliases, arguments);
    }

    @Override
    public List<String> loadCommandAliases(String path) {
        return plugin.getConfig().getStringList(path + "aliases");
    }

    @Override
    public SimpleCommandConfiguration loadSimpleCommandConfiguration(String path) {
        var config = plugin.getConfig();

        var aliases = config.getStringList(path + "aliases");
        var arguments = new ArrayList<SimpleArgumentConfiguration>();

        for (Map<?, ?> map : config.getMapList(path + "arguments")) {
            TypedMapAccessor accessor = new TypedMapAccessor((Map<String, Object>) map);

            var name = accessor.getString("name");
            if (name == null) {
                this.plugin.getLogger().severe("Missing name for " + path);
                continue;
            }

            var displayName = accessor.getString("display-name", name);
            var required = accessor.getBoolean("required", false);
            var autoCompletion = accessor.getList("auto-completion").stream().map(String::valueOf).toList();

            arguments.add(new SimpleArgumentConfiguration(name, displayName, required, autoCompletion));
        }

        return new SimpleCommandConfiguration(aliases, arguments);
    }
}
