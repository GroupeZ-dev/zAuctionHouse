package fr.maxlego08.zauctionhouse.migration.v3.items;

import fr.maxlego08.zauctionhouse.migration.v3.V3StorageType;

import java.util.UUID;

/**
 * Represents an auction item from zAuctionHouse V3.
 * This is a data class used during migration to V4.
 */
public class V3AuctionItem {

    private final UUID id;
    private final String itemstack;
    private final long price;
    private final UUID seller;
    private final UUID buyer;
    private final String economy;
    private final String auctionType; // DEFAULT or INVENTORY
    private final long expireAt;
    private final V3StorageType storageType;
    private final String sellerName;
    private final String serverName;
    private final int priority;

    public V3AuctionItem(UUID id, String itemstack, long price, UUID seller, UUID buyer,
                         String economy, String auctionType, long expireAt,
                         V3StorageType storageType, String sellerName, String serverName, int priority) {
        this.id = id;
        this.itemstack = itemstack;
        this.price = price;
        this.seller = seller;
        this.buyer = buyer;
        this.economy = economy;
        this.auctionType = auctionType;
        this.expireAt = expireAt;
        this.storageType = storageType;
        this.sellerName = sellerName;
        this.serverName = serverName;
        this.priority = priority;
    }

    public UUID getId() {
        return id;
    }

    public String getItemstack() {
        return itemstack;
    }

    public long getPrice() {
        return price;
    }

    public UUID getSeller() {
        return seller;
    }

    public UUID getBuyer() {
        return buyer;
    }

    public String getEconomy() {
        return economy;
    }

    public String getAuctionType() {
        return auctionType;
    }

    public boolean isInventoryType() {
        return "INVENTORY".equalsIgnoreCase(auctionType);
    }

    public long getExpireAt() {
        return expireAt;
    }

    public V3StorageType getStorageType() {
        return storageType;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getServerName() {
        return serverName;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "V3AuctionItem{id=" + id + ", seller=" + seller + ", price=" + price +
               ", storageType=" + storageType + ", auctionType=" + auctionType + "}";
    }
}
