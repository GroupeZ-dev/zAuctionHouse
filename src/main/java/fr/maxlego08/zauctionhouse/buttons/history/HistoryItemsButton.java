package fr.maxlego08.zauctionhouse.buttons.history;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.button.LoadingButton;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.history.ItemLog;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Button that displays the player's sales history with pagination.
 */
public class HistoryItemsButton extends LoadingButton {

    public HistoryItemsButton(Plugin plugin, int loadingSlot) {
        super((AuctionPlugin) plugin, loadingSlot);
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);

        if (!cache.has(PlayerCacheKey.HISTORY_DATA)) {

            inventoryEngine.addItem(this.loadingSlot, getCustomItemStack(player, new Placeholders()));

            Boolean isLoading = cache.get(PlayerCacheKey.HISTORY_LOADING, false);
            if (!isLoading) {
                cache.set(PlayerCacheKey.HISTORY_LOADING, true);
                manager.getHistoryService().getSalesHistory(player.getUniqueId()).thenAccept(history -> {
                    cache.set(PlayerCacheKey.HISTORY_DATA, history);
                    cache.set(PlayerCacheKey.HISTORY_LOADING, false);
                    this.plugin.getScheduler().runNextTick(task -> {
                        if (player.isOnline()) {
                            manager.updateInventory(player);
                        }
                    });
                });
            }
            return;
        }

        List<ItemLog> history = cache.get(PlayerCacheKey.HISTORY_DATA);
        if (history == null || history.isEmpty()) {
            return;
        }

        var configuration = this.plugin.getConfiguration();
        var dateFormat = configuration.getDateFormat();
        var economyManager = this.plugin.getEconomyManager();
        var loreConfig = configuration.getItemLore().historyLore();

        paginate(history, inventoryEngine, (slot, log) -> {
            ItemStack displayItem = createDisplayItem(log, dateFormat, economyManager, loreConfig);
            inventoryEngine.addItem(slot, displayItem);
        });
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        List<LogDTO> history = cache.get(PlayerCacheKey.HISTORY_DATA);
        return history != null ? history.size() : 0;
    }

    private ItemStack createDisplayItem(ItemLog itemLog, SimpleDateFormat dateFormat, EconomyManager economyManager, List<String> loreConfig) {

        ItemStack itemStack;

        if (itemLog.item() instanceof AuctionItem auctionItem) {
            itemStack = auctionItem.getItemStack();
        } else {
            itemStack = new ItemStack(Material.PAPER);
        }

        itemStack = itemStack.clone();
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) return itemStack;

        var placeholders = new Placeholders();
        placeholders.register("buyer", itemLog.item().getBuyerName());
        placeholders.register("price", itemLog.item().getFormattedPrice());
        placeholders.register("date", dateFormat.format(itemLog.log().created_at()));

        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();
        meta.updateLore(itemMeta, loreConfig.stream().map(placeholders::parse).toList(), LoreType.APPEND);

        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }
}
