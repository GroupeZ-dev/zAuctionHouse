package fr.maxlego08.zauctionhouse.loader;

import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.exceptions.InventoryException;
import fr.maxlego08.menu.api.loader.NoneLoader;
import fr.maxlego08.menu.api.pattern.PatternManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.buttons.AuctionItemsButton;
import fr.maxlego08.zauctionhouse.buttons.ShowButton;
import fr.maxlego08.zauctionhouse.buttons.admin.AdminExpiredItemsButton;
import fr.maxlego08.zauctionhouse.buttons.admin.AdminLogsButton;
import fr.maxlego08.zauctionhouse.buttons.admin.AdminOwnedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.admin.AdminPurchasedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.admin.AdminTransactionsButton;
import fr.maxlego08.zauctionhouse.buttons.admin.LogDateFilterButton;
import fr.maxlego08.zauctionhouse.buttons.admin.LogTypeFilterButton;
import fr.maxlego08.zauctionhouse.buttons.admin.TransactionDateFilterButton;
import fr.maxlego08.zauctionhouse.buttons.admin.TransactionStatusFilterButton;
import fr.maxlego08.zauctionhouse.buttons.admin.history.*;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmPurchaseButton;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmRemoveListedButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.ExpiredInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.OwnedInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.PurchasedInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.list.ExpiredItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.OwnedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.PurchasedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.sell.SellBuyButton;
import fr.maxlego08.zauctionhouse.buttons.sell.SellCancelButton;
import fr.maxlego08.zauctionhouse.buttons.sell.SellSlotButton;
import fr.maxlego08.zauctionhouse.loader.buttons.CategoryButtonLoader;
import fr.maxlego08.zauctionhouse.loader.buttons.SortLoader;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;

public class ZInventoriesLoader extends ZUtils implements InventoriesLoader {

    private final AuctionPlugin plugin;
    private final PatternManager patternManager;
    private final ButtonManager buttonManager;
    private final InventoryManager inventoryManager;
    private final PerformanceDebug performanceDebug;

    public ZInventoriesLoader(AuctionPlugin plugin) {
        this.plugin = plugin;

        this.buttonManager = getProvider(ButtonManager.class);
        this.inventoryManager = getProvider(InventoryManager.class);
        this.patternManager = getProvider(PatternManager.class);
        this.performanceDebug = new PerformanceDebug(plugin);
    }

    private <T> T getProvider(Class<T> classz) {
        RegisteredServiceProvider<T> provider = this.plugin.getServer().getServicesManager().getRegistration(classz);
        if (provider == null) {
            this.plugin.getLogger().severe("Unable to retrieve the provider " + classz);
            return null;
        }
        return provider.getProvider();
    }

    @Override
    public void loadInventories() {

        File folder = new File(this.plugin.getDataFolder(), "inventories");
        if (!folder.exists()) {
            folder.mkdir();
            createInventoriesFile();
        }

        files(folder, this::loadInventory);
    }

