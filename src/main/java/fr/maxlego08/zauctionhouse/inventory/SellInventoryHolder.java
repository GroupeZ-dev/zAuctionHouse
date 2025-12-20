package fr.maxlego08.zauctionhouse.inventory;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public class SellInventoryHolder implements InventoryHolder {

    public static final int CONFIRM_SLOT = 48;
    public static final int CANCEL_SLOT = 50;
    private static final Set<Integer> LOCKED_SLOTS = Set.of(45, 46, 47, 49, 51, 52, 53, CONFIRM_SLOT, CANCEL_SLOT);

    private final UUID playerUniqueId;
    private final BigDecimal price;
    private final long expiredAt;
    private final AuctionEconomy auctionEconomy;
    private Inventory inventory;
    private boolean completed;

    public SellInventoryHolder(UUID playerUniqueId, BigDecimal price, long expiredAt, AuctionEconomy auctionEconomy) {
        this.playerUniqueId = playerUniqueId;
        this.price = price;
        this.expiredAt = expiredAt;
        this.auctionEconomy = auctionEconomy;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getPlayerUniqueId() {
        return playerUniqueId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getExpiredAt() {
        return expiredAt;
    }

    public AuctionEconomy getAuctionEconomy() {
        return auctionEconomy;
    }

    public static Set<Integer> getLockedSlots() {
        return LOCKED_SLOTS;
    }

    public boolean isLockedSlot(int slot) {
        return LOCKED_SLOTS.contains(slot);
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
