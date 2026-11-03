package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.zauctionhouse.buttons.RefreshButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshLoaderTest extends LoaderTestBase {

    private InventoryManager inventoryManager;
    private MenuItemStack loadingItem;
    private RefreshLoader loader;

    @BeforeEach
    void setUp() {
        this.inventoryManager = mock(InventoryManager.class);
        this.loadingItem = mock(MenuItemStack.class);
        when(this.inventoryManager.loadItemStack(any(YamlConfiguration.class), any(String.class), any())).thenReturn(this.loadingItem);
        this.loader = new RefreshLoader(this.plugin, this.inventoryManager);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_REFRESH")
    void name() {
        assertEquals("ZAUCTIONHOUSE_REFRESH", loader.getName());
    }

    @Test
    @DisplayName("auction.yml livre : l'item de chargement du bouton refresh est lu sous loading-item.")
    void shippedFile() {
        YamlConfiguration configuration = TestResources.yaml("inventories/auction.yml");
        var items = configuration.getConfigurationSection("items");
        String key = items.getKeys(false).stream().filter(k -> "ZAUCTIONHOUSE_REFRESH".equals(items.getString(k + ".type"))).findFirst().orElseThrow();

        RefreshButton button = load(loader, configuration, key, RefreshButton.class);

        verify(inventoryManager).loadItemStack(eq(configuration), eq("items." + key + ".loading-item."), eq(defaultButtonValue.getFile()));
        assertSame(loadingItem, field(button, "loadingItemStack"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("configuration minimale : la lecture de l'item est toujours deleguee")
    void minimal_delegates() {
        RefreshButton button = loadInline(loader, "refresh", "slot: 49", RefreshButton.class);

        verify(inventoryManager).loadItemStack(any(YamlConfiguration.class), eq("items.refresh.loading-item."), any());
        assertSame(loadingItem, field(button, "loadingItemStack"));
    }
}
