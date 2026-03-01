package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;

public abstract class ZItem implements Item {

    protected final AuctionPlugin plugin;
    protected final int id;
    protected final String serverName;
    protected final UUID sellerUniqueId;
    protected final String sellerName;
    protected final BigDecimal price;
    protected final String economyName;
    protected AuctionEconomy auctionEconomy;
    protected final Date createdAt;
    protected final PerformanceDebug performanceDebug;

    protected Date expiredAt;
    protected ItemStatus itemStatus = ItemStatus.AVAILABLE;
    protected UUID buyerUniqueId;
    protected String buyerName;
    protected Set<Category> categories = new HashSet<>();

    public ZItem(AuctionPlugin plugin, int id, String serverName, UUID sellerUniqueId, String sellerName, BigDecimal price, AuctionEconomy auctionEconomy, Date createdAt, Date expiredAt) {
        this.plugin = plugin;
        this.id = id;
        this.serverName = serverName;
        this.sellerUniqueId = sellerUniqueId;
        this.sellerName = sellerName;
        this.price = price;
        this.economyName = auctionEconomy.getName();
        this.auctionEconomy = auctionEconomy;
        this.createdAt = createdAt;
        this.expiredAt = expiredAt;
        this.performanceDebug = new PerformanceDebug(plugin);
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getServerName() {
        return this.serverName;
    }

    @Override
    public UUID getSellerUniqueId() {
        return this.sellerUniqueId;
    }

    @Override
    public String getSellerName() {
        return this.sellerName;
    }

    @Override
    public BigDecimal getPrice() {
        return this.price;
    }

    @Override
    public AuctionEconomy getAuctionEconomy() {
        return this.auctionEconomy;
    }

    @Override
    public void setAuctionEconomy(AuctionEconomy auctionEconomy) {
        this.auctionEconomy = auctionEconomy;
    }

    @Override
    public String getEconomyName() {
        return this.economyName;
    }

    @Override
    public Date getCreatedAt() {
        return this.createdAt;
    }

    @Override
    public Date getExpiredAt() {
        return this.expiredAt;
    }

    @Override
    public void setExpiredAt(Date expiredAt) {
        this.expiredAt = expiredAt;
    }

    @Override
    public Placeholders createPlaceholders(Player player) {
        return this.performanceDebug.measureWithContext("item.CreatePlaceholders", () -> {
            Placeholders placeholders = new Placeholders();
            placeholders.register("economy-name", this.auctionEconomy.getName());
            placeholders.register("economy-display-name", this.auctionEconomy.getDisplayName());
            placeholders.register("seller", this.getSellerName());
            placeholders.register("status", this.createStatus(player));
            placeholders.register("price", getFormattedPrice());
            placeholders.register("time-remaining", this.getRemainingTime());
            placeholders.register("formatted-expire-date", this.getFormattedExpireDate());
            return placeholders;
        }, () -> "for=" + player.getName() + ", itemId=" + this.id);
    }

    @Override
    public OfflinePlayer getSeller() {
        return Bukkit.getOfflinePlayer(this.sellerUniqueId);
    }

    @Override
    public String getFormattedExpireDate() {
        return this.plugin.getConfiguration().getDateFormat().format(this.expiredAt);
    }

    @Override
    public String getFormattedPrice() {
        return this.plugin.getEconomyManager().format(this.auctionEconomy, this.price);
    }

    @Override
    public String getRemainingTime() {
        var remainingMilliSeconds = this.expiredAt.getTime() - System.currentTimeMillis();
        return this.plugin.getConfiguration().getTime().getStringTime(remainingMilliSeconds);
    }

    @Override
    public boolean isExpired() {
        return System.currentTimeMillis() >= this.expiredAt.getTime() && this.expiredAt.getTime() != 0;
    }

    @Override
    public boolean canReceiveItem(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

    @Override
    public ItemStatus getStatus() {
        return this.itemStatus;
    }

    @Override
    public void setStatus(ItemStatus status) {
        this.itemStatus = status;
    }

    @Override
    public UUID getBuyerUniqueId() {
        return this.buyerUniqueId;
    }

    @Override
    public String getBuyerName() {
        return this.buyerName;
    }

    @Override
    public void setBuyer(Player player) {
        this.buyerUniqueId = player.getUniqueId();
        this.buyerName = player.getName();
    }

    @Override
    public void setBuyer(UUID buyerUniqueId, String buyerName) {
        this.buyerUniqueId = buyerUniqueId;
        this.buyerName = buyerName;
    }

    @Override
    public Set<Category> getCategories() {
        return this.categories;
    }

    @Override
    public void setCategories(Set<Category> categories) {
        this.categories = categories != null ? categories : new HashSet<>();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        return object instanceof Item item && id == item.getId();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
