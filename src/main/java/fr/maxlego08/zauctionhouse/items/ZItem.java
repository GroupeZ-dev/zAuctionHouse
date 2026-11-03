package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemPlaceholder;
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
    protected final Date createdAt;
    protected final PerformanceDebug performanceDebug;
    protected AuctionEconomy auctionEconomy;
    protected Date expiredAt;
    protected ItemStatus itemStatus = ItemStatus.AVAILABLE;
    /**
     * Detenteur LOCAL de la reservation de confirmation (achat, retrait vendeur, retrait admin).
     * Volatile : lu depuis les fils asynchrones des services et du pont cluster.
     */
    protected volatile UUID reservationOwner;
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
        return createPlaceholders(player, ItemPlaceholder.all());
    }

    @Override
    public Placeholders createPlaceholders(Player player, Set<ItemPlaceholder> needed) {
        return this.performanceDebug.measureWithContext("item.CreatePlaceholders", () -> {
            Placeholders placeholders = new Placeholders();
            if (needed.contains(ItemPlaceholder.ECONOMY_NAME))
                placeholders.register("economy-name", this.auctionEconomy.getName());
            if (needed.contains(ItemPlaceholder.ECONOMY_DISPLAY_NAME))
                placeholders.register("economy-display-name", this.auctionEconomy.getDisplayName());
            if (needed.contains(ItemPlaceholder.SELLER))
                placeholders.register("seller", this.getSellerName());
            if (needed.contains(ItemPlaceholder.STATUS))
                placeholders.register("status", this.createStatus(player));
            if (needed.contains(ItemPlaceholder.PRICE))
                placeholders.register("price", getFormattedPrice());
            if (needed.contains(ItemPlaceholder.PRICE_RAW))
                placeholders.register("price-price-raw", getFormattedPrice(PriceFormat.PRICE_RAW));
            if (needed.contains(ItemPlaceholder.PRICE_WITH_DECIMAL_FORMAT))
                placeholders.register("price-price-with-decimal-format", getFormattedPrice(PriceFormat.PRICE_WITH_DECIMAL_FORMAT));
            if (needed.contains(ItemPlaceholder.PRICE_WITH_REDUCTION))
                placeholders.register("price-price-with-reduction", getFormattedPrice(PriceFormat.PRICE_WITH_REDUCTION));
            if (needed.contains(ItemPlaceholder.PRICE_WITHOUT_DECIMAL))
                placeholders.register("price-price-without-decimal", getFormattedPrice(PriceFormat.PRICE_WITHOUT_DECIMAL));
            if (needed.contains(ItemPlaceholder.TIME_REMAINING))
                placeholders.register("time-remaining", this.getRemainingTime());
            if (needed.contains(ItemPlaceholder.FORMATTED_EXPIRE_DATE))
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
    public String getFormattedPrice(PriceFormat priceFormat) {
        return this.plugin.getEconomyManager().format(priceFormat, this.price);
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
    public synchronized void setStatus(ItemStatus status) {
        this.itemStatus = status;
        // Toute remise en vente (fermeture d'une confirmation, balayage de maintenance, message
        // d'un autre serveur) met fin a la reservation : une reservation posee ensuite par un
        // autre joueur, ici ou ailleurs, ne pourra jamais etre prise pour celle de l'ancien
        // detenteur. Les restaurations IS_BEING_* -> IS_*_CONFIRM conservent le detenteur.
        if (status == ItemStatus.AVAILABLE) this.reservationOwner = null;
    }

    /**
     * Version atomique : statut et detenteur sont poses sous le meme moniteur que
     * {@link #setStatus}, si bien qu'une remise en vente concurrente (autre region Folia,
     * message du cluster) ne peut ni s'intercaler entre les deux ni laisser un detenteur perime
     * sur une reservation posee ensuite par un autre serveur.
     */
    @Override
    public synchronized boolean tryReserve(ItemStatus confirmStatus, UUID owner) {
        if (this.itemStatus != ItemStatus.AVAILABLE) return false;
        this.itemStatus = confirmStatus;
        this.reservationOwner = owner;
        return true;
    }

    @Override
    public UUID getReservationOwner() {
        return this.reservationOwner;
    }

    @Override
    public synchronized void setReservationOwner(UUID owner) {
        this.reservationOwner = owner;
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
