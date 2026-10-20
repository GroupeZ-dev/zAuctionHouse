package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.command.commands.CommandAuctionSell;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Button to confirm the sale of selected items.
 * Shows a summary of the price and economy, and processes the sale when clicked.
 * <p>
 * With an {@code economy} option the button always sells with that economy, whatever economy is
 * selected: one button per economy lists the item in a single click.
 */
public class SellConfirmButton extends Button {

    private final AuctionPlugin plugin;
    // null = economie selectionnee dans l'inventaire (SELL_ECONOMY)
    private final String economyName;

    public SellConfirmButton(Plugin plugin) {
        this(plugin, null);
    }

    public SellConfirmButton(Plugin plugin, String economyName) {
        this.plugin = (AuctionPlugin) plugin;
        this.economyName = economyName;
    }

    /**
     * @return l'economie de la vente : celle imposee par le bouton, sinon celle selectionnee,
     * sinon l'economie par defaut. null si l'economie imposee n'existe pas (ou plus).
     */
    private AuctionEconomy resolveEconomy(Player player, AuctionEconomy defaultEconomy) {
        if (this.economyName != null) {
            return this.plugin.getEconomyManager().getEconomy(this.economyName).orElse(null);
        }
        if (defaultEconomy == null) return null;
        return this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.SELL_ECONOMY, defaultEconomy);
    }

    @Override
    public void onInventoryOpen(@NotNull Player player, @NotNull InventoryEngine inventory, @NotNull Placeholders placeholders) {
        createPlaceholders(placeholders, player);
        super.onInventoryOpen(player, inventory, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);

        Map<Integer, ItemStack> sellItems = cache.get(PlayerCacheKey.SELL_ITEMS);
        if (sellItems == null || sellItems.isEmpty()) {
            // If no items selected, use main hand item
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            if (mainHandItem.getType().isAir()) {
                this.plugin.getAuctionManager().message(player, Message.SELL_INVENTORY_EMPTY);
                return;
            }
            var itemToSell = mainHandItem.clone();
            int sellAmount = cache.get(PlayerCacheKey.SELL_AMOUNT, 0);
            if (sellAmount > 0) itemToSell.setAmount(Math.min(sellAmount, mainHandItem.getAmount()));

            sellItems = new HashMap<>();
            sellItems.put(AuctionSellService.MAIN_HAND_SLOT, itemToSell);
        }

        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy defaultEconomy = this.plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION);
        if (defaultEconomy == null) {
            this.plugin.getAuctionManager().message(player, Message.SELL_ERROR_DEFAULT_ECONOMY);
            return;
        }
        AuctionEconomy economy = resolveEconomy(player, defaultEconomy);
        if (economy == null) {
            this.plugin.getAuctionManager().message(player, Message.SELL_ERROR_ECONOMY, "%name%", this.economyName);
            return;
        }
        long expiredAt = cache.get(PlayerCacheKey.SELL_EXPIRED_AT, 0L);

        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);
        cache.remove(PlayerCacheKey.SELL_ITEMS);

        // Close inventory before selling - items will be removed by the service after verification
        player.closeInventory();

        // The service will verify items are still in their slots and remove them after async tax check
        manager.getSellService().sellAuctionItems(player, price, expiredAt, sellItems, economy).thenAccept(result -> {
            if (result != null && result.success()) applySellCooldown(player);
        });
    }

    /**
     * L'inventaire de vente n'est ouvert que par /ah sell : c'est ici, une fois la vente
     * reussie, que le cooldown de la commande demarre (voir {@link CommandAuctionSell}).
     */
    private void applySellCooldown(Player player) {
        var commandManager = this.plugin.getCommandManager();
        commandManager.getCommands().stream()
                .filter(command -> command instanceof CommandAuctionSell)
                .findFirst()
                .ifPresent(command -> commandManager.applyCooldown(player, command));
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        createPlaceholders(placeholders, player);
        return getItemStack().build(player, false, placeholders);
    }

    private void createPlaceholders(Placeholders placeholders, Player player) {
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        var economyManager = this.plugin.getEconomyManager();

        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy defaultEconomy = economyManager.getDefaultEconomy(ItemType.AUCTION);
        AuctionEconomy economy = resolveEconomy(player, defaultEconomy);

        Map<Integer, ItemStack> sellItems = cache.get(PlayerCacheKey.SELL_ITEMS);
        int itemCount = 0;
        int totalAmount = 0;

        if (sellItems != null && !sellItems.isEmpty()) {
            itemCount = sellItems.size();
            totalAmount = sellItems.values().stream()
                    .filter(item -> item != null && !item.getType().isAir())
                    .mapToInt(ItemStack::getAmount)
                    .sum();
        }

        if (economy != null) {
            placeholders.register("price", economyManager.format(economy, price));
            placeholders.register("economy", economy.getDisplayName());
            placeholders.register("economy_name", economy.getName());
        } else {
            placeholders.register("price", price.toPlainString());
            placeholders.register("economy", "N/A");
            placeholders.register("economy_name", "N/A");
        }
        placeholders.register("item_count", String.valueOf(itemCount));
        placeholders.register("total_amount", String.valueOf(totalAmount));
    }

}
