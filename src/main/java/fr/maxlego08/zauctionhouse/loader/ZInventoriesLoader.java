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
import fr.maxlego08.zauctionhouse.buttons.ZAuctionListingButton;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;

public class ZInventoriesLoader extends ZUtils implements InventoriesLoader {

    private final AuctionPlugin plugin;
    private final PatternManager patternManager;
    private final ButtonManager buttonManager;
    private final InventoryManager inventoryManager;

    public ZInventoriesLoader(AuctionPlugin plugin) {
        this.plugin = plugin;

        this.buttonManager = getProvider(ButtonManager.class);
        this.inventoryManager = getProvider(InventoryManager.class);
        this.patternManager = getProvider(PatternManager.class);
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

        this.buttonManager.register(new NoneLoader(this.plugin, ZAuctionListingButton.class, "ZAUCTIONHOUSE_LISTING"));
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

    }

    private void createInventoriesFile() {
        copyFiles("inventories",
                "auction"
        );
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
        var optional = this.inventoryManager.getInventory(this.plugin, inventories.getFileName());
        if (optional.isEmpty()) {
            this.plugin.getLogger().severe("Unable to open inventory " + inventories.getFileName() + ", inventory not found");
            message(this.plugin, player, Message.INVENTORY_NOT_FOUND, "%inventory-name%", inventories.getFileName());
            return;
        }

        var inventory = optional.get();
        this.inventoryManager.openInventoryWithOldInventories(player, inventory, page);
    }
}
