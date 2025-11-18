package fr.maxlego08.zauctionhouse.economy;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.traqueur.currencies.CurrencyProvider;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

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

    public ZAuctionEconomy(AuctionPlugin plugin, CurrencyProvider currencyProvider, String name, String displayName, String format, String symbol, String permission, String depositReason, String withdrawReason) {
        this.plugin = plugin;
        this.currencyProvider = currencyProvider;
        this.name = name;
        this.displayName = displayName;
        this.format = format;
        this.symbol = symbol;
        this.permission = permission;
        this.depositReason = depositReason;
        this.withdrawReason = withdrawReason;
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
    public BigDecimal get(OfflinePlayer offlinePlayer) {
        return this.currencyProvider.getBalance(offlinePlayer);
    }

    @Override
    public boolean has(OfflinePlayer offlinePlayer, BigDecimal price) {
        return get(offlinePlayer).compareTo(price) >= 0;
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
    public String getDepositReason() {
        return this.depositReason;
    }

    @Override
    public String getWithdrawReason() {
        return this.withdrawReason;
    }
}
