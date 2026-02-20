package fr.maxlego08.zauctionhouse.buttons.history;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.PlayerRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Button that displays the player's sales history with pagination.
 */
public class HistoryItemsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public HistoryItemsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);

        // Check if we need to load history
        if (!cache.has(PlayerCacheKey.HISTORY_DATA)) {
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

        List<LogDTO> history = cache.get(PlayerCacheKey.HISTORY_DATA);
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
    public int getPaginationSize(Player player) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        List<LogDTO> history = cache.get(PlayerCacheKey.HISTORY_DATA);
        return history != null ? history.size() : 0;
    }

    private ItemStack createDisplayItem(LogDTO log, SimpleDateFormat dateFormat, EconomyManager economyManager, List<String> loreConfig) {
        ItemStack item;

        // Try to decode the itemstack from the log
        if (log.itemstack() != null && !log.itemstack().isEmpty()) {
            // Handle multiple items separated by ;
            String[] itemstacks = log.itemstack().split(";");
            item = Base64ItemStack.decode(itemstacks[0]);
            if (item == null) {
                item = new ItemStack(Material.PAPER);
            }
        } else {
            item = new ItemStack(Material.PAPER);
        }

        item = item.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Get buyer name
        String buyerName = getBuyerName(log.player_unique_id());

        // Format price
        String formattedPrice = formatPrice(log, economyManager);

        // Format date
        String formattedDate = dateFormat.format(log.created_at());

        // Build lore
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.getLore());
        }

        for (String line : loreConfig) {
            lore.add(line
                    .replace("%buyer%", buyerName)
                    .replace("%price%", formattedPrice)
                    .replace("%date%", formattedDate)
            );
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private String getBuyerName(java.util.UUID buyerUniqueId) {
        if (buyerUniqueId == null) {
            return "Unknown";
        }

        var playerRepo = this.plugin.getStorageManager().with(PlayerRepository.class);
        String name = playerRepo.select(buyerUniqueId);
        return name != null ? name : "Unknown";
    }

    private String formatPrice(LogDTO log, EconomyManager economyManager) {
        if (log.economy_name() != null) {
            var optionalEconomy = economyManager.getEconomy(log.economy_name());
            if (optionalEconomy.isPresent()) {
                return economyManager.format(optionalEconomy.get(), log.price());
            }
        }
        return log.price().toString();
    }
}
