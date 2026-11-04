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
        return buildAuctionItemStack(player, false);
    }

    @Override
    public ItemStack buildListedItemStack(Player player) {
        return buildAuctionItemStack(player, true);
    }

    /**
     * @param listing {@code true} uniquement depuis la liste de l'HDV : c'est le seul endroit ou
     *                le clic d'apercu du contenu est gere, l'indication n'a de sens que la.
     */
    private ItemStack buildAuctionItemStack(Player player, boolean listing) {
        var configuration = this.plugin.getConfiguration().getItemLore();
        boolean single = this.itemStacks.size() == 1;
        var lore = single ? configuration.listedAuctionLore() : configuration.multipleListedAuctionLore();
        var needed = single ? configuration.listedAuctionPlaceholders() : configuration.multipleListedAuctionPlaceholders();
        String hint = listing && needed.contains(ItemPlaceholder.CONTAINER_PREVIEW) && canPreviewContainer() ? configuration.containerPreview() : null;
        return this.buildItemStack(player, lore, needed, hint);
    }

    /**
     * Meme condition que {@code ListedItemsButton#openContainerPreview} : apercu actif et au
     * moins un conteneur reconnu par un {@code ItemContentProvider}. Volontairement non mis en
     * cache : les providers peuvent etre enregistres apres le chargement des annonces.
     */
    private boolean canPreviewContainer() {
        if (!this.plugin.getConfiguration().getActions().containerPreview().enable()) return false;
        var contentManager = this.plugin.getItemContentManager();
        return contentManager != null && contentManager.containsContainer(this.itemStacks);
    }

    @Override
    public ItemStack buildItemStack(Player player, List<String> lore) {
        return buildItemStack(player, lore, ItemPlaceholder.detect(lore));
    }

    @Override
    public ItemStack buildItemStack(Player player, List<String> lore, Set<ItemPlaceholder> needed) {
        return buildItemStack(player, lore, needed, null);
    }

    /**
     * Remplace {@code %container-preview%} par l'indication, ou le retire quand elle ne
     * s'applique pas : une ligne ne contenant que ce placeholder est supprimee pour ne pas
     * laisser de ligne vide dans le lore.
     *
     * @param lore lignes du template
     * @param hint indication a afficher, {@code null} ou vide si l'annonce n'est pas previsualisable
     * @return les lignes a afficher
     */
    static List<String> applyContainerPreview(List<String> lore, String hint) {
        String pattern = ItemPlaceholder.CONTAINER_PREVIEW.getPattern();
        boolean show = hint != null && !hint.isEmpty();
        List<String> result = new ArrayList<>(lore.size());
        for (String line : lore) {
            if (!line.contains(pattern)) {
                result.add(line);
            } else if (show) {
                result.add(line.replace(pattern, hint));
            } else if (!line.trim().equals(pattern)) {
                result.add(line.replace(pattern, ""));
            }
        }
        return result;
    }

    private ItemStack buildItemStack(Player player, List<String> template, Set<ItemPlaceholder> needed, String containerPreviewHint) {
        var lore = needed.contains(ItemPlaceholder.CONTAINER_PREVIEW) ? applyContainerPreview(template, containerPreviewHint) : template;
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
