package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Button that allows players to claim their pending money from transactions.
 */
public class ClaimButton extends Button {

    private final AuctionPlugin plugin;

    public ClaimButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public void onRender(Player player, InventoryEngine inventoryEngine) {
        var cache = this.plugin.getAuctionManager().getCache(player);

        // Check if we already have cached data
        if (cache.has(PlayerCacheKey.PENDING_MONEY_DATA)) {
            return;
        }

        // Load pending money asynchronously
        Boolean isLoading = cache.get(PlayerCacheKey.PENDING_MONEY_LOADING, false);
        if (isLoading) {
            return;
        }

        cache.set(PlayerCacheKey.PENDING_MONEY_LOADING, true);

        this.plugin.getAuctionManager().getClaimService().getPendingMoneyByEconomy(player.getUniqueId())
                .thenAccept(pendingByEconomy -> {
                    cache.set(PlayerCacheKey.PENDING_MONEY_DATA, pendingByEconomy);
                    cache.set(PlayerCacheKey.PENDING_MONEY_LOADING, false);

                    // Update the inventory on the main thread
                    this.plugin.getScheduler().runNextTick(task -> {
                        if (player.isOnline()) {
                            this.plugin.getAuctionManager().updateInventory(player);
                        }
                    });
                });
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int slot, Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        Map<String, BigDecimal> pendingData = cache.get(PlayerCacheKey.PENDING_MONEY_DATA);

        if (pendingData == null || pendingData.isEmpty()) {
            return;
        }

        BigDecimal total = pendingData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Clear cached data before claiming
        cache.remove(PlayerCacheKey.PENDING_MONEY_DATA, PlayerCacheKey.PENDING_MONEY_LOADING);

        // Claim the money
        this.plugin.getAuctionManager().getClaimService().claimMoney(player).thenRun(() -> {
            this.plugin.getScheduler().runNextTick(task -> {
                if (player.isOnline()) {
                    this.plugin.getAuctionManager().updateInventory(player);
                }
            });
        });
    }
}
