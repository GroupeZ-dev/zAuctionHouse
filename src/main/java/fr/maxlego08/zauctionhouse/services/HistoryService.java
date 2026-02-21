package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.history.ItemLog;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionHistoryService;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.LogRepository;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HistoryService extends AuctionService implements AuctionHistoryService {

    private final AuctionPlugin plugin;

    public HistoryService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<List<ItemLog>> getSalesHistory(UUID playerUniqueId) {

        var storageManager = this.plugin.getStorageManager();
        var economyManager = this.plugin.getEconomyManager();

        return CompletableFuture.supplyAsync(() -> {

            var logs = storageManager.selectSalesHistory(playerUniqueId);
            var items = storageManager.selectItems(logs.stream().map(LogDTO::item_id).toList());

            List<ItemLog> itemLogs = new ArrayList<>();
            logs.forEach(dto -> {
                var optional = items.stream().filter(e -> e.getId() == dto.item_id()).findFirst();
                optional.ifPresentOrElse(item -> itemLogs.add(new ItemLog(dto, item)), () -> plugin.getLogger().warning("Item not found for log ID: " + dto.id()));
            });

            return itemLogs;
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<List<LogDTO>> getUnreadSales(UUID playerUniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            var repository = this.plugin.getStorageManager().with(LogRepository.class);
            return repository.selectUnreadSales(playerUniqueId);
        }, this.plugin.getExecutorService());
    }

    @Override
    public CompletableFuture<Void> markSalesAsRead(List<Integer> logIds) {
        return CompletableFuture.runAsync(() -> {
            var repository = this.plugin.getStorageManager().with(LogRepository.class);
            repository.markAsRead(logIds);
        }, this.plugin.getExecutorService());
    }

    @Override
    public void handlePlayerJoin(Player player) {
        var config = this.plugin.getConfiguration().getSalesNotificationConfiguration();

        if (!config.enabled()) {
            return;
        }

        getUnreadSales(player.getUniqueId()).thenAccept(unreadSales -> {
            if (unreadSales.isEmpty()) {
                return;
            }

            // Calculate total earned
            BigDecimal totalEarned = unreadSales.stream().map(LogDTO::price).reduce(BigDecimal.ZERO, BigDecimal::add);

            int salesCount = unreadSales.size();

            // Mark as read
            var logIds = unreadSales.stream().map(LogDTO::id).toList();
            markSalesAsRead(logIds);

            // Send notification after delay
            long delay = config.delayTicks();
            Runnable notifyTask = () -> {
                if (player.isOnline()) {
                    // Format the total using the first economy found, or just the number
                    String formattedTotal = formatTotalEarned(unreadSales, totalEarned);

                    message(this.plugin, player, Message.SALES_NOTIFICATION, "%count%", String.valueOf(salesCount), "%total%", formattedTotal);
                }
            };

            if (delay <= 0) {
                this.plugin.getScheduler().runNextTick(task -> notifyTask.run());
            } else {
                this.plugin.getScheduler().runLater(task -> notifyTask.run(), delay);
            }
        });
    }

    @Override
    public void openHistoryInventory(Player player) {
        openHistoryInventory(player, 1);
    }

    @Override
    public void openHistoryInventory(Player player, int page) {
        var cache = this.plugin.getAuctionManager().getCache(player);

        // Check if history is already loaded
        if (cache.has(PlayerCacheKey.HISTORY_DATA)) {
            this.plugin.getInventoriesLoader().openInventory(player, Inventories.HISTORY, page);
        } else {
            // Load history asynchronously
            getSalesHistory(player.getUniqueId()).thenAccept(history -> {
                cache.set(PlayerCacheKey.HISTORY_DATA, history);
                this.plugin.getScheduler().runNextTick(task -> {
                    if (player.isOnline()) {
                        this.plugin.getInventoriesLoader().openInventory(player, Inventories.HISTORY, page);
                    }
                });
            });
        }
    }

    private String formatTotalEarned(List<LogDTO> sales, BigDecimal total) {
        var economyManager = this.plugin.getEconomyManager();

        // Try to get the economy from the first sale
        if (!sales.isEmpty()) {
            String economyName = sales.getFirst().economy_name();
            if (economyName != null) {
                var optionalEconomy = economyManager.getEconomy(economyName);
                if (optionalEconomy.isPresent()) {
                    return economyManager.format(optionalEconomy.get(), total);
                }
            }
        }

        return total.toString();
    }
}
