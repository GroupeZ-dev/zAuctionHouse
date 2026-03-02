package fr.maxlego08.zauctionhouse.economy;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.tax.TaxConfiguration;
import fr.maxlego08.zauctionhouse.tax.ZTaxConfiguration;
import fr.traqueur.currencies.CurrencyProvider;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;

public class ZAuctionEconomy implements AuctionEconomy {

    private final AuctionPlugin plugin;
    private final CurrencyProvider currencyProvider;
    private final String name;
    private final String displayName;
    private final String format;
    private final String symbol;
    private final String permission;
    private final String depositReason;
    private final String withdrawReason;
    private final PriceFormat priceFormat;
    private final EnumMap<ItemType, BigDecimal> minPrices;
    private final EnumMap<ItemType, BigDecimal> maxPrices;
    private final boolean autoClaim;
    private final boolean mustBeOnline;
    private final TaxConfiguration taxConfiguration;

    public ZAuctionEconomy(AuctionPlugin plugin, CurrencyProvider currencyProvider, String name, String displayName, String format, String symbol, String permission, String depositReason, String withdrawReason, PriceFormat priceFormat, EnumMap<ItemType, BigDecimal> minPrices, EnumMap<ItemType, BigDecimal> maxPrices, boolean autoClaim, boolean mustBeOnline, TaxConfiguration taxConfiguration) {
        this.plugin = plugin;
        this.currencyProvider = currencyProvider;
        this.name = name;
        this.displayName = displayName;
        this.format = format;
        this.symbol = symbol;
        this.permission = permission;
        this.depositReason = depositReason;
        this.withdrawReason = withdrawReason;
        this.priceFormat = priceFormat;
        this.minPrices = minPrices;
        this.maxPrices = maxPrices;
        this.autoClaim = autoClaim;
        this.mustBeOnline = mustBeOnline;
        this.taxConfiguration = taxConfiguration != null ? taxConfiguration : ZTaxConfiguration.disabled();
    }

    public AuctionPlugin getPlugin() {
        return this.plugin;
    }

    public CurrencyProvider getCurrencyProvider() {
        return this.currencyProvider;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public String getFormat() {
        return this.format;
    }

    @Override
    public CompletableFuture<BigDecimal> get(OfflinePlayer offlinePlayer) {
        return CompletableFuture.completedFuture(this.currencyProvider.getBalance(offlinePlayer));
    }

    @Override
    public CompletableFuture<Boolean> has(OfflinePlayer offlinePlayer, BigDecimal price) {
        return get(offlinePlayer).thenApply(balance -> balance.compareTo(price) >= 0);
    }

    @Override
    public void deposit(OfflinePlayer offlinePlayer, BigDecimal value, String reason) {
        this.currencyProvider.deposit(offlinePlayer, value, reason);
    }

    @Override
    public void withdraw(OfflinePlayer offlinePlayer, BigDecimal value, String reason) {
        this.currencyProvider.withdraw(offlinePlayer, value, reason);
    }

    @Override
    public String getSymbol() {
        return this.symbol;
    }

    @Override
    @Nullable
    public String getPermission() {
        return this.permission;
    }

    @Override
    public PriceFormat getPriceFormat() {
        return this.priceFormat;
    }

    @Override
    public String getDepositReason() {
        return this.depositReason;
    }

    @Override
    public String getWithdrawReason() {
        return this.withdrawReason;
    }

    @Override
    public boolean isAutoClaim() {
        return this.autoClaim;
    }

    @Override
    public boolean mustBeOnline() {
        return this.mustBeOnline;
    }

    @Override
    public BigDecimal getMaxPrice(ItemType itemType) {
        return this.maxPrices.get(itemType);
    }

    @Override
    public BigDecimal getMinPrice(ItemType itemType) {
        return this.minPrices.get(itemType);
    }

    @Override
    public TaxConfiguration getTaxConfiguration() {
        return this.taxConfiguration;
    }
}
