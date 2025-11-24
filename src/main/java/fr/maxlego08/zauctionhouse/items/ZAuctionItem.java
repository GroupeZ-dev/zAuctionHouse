package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ZAuctionItem extends ZItem implements AuctionItem {

    private final ItemStack itemStack;

    public ZAuctionItem(AuctionPlugin plugin, int id, UUID sellerUniqueId, String sellerName, BigDecimal price, AuctionEconomy auctionEconomy, Date createdAt, Date expiredAt, ItemStack itemStack) {
        super(plugin, id, sellerUniqueId, sellerName, price, auctionEconomy, createdAt, expiredAt);
        this.itemStack = itemStack;
    }

    @Override
    public ItemStack getItemStack() {
        return itemStack;
    }

    @Override
    public ItemStack buildItemStack(Player player) {
        return this.buildItemStack(player, this.plugin.getConfiguration().getItemLore().listedAuctionLore());
    }

    @Override
    public ItemStack buildItemStack(Player player, List<String> lore) {

        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();

        var itemStack = this.itemStack.clone();
        var itemMeta = itemStack.getItemMeta();

        Placeholders placeholders = createPlaceholders(player);

        meta.updateLore(itemMeta, lore.stream().map(placeholders::parse).toList(), LoreType.APPEND);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    @Override
    public String createStatus(Player player) {
        var config = this.plugin.getConfiguration().getItemLore();
        return this.sellerUniqueId.equals(player.getUniqueId()) ? config.sellerStatus() : config.buyerStatus();
    }

    @Override
    public int getAmount() {
        return this.itemStack.getAmount();
    }

    @Override
    public String getTranslationKey() {
        return this.itemStack.getType().translationKey();
    }
}
