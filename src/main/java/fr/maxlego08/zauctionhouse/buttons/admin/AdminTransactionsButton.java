package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.LoreType;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.storage.repository.repositeries.TransactionRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AdminTransactionsButton extends PaginateButton {

    private final AuctionPlugin plugin;

    public AdminTransactionsButton(Plugin plugin) {
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
        Boolean isLoading = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_LOADING, false);

        if (isLoading) {
            showLoadingItem(inventoryEngine, player);
            return;
        }

        List<TransactionDTO> transactions = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_DATA);
        if (transactions == null || transactions.isEmpty()) {
            if (!cache.has(PlayerCacheKey.ADMIN_TRANSACTIONS_DATA)) {
                loadTransactionsAsync(player, target.get(), inventoryEngine);
                return;
            }
        }

        if (transactions == null) transactions = new ArrayList<>();

        List<TransactionDTO> filtered = applyFilters(cache, transactions);

        paginate(filtered, inventoryEngine, (slot, transaction) -> {
            ItemStack itemStack = buildTransactionItemStack(transaction);
            inventoryEngine.addItem(slot, itemStack);
        });
    }

    @Override
    public int getPaginationSize(Player player) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        List<TransactionDTO> transactions = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_DATA);
        if (transactions == null) return 0;
        return applyFilters(cache, transactions).size();
    }

    private Optional<UUID> getTarget(Player player) {
        return Optional.ofNullable(this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ADMIN_TARGET_UUID));
    }

    private void loadTransactionsAsync(Player player, UUID targetUniqueId, InventoryEngine engine) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_LOADING, true);

        showLoadingItem(engine, player);

        CompletableFuture.supplyAsync(() -> {
            return this.plugin.getStorageManager().with(TransactionRepository.class).selectByPlayer(targetUniqueId);
        }, this.plugin.getExecutorService()).thenAccept(transactions -> {
            cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_DATA, transactions);
            cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_LOADING, false);

            this.plugin.getScheduler().runNextTick(w -> {
                this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
            });
        }).exceptionally(throwable -> {
            cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_LOADING, false);
            this.plugin.getLogger().severe("Failed to load transactions: " + throwable.getMessage());
            return null;
        });
    }

    private void showLoadingItem(InventoryEngine engine, Player player) {
        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();
        ItemStack loadingItem = new ItemStack(Material.HOPPER);
        var itemMeta = loadingItem.getItemMeta();
        meta.updateDisplayName(itemMeta, "#FFD700Loading transactions...", null);
        loadingItem.setItemMeta(itemMeta);

        for (Integer slot : getSlots()) {
            engine.addItem(slot, loadingItem);
        }
    }

    private List<TransactionDTO> applyFilters(PlayerCache cache, List<TransactionDTO> transactions) {
        TransactionStatus statusFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_STATUS_FILTER);
        DateFilter dateFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_DATE_FILTER, DateFilter.ALL);

        return transactions.stream()
                .filter(tx -> statusFilter == null || tx.status() == statusFilter)
                .filter(tx -> dateFilter.matches(tx.created_at()))
                .toList();
    }

    private ItemStack buildTransactionItemStack(TransactionDTO transaction) {
        Material material = transaction.status() == TransactionStatus.PENDING ? Material.GOLD_BLOCK : Material.EMERALD_BLOCK;
        var configuration = this.plugin.getConfiguration();
        var dateFormat = configuration.getDateFormat();

        ItemStack itemStack = new ItemStack(material);
        var itemMeta = itemStack.getItemMeta();

        var meta = this.plugin.getInventoriesLoader().getInventoryManager().getMeta();

        String statusColor = transaction.status() == TransactionStatus.PENDING ? "#FFD700" : "#00FF00";
        String sign = transaction.value().signum() >= 0 ? "+" : "";
        meta.updateDisplayName(itemMeta, statusColor + "<bold>" + transaction.status().name() + " #92ffff- #2CCED2" + sign + transaction.value() + " " + transaction.economy_name(), null);

        List<String> lore = new ArrayList<>();
        lore.add("#8c8c8c• #92ffffStatus: #2CCED2" + transaction.status().name());
        lore.add("#8c8c8c• #92ffffEconomy: #2CCED2" + transaction.economy_name());
        lore.add("#8c8c8c• #92ffffBefore: #2CCED2" + transaction.before());
        lore.add("#8c8c8c• #92ffffAfter: #2CCED2" + transaction.after());
        lore.add("#8c8c8c• #92ffffValue: #2CCED2" + sign + transaction.value());
        lore.add("#8c8c8c• #92ffffDate: #2CCED2" + dateFormat.format(transaction.created_at()));

        meta.updateLore(itemMeta, lore, LoreType.REPLACE);
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }
}
