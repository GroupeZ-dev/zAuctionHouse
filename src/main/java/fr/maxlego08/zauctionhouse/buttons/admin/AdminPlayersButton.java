package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.storage.dto.AdminPlayerDTO;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.PlayerRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AdminPlayersButton extends PaginateButton {
    private final AuctionPlugin plugin;
    public AdminPlayersButton(Plugin plugin) { this.plugin = (AuctionPlugin) plugin; }

    private List<AdminPlayerDTO> sorted(Player player) {
        var cache = plugin.getAuctionManager().getCache(player);
        List<AdminPlayerDTO> data = cache.get(PlayerCacheKey.ADMIN_PLAYERS_DATA);
        int sort = cache.get(PlayerCacheKey.ADMIN_PLAYERS_SORT);
        Comparator<AdminPlayerDTO> comparator = switch (sort) {
            case 1 -> Comparator.comparingLong(AdminPlayerDTO::listed).reversed();
            case 2 -> Comparator.comparingLong(AdminPlayerDTO::sold).reversed();
            case 3 -> Comparator.comparingLong(AdminPlayerDTO::purchased).reversed();
            default -> Comparator.comparing(AdminPlayerDTO::name, String.CASE_INSENSITIVE_ORDER);
        };
        return data.stream().sorted(comparator.thenComparing(AdminPlayerDTO::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AdminPlayerDTO::uniqueId)).toList();
    }

    @Override
    public void onRender(Player player, InventoryEngine engine) {
        if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS.asPermission())) return;
        var cache = plugin.getAuctionManager().getCache(player);
        if (!cache.has(PlayerCacheKey.ADMIN_PLAYERS_DATA)) {
            if (cache.get(PlayerCacheKey.ADMIN_PLAYERS_LOADING)) return;
            cache.set(PlayerCacheKey.ADMIN_PLAYERS_LOADING, true);
            CompletableFuture.supplyAsync(this::loadPlayers, plugin.getExecutorService()).whenComplete((data, error) ->
                plugin.getScheduler().runAtEntity(player, task -> {
                    cache.set(PlayerCacheKey.ADMIN_PLAYERS_LOADING, false);
                    if (error != null) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot load admin players", error);
                        return;
                    }
                    cache.set(PlayerCacheKey.ADMIN_PLAYERS_DATA, data);
                    if (player.isOnline() && !engine.isClose()) plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
                }));
            return;
        }
        var data = sorted(player);
        if (data.isEmpty()) { engine.buildButton(getElseButton(), new Placeholders()); return; }
        paginate(data, engine, (slot, entry) -> {
            var placeholders = new Placeholders();
            placeholders.register("target", entry.name());
            placeholders.register("listed", String.valueOf(entry.listed()));
            placeholders.register("sold", String.valueOf(entry.sold()));
            placeholders.register("purchased", String.valueOf(entry.purchased()));
            var button = engine.addItem(slot, getCustomItemStack(player, false, placeholders));
            if (button != null) button.setClick(event -> {
                if (!player.hasPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS.asPermission())) return;
                cache.remove(PlayerCacheKey.ADMIN_LOGS_DATA, PlayerCacheKey.ADMIN_TRANSACTIONS_DATA,
                        PlayerCacheKey.ADMIN_LOGS_LOADING, PlayerCacheKey.ADMIN_TRANSACTIONS_LOADING);
                cache.set(PlayerCacheKey.ADMIN_TARGET_UUID, entry.uniqueId());
                cache.set(PlayerCacheKey.ADMIN_TARGET_NAME, entry.name());
                plugin.getInventoriesLoader().openInventory(player, Inventories.ADMIN_HISTORY_MAIN);
            });
        });
    }

    private List<AdminPlayerDTO> loadPlayers() {
        return plugin.getStorageManager().with(PlayerRepository.class).selectAdminPlayers();
    }

    @Override
    public int getPaginationSize(@NonNull Player player) { return sorted(player).size(); }
}
