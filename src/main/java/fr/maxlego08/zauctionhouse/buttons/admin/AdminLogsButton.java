package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.button.LoadingButton;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.log.AdminLogItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.LogRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AdminLogsButton extends LoadingButton {

    public AdminLogsButton(Plugin plugin, int loadingSlot) {
        super((AuctionPlugin) plugin, loadingSlot);
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

        if (filtered.isEmpty()) {
            inventoryEngine.buildButton(this.getElseButton(), new Placeholders());
            return;
        }

        var configuration = this.plugin.getConfiguration();
        var dateFormat = configuration.getDateFormat();
        var loreConfig = configuration.getItemLore().adminLogLore();
        var loreMultipleConfig = configuration.getItemLore().adminLogMultipleLore();

        paginate(filtered, inventoryEngine, (slot, log) -> {
            AdminLogItem adminLogItem = createAdminLogItem(log);
            boolean hasMultipleItems = adminLogItem.hasMultipleItems();
            List<String> lore = hasMultipleItems ? loreMultipleConfig : loreConfig;

            ItemStack displayItem = createDisplayItem(adminLogItem, dateFormat, lore);
            var button = inventoryEngine.addItem(slot, displayItem);
            if (button != null) {
                button.setClick(event -> handleClick(player, event.getClick(), adminLogItem));
            }
        });
    }

    @Override
    public int getPaginationSize(@NonNull Player player) {
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

    private void showLoadingItem(InventoryEngine inventoryEngine, Player player) {
        inventoryEngine.addItem(this.loadingSlot, getCustomItemStack(player, false, new Placeholders()));
    }

    private List<LogDTO> applyFilters(PlayerCache cache, List<LogDTO> logs) {
        LogType typeFilter = cache.get(PlayerCacheKey.ADMIN_LOGS_TYPE_FILTER);
        DateFilter dateFilter = cache.get(PlayerCacheKey.ADMIN_LOGS_DATE_FILTER, DateFilter.ALL);

        return logs.stream().filter(log -> typeFilter == null || log.log_type() == typeFilter).filter(log -> dateFilter.matches(log.created_at())).toList();
    }

    /**
     * Creates an AdminLogItem by deserializing the item stacks from the log.
     */
    private AdminLogItem createAdminLogItem(LogDTO log) {
        List<ItemStack> itemStacks = new ArrayList<>();

        if (log.itemstack() != null && !log.itemstack().isEmpty()) {
            try {
                ItemStack decoded = Base64ItemStack.decode(log.itemstack());
                if (decoded != null) {
                    itemStacks.add(decoded);
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to decode itemstack for log " + log.id() + ": " + e.getMessage());
            }
        }

        return new AdminLogItem(log, itemStacks);
    }

    /**
     * Creates the display item for a log entry.
     */
    private ItemStack createDisplayItem(AdminLogItem adminLogItem, SimpleDateFormat dateFormat, List<String> loreConfig) {
        LogDTO log = adminLogItem.log();
        ItemStack itemStack;

        // Use the actual item if available, otherwise use a fallback material
        ItemStack firstItem = adminLogItem.getFirstItem();
        if (firstItem != null) {
            itemStack = firstItem.clone();
        } else {
            itemStack = new ItemStack(getMaterialForLogType(log.log_type()));
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) return itemStack;

        var itemLoreConfig = this.plugin.getConfiguration().getItemLore();
        var placeholders = new Placeholders();
        placeholders.register("type", itemLoreConfig.getLogTypeName(log.log_type()));
        placeholders.register("player", getPlayerName(log.player_unique_id()));
        placeholders.register("target", log.target_unique_id() != null ? getPlayerName(log.target_unique_id()) : "N/A");
        placeholders.register("price", formatPrice(log));
        placeholders.register("date", dateFormat.format(log.created_at()));
        placeholders.register("item_id", String.valueOf(log.item_id()));

        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();
        meta.updateLore(itemMeta, loreConfig.stream().map(placeholders::parse).toList(), LoreType.APPEND);

        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    /**
     * Handles click events on log items.
     */
    private void handleClick(Player player, ClickType clickType, AdminLogItem adminLogItem) {
        var cache = this.plugin.getAuctionManager().getCache(player);

        if (clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT) {
            // Left click - retrieve the item
            giveItemsToPlayer(player, adminLogItem);
        } else if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            // Right click - view all items if multiple
            if (adminLogItem.hasMultipleItems()) {
                cache.set(PlayerCacheKey.ADMIN_LOG_SELECTED, adminLogItem);
                cache.set(PlayerCacheKey.CURRENT_PAGE, this.plugin.getInventoriesLoader().getInventoryManager().getPage(player));
                this.plugin.getInventoriesLoader().openInventory(player, Inventories.PURCHASE_INVENTORY_CONFIRM);
            } else {
                // If single item, just retrieve it
                giveItemsToPlayer(player, adminLogItem);
            }
        }
    }

    /**
     * Gives the items from a log entry to the player.
     */
    private void giveItemsToPlayer(Player player, AdminLogItem adminLogItem) {
        List<ItemStack> items = adminLogItem.itemStacks();
        if (items == null || items.isEmpty()) {
            this.plugin.getAuctionManager().message(player, Message.ADMIN_NO_ITEM_TO_RETRIEVE);
            return;
        }

        for (ItemStack itemStack : items) {
            if (itemStack != null) {
                player.getInventory().addItem(itemStack.clone()).forEach((slot, dropItemStack) -> player.getWorld().dropItem(player.getLocation(), dropItemStack));
            }
        }

        this.plugin.getAuctionManager().message(player, Message.ADMIN_ITEM_RETRIEVED);
    }

    private String formatPrice(LogDTO log) {
        String price = log.price() != null ? log.price().toPlainString() : "0";
        if (log.economy_name() != null && !log.economy_name().isEmpty()) {
            return price + " " + log.economy_name();
        }
        return price;
    }

    private Material getMaterialForLogType(LogType logType) {
        return switch (logType) {
            case SALE -> Material.GOLD_INGOT;
            case PURCHASE -> Material.EMERALD;
            case REMOVE_LISTED -> Material.BARRIER;
            case REMOVE_SELLING -> Material.CHEST;
            case REMOVE_EXPIRED -> Material.CLOCK;
            case REMOVE_PURCHASED -> Material.HOPPER;
        };
    }

    private String getPlayerName(UUID uniqueId) {
        if (uniqueId == null) return "N/A";
        var offlinePlayer = this.plugin.getServer().getOfflinePlayer(uniqueId);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : uniqueId.toString().substring(0, 8);
    }
}
