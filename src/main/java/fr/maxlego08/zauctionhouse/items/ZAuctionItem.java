package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemPlaceholder;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        boolean single = this.itemStacks.size() == 1;
        return this.buildItemStack(player,
                single ? configuration.listedAuctionLore() : configuration.multipleListedAuctionLore(),
                single ? configuration.listedAuctionPlaceholders() : configuration.multipleListedAuctionPlaceholders());
    }

    @Override
    public ItemStack buildItemStack(Player player, List<String> lore) {
        return buildItemStack(player, lore, ItemPlaceholder.detect(lore));
    }

    @Override
    public ItemStack buildItemStack(Player player, List<String> lore, Set<ItemPlaceholder> needed) {
        return this.performanceDebug.measureWithContext("item.BuildItemStack", () -> {
            var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();

            var itemStack = getItemStack(player);
            var itemMeta = itemStack.getItemMeta();

            Placeholders placeholders = createPlaceholders(player, needed);
            if (needed.contains(ItemPlaceholder.ITEM_COUNT)) {
                // Ceinture defensive (C-038) : depuis la quarantaine du chargement, aucun
                // ZAuctionItem publie ne porte de ItemStack null, mais un lot construit par
                // une autre voie (addon, migration) ne doit pas faire partir l'affichage en NPE.
                placeholders.register("item_count", this.itemStacks.stream().filter(Objects::nonNull).map(ItemStack::getAmount).reduce(0, Integer::sum).toString());
            }

            meta.updateLore(itemMeta, lore.stream().map(placeholders::parse).toList(), LoreType.APPEND);
            itemStack.setItemMeta(itemMeta);
            return itemStack;
        }, () -> "for=" + player.getName() + ", itemId=" + this.id);
    }

    private ItemStack getItemStack(Player player) {
        if (this.itemStacks.size() == 1) {
            var first = this.itemStacks.getFirst();
            if (first == null) {
                this.plugin.getLogger().warning("Item #" + this.id + " has a null ItemStack, the item data may be corrupted.");
                return new ItemStack(org.bukkit.Material.BARRIER);
            }
            var itemStack = first.clone();
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
        if (this.itemStacks.size() == 1) {
            var first = this.itemStacks.getFirst();
            return first != null ? first.getAmount() : 0;
        }
        return 0;
    }

    @Override
    public String getTranslationKey() {
        if (this.itemStacks.size() == 1) {
            var first = this.itemStacks.getFirst();
            return first != null ? first.getType().translationKey() : "";
        }
        return "";
    }

    @Override
    public String getItemDisplay() {

        StringBuilder builder = new StringBuilder();
        var componentHelper = ComponentMessageHelper.componentMessage;
        var configuration = this.plugin.getConfiguration().getItemDisplay();

        // LES DEUX branches parcouraient itemStacks sans garde : la fusion (isSimilar) et le
        // rendu (hasDisplayName / getAmount). Le filtre est pose UNE fois, en amont des deux.
        List<ItemStack> currentItemStacks = this.itemStacks.stream().filter(Objects::nonNull).toList();
        if (configuration.mergeSimilar()) {
            List<ItemStack> merged = new ArrayList<>();
            for (ItemStack itemStack : currentItemStacks) {
                boolean canAdd = true;
                for (ItemStack currentItemStack : merged) {
                    if (currentItemStack.isSimilar(itemStack)) {
                        currentItemStack.setAmount(currentItemStack.getAmount() + itemStack.getAmount());
                        canAdd = false;
                        break;
                    }
                }

                if (canAdd) {
                    merged.add(itemStack.clone());
                }
            }
            currentItemStacks = merged;
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
        return this.itemStacks.stream().filter(Objects::nonNull).map(i -> "x" + i.getAmount() + " " + i.getType().name()).collect(Collectors.joining(", "));
    }
}
