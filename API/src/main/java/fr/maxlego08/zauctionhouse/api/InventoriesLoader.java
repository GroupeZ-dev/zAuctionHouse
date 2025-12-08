package fr.maxlego08.zauctionhouse.api;

import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Handles discovery and loading of every graphical inventory registered by the plugin, including
 * associated patterns and reusable button configurations. Implementations encapsulate the loading
 * strategy (filesystem, packaged defaults, caching) so integrations can simply request menus to be
 * opened for players.
 */
public interface InventoriesLoader {

    /**
     * Loads all available inventories from the configured sources. This should be called during
     * plugin startup before any menus are opened.
     */
    void loadInventories();

    /**
     * Loads a single inventory definition from the provided file, updating the registry if it
     * already existed.
     *
     * @param file serialized inventory definition to parse
     */
    void loadInventory(File file);

    /**
     * Loads every configured pattern (slot layouts, decorations, etc.) that inventories can reuse.
     */
    void loadPatterns();

    /**
     * Loads a specific pattern file and registers it for later inventory construction.
     *
     * @param file serialized pattern to parse
     */
    void loadPattern(File file);

    /**
     * Loads reusable button configurations so inventories can reference them by key instead of
     * duplicating definitions.
     */
    void loadButtons();

    /**
     * Convenience method that sequentially loads buttons, patterns, and inventories. Intended for
     * first-time initialization.
     */
    void load();

    /**
     * Reloads every inventory-related resource from disk, replacing existing registrations so
     * changes take effect without restarting the server.
     */
    void reload();

    /**
     * @return the underlying inventory manager used to register and open menus
     */
    InventoryManager getInventoryManager();

    /**
     * @return the button manager providing access to shared button definitions
     */
    ButtonManager getButtonManager();

    /**
     * Opens the specified inventory for the player on its initial page.
     *
     * @param player      viewer who should see the menu
     * @param inventories identifier of the inventory to open
     */
    void openInventory(Player player, Inventories inventories);

    /**
     * Opens the specified inventory for the player at the requested page index, enabling
     * pagination-aware navigation.
     *
     * @param player      viewer who should see the menu
     * @param inventories identifier of the inventory to open
     * @param page        page index to display
     */
    void openInventory(Player player, Inventories inventories, int page);
}
