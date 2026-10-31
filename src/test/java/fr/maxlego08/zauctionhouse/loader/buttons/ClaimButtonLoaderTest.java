package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.zauctionhouse.buttons.ClaimButton;
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

class ClaimButtonLoaderTest extends LoaderTestBase {

    private InventoryManager inventoryManager;
    private MenuItemStack loadingItem;
    private ClaimButtonLoader loader;

    @BeforeEach
    void setUp() {
        this.inventoryManager = mock(InventoryManager.class);
        this.loadingItem = mock(MenuItemStack.class);
        when(this.inventoryManager.loadItemStack(any(YamlConfiguration.class), any(String.class), any())).thenReturn(this.loadingItem);
        this.loader = new ClaimButtonLoader(this.plugin, this.inventoryManager);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_CLAIM")
    void name() {
        assertEquals("ZAUCTIONHOUSE_CLAIM", loader.getName());
    }

    @Test
    @DisplayName("l'item de chargement est lu sous <path>loading-item. avec le fichier du bouton")
    void loadingItem_readFromSubSection() {
        YamlConfiguration configuration = inline("claim", """
                loading-item:
                  material: CLOCK
                """);

        ClaimButton button = load(loader, configuration, "claim", ClaimButton.class);

        verify(inventoryManager).loadItemStack(eq(configuration), eq("items.claim.loading-item."), eq(defaultButtonValue.getFile()));
        assertSame(loadingItem, field(button, "loadingItemStack"));
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("sans section loading-item : la lecture est quand meme deleguee (zMenu gere les defauts)")
    void missingLoadingItem_stillDelegated() {
        ClaimButton button = loadInline(loader, "claim", "slot: 49", ClaimButton.class);

        verify(inventoryManager).loadItemStack(any(YamlConfiguration.class), eq(TestResources.buttonPath("claim") + "loading-item."), any());
        assertSame(loadingItem, field(button, "loadingItemStack"));
    }
}