    @Override
    public void loadInventory(File file) {
        try {
            this.inventoryManager.loadInventory(this.plugin, file);
        } catch (InventoryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void loadPatterns() {

        File folder = new File(this.plugin.getDataFolder(), "patterns");
        if (!folder.exists()) {
            folder.mkdir();
            createPatternFiles();
        }

        this.files(folder, this::loadPattern);
    }

    @Override
    public void loadPattern(File file) {
        try {
            this.patternManager.loadPattern(file);
        } catch (InventoryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void loadButtons() {

        this.buttonManager.unregisters(this.plugin);

        // Player
        this.buttonManager.register(new NoneLoader(this.plugin, ListedItemsButton.class, "ZAUCTIONHOUSE_LISTED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, ExpiredItemsButton.class, "ZAUCTIONHOUSE_EXPIRED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, OwnedItemsButton.class, "ZAUCTIONHOUSE_OWNED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, PurchasedItemsButton.class, "ZAUCTIONHOUSE_PURCHASED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AuctionItemsButton.class, "ZAUCTIONHOUSE_AUCTION_ITEMS"));

        this.buttonManager.register(new NoneLoader(this.plugin, ExpiredInventoryButton.class, "ZAUCTIONHOUSE_EXPIRED_INVENTORY"));
        this.buttonManager.register(new NoneLoader(this.plugin, OwnedInventoryButton.class, "ZAUCTIONHOUSE_OWNED_INVENTORY"));
        this.buttonManager.register(new NoneLoader(this.plugin, PurchasedInventoryButton.class, "ZAUCTIONHOUSE_PURCHASED_INVENTORY"));

        this.buttonManager.register(new NoneLoader(this.plugin, ShowButton.class, "ZAUCTIONHOUSE_SHOW"));
        this.buttonManager.register(new NoneLoader(this.plugin, ConfirmRemoveListedButton.class, "ZAUCTIONHOUSE_CONFIRM_REMOVE_LISTED"));
        this.buttonManager.register(new NoneLoader(this.plugin, ConfirmPurchaseButton.class, "ZAUCTIONHOUSE_CONFIRM_PURCHASE"));
        this.buttonManager.register(new NoneLoader(this.plugin, SellSlotButton.class, "ZAUCTIONHOUSE_SELL_SLOT"));
        this.buttonManager.register(new NoneLoader(this.plugin, SellBuyButton.class, "ZAUCTIONHOUSE_SELL_BUY"));
        this.buttonManager.register(new NoneLoader(this.plugin, SellCancelButton.class, "ZAUCTIONHOUSE_SELL_CANCEL"));

        // Admin
        this.buttonManager.register(new NoneLoader(this.plugin, AdminOwnedItemsButton.class, "ZAUCTIONHOUSE_ADMIN_OWNED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminExpiredItemsButton.class, "ZAUCTIONHOUSE_ADMIN_EXPIRED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminPurchasedItemsButton.class, "ZAUCTIONHOUSE_ADMIN_PURCHASED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminLogsButton.class, "ZAUCTIONHOUSE_ADMIN_LOGS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminTransactionsButton.class, "ZAUCTIONHOUSE_ADMIN_TRANSACTIONS"));
        this.buttonManager.register(new NoneLoader(this.plugin, LogTypeFilterButton.class, "ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_TYPE"));
        this.buttonManager.register(new NoneLoader(this.plugin, LogDateFilterButton.class, "ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_DATE"));
        this.buttonManager.register(new NoneLoader(this.plugin, TransactionStatusFilterButton.class, "ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_STATUS"));
        this.buttonManager.register(new NoneLoader(this.plugin, TransactionDateFilterButton.class, "ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_DATE"));

        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainButton.class, "ZAUCTIONHOUSE_ADMIN_HISTORY_MAIN"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainLogsButton.class, "ZAUCTIONHOUSE_ADMIN_HISTORY_LOGS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainOwnedButton.class, "ZAUCTIONHOUSE_ADMIN_HISTORY_OWNED"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainPurchasedButton.class, "ZAUCTIONHOUSE_ADMIN_HISTORY_PURCHASED"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainTransactionsButton.class, "ZAUCTIONHOUSE_ADMIN_HISTORY_TRANSACTIONS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainExpiredButton.class, "ZAUCTIONHOUSE_ADMIN_HISTORY_EXPIRED"));

        this.buttonManager.register(new SortLoader(this.plugin, this.inventoryManager));
        this.buttonManager.register(new CategoryButtonLoader(this.plugin));
    }

    @Override
    public void load() {

        this.loadButtons();
        this.loadPatterns();
        this.loadInventories();
    }

    @Override
    public void reload() {

        this.inventoryManager.deleteInventories(this.plugin);
        this.loadPatterns();
        this.loadInventories();
    }

    private void createPatternFiles() {
        copyFiles("patterns", "decoration", "pagination", "back");
    }

    private void createInventoriesFile() {
        copyFiles("inventories", "auction", "expired-items", "owned-items", "purchased-items",//
                "admin/admin-owned-items", "admin/admin-expired-items", "admin/admin-purchased-items", "admin/admin-history-main", //
                "admin/admin-logs", "admin/admin-transactions", //
                "remove-confirm", "purchase-confirm", "auction-item", "sell-inventory", "categories");
    }

    private void copyFiles(String path, String... files) {
        for (String fileName : files) {
            this.plugin.saveFile(path + "/" + fileName + ".yml", false);
        }
    }

    @Override
    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    @Override
    public ButtonManager getButtonManager() {
        return buttonManager;
    }

    @Override
    public void openInventory(Player player, Inventories inventories) {
        this.openInventory(player, inventories, 1);
    }

    @Override
    public void openInventory(Player player, Inventories inventories, int page) {
        long start = this.performanceDebug.start();

        var optional = this.inventoryManager.getInventory(this.plugin, inventories.getFileName());
        if (optional.isEmpty()) {
            this.plugin.getLogger().severe("Unable to open inventory " + inventories.getFileName() + ", inventory not found");
            message(this.plugin, player, Message.INVENTORY_NOT_FOUND, "%inventory-name%", inventories.getFileName());
            return;
        }

        var inventory = optional.get();
        this.inventoryManager.openInventoryWithOldInventories(player, inventory, page);

        this.performanceDebug.end("openInventory." + inventories.getFileName(), start, "for=" + player.getName() + ", page=" + page);
    }
}
