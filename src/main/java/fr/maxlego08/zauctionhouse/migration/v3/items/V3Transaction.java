package fr.maxlego08.zauctionhouse.migration.v3.items;

import java.util.UUID;

/**
 * Represents a transaction from zAuctionHouse V3.
 * This is a data class used during migration to V4.
 */
public class V3Transaction {

    private final int id;
    private final UUID seller;
    private final UUID buyer;
    private final String itemstack;
    private final long transactionDate;
    private final long price;
    private final String economy;
    private final boolean isRead;
    private final boolean needMoney;

    public V3Transaction(int id, UUID seller, UUID buyer, String itemstack,
                         long transactionDate, long price, String economy,
                         boolean isRead, boolean needMoney) {
        this.id = id;
        this.seller = seller;
        this.buyer = buyer;
        this.itemstack = itemstack;
        this.transactionDate = transactionDate;
        this.price = price;
        this.economy = economy;
        this.isRead = isRead;
        this.needMoney = needMoney;
    }

    public int getId() {
        return id;
    }

    public UUID getSeller() {
        return seller;
    }

    public UUID getBuyer() {
        return buyer;
    }

    public String getItemstack() {
        return itemstack;
    }

    public long getTransactionDate() {
        return transactionDate;
    }

    public long getPrice() {
        return price;
    }

    public String getEconomy() {
        return economy;
    }

    public boolean isRead() {
        return isRead;
    }

    public boolean isNeedMoney() {
        return needMoney;
    }

    @Override
    public String toString() {
        return "V3Transaction{id=" + id + ", seller=" + seller + ", buyer=" + buyer +
               ", price=" + price + ", date=" + transactionDate + "}";
    }
}
