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
import java.util.ArrayList;
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
        return this.performanceDebug.measureWithContext("item.BuildItemStack", () -> {
            var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();

            var itemStack = getItemStack(player);
            var itemMeta = itemStack.getItemMeta();

            Placeholders placeholders = createPlaceholders(player);
            placeholders.register("item_count", this.itemStacks.stream().map(ItemStack::getAmount).reduce(0, Integer::sum).toString());

            meta.updateLore(itemMeta, lore.stream().map(placeholders::parse).toList(), LoreType.APPEND);
            itemStack.setItemMeta(itemMeta);
            return itemStack;
        }, () -> "for=" + player.getName() + ", itemId=" + this.id);
    }

    private ItemStack getItemStack(Player player) {
        if (this.itemStacks.size() == 1) {
            var itemStack = this.itemStacks.getFirst().clone();
            if (this.plugin.getConfiguration().getItemLore().forceAmountOne()) {
                itemStack.setAmount(1);
            }
            return itemStack;
        }
        return this.plugin.getConfiguration().getSpecialItems().auctionItem().build(player).clone();
    }

    @Override
    public String createStatus(Player player) {
        var config = this.plugin.getConfiguration().getItemLore();
        var isSeller = this.sellerUniqueId.equals(player.getUniqueId());
        return this.itemStacks.size() == 1 ? (isSeller ? config.sellerStatus() : config.buyerStatus()) : (isSeller ? config.rightSellerStatus() : config.rightBuyerStatus());
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

        var currentItemStacks = this.itemStacks;
        if (configuration.mergeSimilar()) {
            currentItemStacks = new ArrayList<>();
            for (ItemStack itemStack : this.itemStacks) {
                boolean canAdd = true;
                for (ItemStack currentItemStack : currentItemStacks) {
                    if (currentItemStack.isSimilar(itemStack)) {
                        currentItemStack.setAmount(currentItemStack.getAmount() + itemStack.getAmount());
                        canAdd = false;
                        break;
                    }
                }

                if (canAdd) {
                    currentItemStacks.add(itemStack.clone());
                }
            }
        }

        int size = currentItemStacks.size();

        for (int i = 0; i < size; i++) {

            var itemStack = currentItemStacks.get(i);
            if (componentHelper.hasDisplayName(itemStack)) {
                builder.append(configuration.itemNameDisplay().replace("%item-name%", componentHelper.getItemStackDisplayName(itemStack)).replace("%amount%", String.valueOf(itemStack.getAmount())));
            } else {
                builder.append(configuration.langDisplay().replace("%item-translation-key%", itemStack.translationKey()).replace("%amount%", String.valueOf(itemStack.getAmount())));
            }

            if (size > 1 && i < size - 1) {
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
