package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ZAuctionItem extends ZItem implements AuctionItem {

    private final List<ItemStack> itemStacks;

    public ZAuctionItem(AuctionPlugin plugin, int id, String serverName, UUID sellerUniqueId, String sellerName, BigDecimal price, AuctionEconomy auctionEconomy, Date createdAt, Date expiredAt, List<ItemStack> itemStacks) {
        super(plugin, id, serverName, sellerUniqueId, sellerName, price, auctionEconomy, createdAt, expiredAt);
        this.itemStacks = itemStacks;
    }

    @Override
    public List<ItemStack> getItemStacks() {
        return itemStacks;
    }

    @Override
    public ItemStack buildItemStack(Player player) {
        var configuration = this.plugin.getConfiguration().getItemLore();
        return this.buildItemStack(player, this.itemStacks.size() == 1 ? configuration.listedAuctionLore() : configuration.multipleListedAuctionLore());
    }

    @Override
    public ItemStack buildItemStack(Player player, List<String> lore) {

        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();

        var itemStack = getItemStack(player);
        var itemMeta = itemStack.getItemMeta();

        Placeholders placeholders = createPlaceholders(player);

        meta.updateLore(itemMeta, lore.stream().map(placeholders::parse).toList(), LoreType.APPEND);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack getItemStack(Player player) {
        return this.itemStacks.size() == 1 ? this.itemStacks.getFirst().clone() : this.plugin.getConfiguration().getSpecialItems().auctionItem().build(player);
    }

    @Override
    public String createStatus(Player player) {
        var config = this.plugin.getConfiguration().getItemLore();
        return this.sellerUniqueId.equals(player.getUniqueId()) ? config.sellerStatus() : config.buyerStatus();
    }

    @Override
    public int getAmount() {
        return this.itemStacks.size() == 1 ? this.itemStacks.getFirst().getAmount() : 0;
    }

    @Override
    public String getTranslationKey() {
        return this.itemStacks.size() == 1 ? this.itemStacks.getFirst().getType().translationKey() : "";
    }

    @Override
    public String getItemDisplay() {

        StringBuilder builder = new StringBuilder();
        var componentHelper = ComponentMessageHelper.componentMessage;
        var configuration = this.plugin.getConfiguration().getItemDisplay();

        int size = this.itemStacks.size();

        for (int i = 0; i < size; i++) {

            var itemStack = this.itemStacks.get(i);
            if (componentHelper.hasDisplayName(itemStack)) {
                builder.append(configuration.itemNameDisplay().replace("%item-name%", componentHelper.getItemStackDisplayName(itemStack)).replace("%amount%", String.valueOf(itemStack.getAmount())));
            } else {
                builder.append(configuration.langDisplay().replace("%item-translation-key%", itemStack.translationKey()).replace("%amount%", String.valueOf(itemStack.getAmount())));
            }

            if (size > 1) {
                builder.append(i == size - 2 ? configuration.and() : configuration.between());
            }
        }

        return builder.toString();
    }

    @Override
    public String getItemsAsString() {
        return this.itemStacks.stream().map(i -> "x" + i.getAmount() + " " + i.getType().name()).collect(Collectors.joining(", "));
    }
}
