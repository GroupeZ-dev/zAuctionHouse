package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.LogRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AdminLogsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public AdminLogsButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        var target = this.getTarget(player);
        if (target.isEmpty()) {
            this.plugin.getAuctionManager().message(player, Message.ADMIN_TARGET_REQUIRED);
            return;
        }

        var cache = this.plugin.getAuctionManager().getCache(player);
        Boolean isLoading = cache.get(PlayerCacheKey.ADMIN_LOGS_LOADING, false);

        if (isLoading) {
            showLoadingItem(inventoryEngine, player);
            return;
        }

        List<LogDTO> logs = cache.get(PlayerCacheKey.ADMIN_LOGS_DATA);
        if (logs == null || logs.isEmpty()) {
            if (!cache.has(PlayerCacheKey.ADMIN_LOGS_DATA)) {
                loadLogsAsync(player, target.get(), inventoryEngine);
                return;
            }
        }

        if (logs == null) logs = new ArrayList<>();

        List<LogDTO> filtered = applyFilters(cache, logs);

        paginate(filtered, inventoryEngine, (slot, log) -> {
            ItemStack itemStack = buildLogItemStack(log);
            inventoryEngine.addItem(slot, itemStack);
        });
    }

    @Override
    public int getPaginationSize(Player player) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        List<LogDTO> logs = cache.get(PlayerCacheKey.ADMIN_LOGS_DATA);
        if (logs == null) return 0;
        return applyFilters(cache, logs).size();
    }

    private Optional<UUID> getTarget(Player player) {
        return Optional.ofNullable(this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ADMIN_TARGET_UUID));
    }

    private void loadLogsAsync(Player player, UUID targetUniqueId, InventoryEngine engine) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        cache.set(PlayerCacheKey.ADMIN_LOGS_LOADING, true);

        showLoadingItem(engine, player);

        CompletableFuture.supplyAsync(() -> {
            return this.plugin.getStorageManager().with(LogRepository.class).selectByPlayerOrTarget(targetUniqueId);
        }, this.plugin.getExecutorService()).thenAccept(logs -> {
            cache.set(PlayerCacheKey.ADMIN_LOGS_DATA, logs);
            cache.set(PlayerCacheKey.ADMIN_LOGS_LOADING, false);

            this.plugin.getScheduler().runNextTick(w -> {
                this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
            });
        }).exceptionally(throwable -> {
            cache.set(PlayerCacheKey.ADMIN_LOGS_LOADING, false);
            this.plugin.getLogger().severe("Failed to load logs: " + throwable.getMessage());
            return null;
        });
    }

    private void showLoadingItem(InventoryEngine engine, Player player) {
        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();
        ItemStack loadingItem = new ItemStack(Material.HOPPER);
        var itemMeta = loadingItem.getItemMeta();
        meta.updateDisplayName(itemMeta, "#FFD700Loading logs...", null);
        loadingItem.setItemMeta(itemMeta);

        for (Integer slot : getSlots()) {
            engine.addItem(slot, loadingItem);
        }
    }

    private List<LogDTO> applyFilters(PlayerCache cache, List<LogDTO> logs) {
        LogType typeFilter = cache.get(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER);
        DateFilter dateFilter = cache.get(PlayerCacheKey.ADMIN_LOGS_DATE_FILTER, DateFilter.ALL);

        return logs.stream()
                .filter(log -> typeFilter == null || log.log_type() == typeFilter)
                .filter(log -> dateFilter.matches(log.created_at()))
                .toList();
    }

    private ItemStack buildLogItemStack(LogDTO log) {
        Material material = getMaterialForLogType(log.log_type().name());
        var configuration = this.plugin.getConfiguration();
        var dateFormat = configuration.getDateFormat();

        ItemStack itemStack = new ItemStack(material);
        var itemMeta = itemStack.getItemMeta();

        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();

        meta.updateDisplayName(itemMeta, "#2CCED2<bold>" + log.log_type().name(), null);

        List<String> lore = new ArrayList<>();
        lore.add("#8c8c8c• #92ffffType: #2CCED2" + log.log_type().name());
        lore.add("#8c8c8c• #92ffffItem ID: #2CCED2" + log.item_id());
        lore.add("#8c8c8c• #92ffffPlayer: #2CCED2" + getPlayerName(log.player_unique_id()));
        if (log.target_unique_id() != null) {
            lore.add("#8c8c8c• #92ffffTarget: #2CCED2" + getPlayerName(log.target_unique_id()));
        }
        lore.add("#8c8c8c• #92ffffPrice: #2CCED2" + log.price() + (log.economy_name() != null ? " " + log.economy_name() : ""));
        lore.add("#8c8c8c• #92ffffDate: #2CCED2" + dateFormat.format(log.created_at()));

        meta.updateLore(itemMeta, lore, LoreType.REPLACE);
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    private Material getMaterialForLogType(String logType) {
        return switch (logType) {
            case "SALE" -> Material.GOLD_INGOT;
            case "PURCHASE" -> Material.EMERALD;
            case "REMOVE_LISTED" -> Material.BARRIER;
            case "REMOVE_OWNED" -> Material.CHEST;
            case "REMOVE_EXPIRED" -> Material.CLOCK;
            case "REMOVE_PURCHASED" -> Material.HOPPER;
            default -> Material.PAPER;
        };
    }

    private String getPlayerName(UUID uniqueId) {
        if (uniqueId == null) return "N/A";
        var offlinePlayer = this.plugin.getServer().getOfflinePlayer(uniqueId);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : uniqueId.toString().substring(0, 8);
    }
}
