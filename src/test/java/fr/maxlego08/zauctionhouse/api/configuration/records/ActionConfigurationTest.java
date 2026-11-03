package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActionConfigurationTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin(YamlConfiguration configuration) {
        return ConfigTestSupport.configPlugin(this.dataFolder, configuration);
    }

    @Test
    @DisplayName("configuration vide : valeurs par defaut de chaque sous-record")
    void of_defaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        AuctionPlugin plugin = plugin(yaml);

        ActionConfiguration actions = ActionConfiguration.of(plugin, yaml);

        assertFalse(actions.updateInventoryOnAction());
        assertTrue(actions.resetCategoryOnOpen());
        assertTrue(actions.resetSearchOnOpen());
        assertTrue(actions.containerPreview().enable());
        assertEquals(ClickType.RIGHT, actions.containerPreview().click());
        assertFalse(actions.listed().giveItem());
        assertFalse(actions.listed().openInventory());
        assertFalse(actions.listed().openConfirmInventory());
        assertFalse(actions.purchased().giveItem());
        assertFalse(actions.purchased().openInventory());
        assertFalse(actions.purchased().sendNoMoneyMessage());
        assertFalse(actions.purchased().freeSpace());
        assertFalse(actions.purchased().noMoney().enable());
        assertEquals(0, actions.purchased().noMoney().duration());
        assertNull(actions.purchased().noMoney().menuItemStack());
        assertFalse(actions.purchased().noMoneySound().enable());
        assertEquals(SoundCategory.MASTER, actions.purchased().noMoneySound().category());
        assertFalse(actions.selling().openInventory());
        assertTrue(actions.selling().freeSpace());
        assertFalse(actions.expired().openInventory());
        assertTrue(actions.expired().freeSpace());
        verify(plugin.getInventoriesLoader().getInventoryManager(), never()).loadItemStack(any(YamlConfiguration.class), anyString(), any(File.class));
    }

    @Test
    @DisplayName("valeurs explicites de chaque sous-record")
    void of_explicitValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("action.update-inventory-on-action", true);
        yaml.set("action.reset-category-on-open", false);
        yaml.set("action.reset-search-on-open", false);
        yaml.set("action.container-preview.enable", false);
        yaml.set("action.container-preview.click", "shift_left");
        yaml.set("action.remove-listed-item.give-item", true);
        yaml.set("action.remove-listed-item.open-inventory", true);
        yaml.set("action.remove-listed-item.open-confirm-inventory", true);
        yaml.set("action.purchased-item.give-item", true);
        yaml.set("action.purchased-item.open-inventory", true);
        yaml.set("action.purchased-item.money-message", true);
        yaml.set("action.purchased-item.player-inventory-must-have-free-space", true);
        yaml.set("action.selling-item.open-inventory", true);
        yaml.set("action.selling-item.player-inventory-must-have-free-space", false);
        yaml.set("action.remove-expired-item.open-inventory", true);
        yaml.set("action.remove-expired-item.player-inventory-must-have-free-space", false);

        ActionConfiguration actions = ActionConfiguration.of(plugin(yaml), yaml);

        assertTrue(actions.updateInventoryOnAction());
        assertFalse(actions.resetCategoryOnOpen());
        assertFalse(actions.resetSearchOnOpen());
        assertFalse(actions.containerPreview().enable());
        assertEquals(ClickType.SHIFT_LEFT, actions.containerPreview().click());
        assertTrue(actions.listed().giveItem());
        assertTrue(actions.listed().openInventory());
        assertTrue(actions.listed().openConfirmInventory());
        assertTrue(actions.purchased().giveItem());
        assertTrue(actions.purchased().openInventory());
        assertTrue(actions.purchased().sendNoMoneyMessage());
        assertTrue(actions.purchased().freeSpace());
        assertTrue(actions.selling().openInventory());
        assertFalse(actions.selling().freeSpace());
        assertTrue(actions.expired().openInventory());
        assertFalse(actions.expired().freeSpace());
    }

    @Test
    @DisplayName("type de clic invalide : RIGHT")
    void containerPreview_invalidClick_right() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("action.container-preview.click", "TRIPLE_TAP");

        assertEquals(ClickType.RIGHT, ActionConfiguration.ContainerPreviewConfiguration.of(plugin(yaml), yaml).click());
    }

    @Test
    @DisplayName("item 'pas assez d'argent' actif : charge via zMenu, duree conservee")
    void noMoney_enabled_loadsItem() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("action.purchased-item.money-item.enable", true);
        yaml.set("action.purchased-item.money-item.duration", 45);
        yaml.set("action.purchased-item.money-item.item.material", "BARRIER");
        AuctionPlugin plugin = plugin(yaml);

        ActionConfiguration.PurchaseNoMoneyConfiguration noMoney = ActionConfiguration.PurchaseNoMoneyConfiguration.of(plugin, yaml);

        assertTrue(noMoney.enable());
        assertEquals(45, noMoney.duration());
        MenuItemStack expected = plugin.getInventoriesLoader().getInventoryManager().loadItemStack(yaml, "x", new File("x"));
        assertNotNull(noMoney.menuItemStack());
        assertEquals(expected, noMoney.menuItemStack());
        verify(plugin.getInventoriesLoader().getInventoryManager()).loadItemStack(yaml, "action.purchased-item.money-item.item.", new File(dataFolder, "config.yml"));
    }

    @Test
    @DisplayName("duree nulle ou negative : ramenee a 1 ; duree absente : 1")
    void noMoney_invalidDuration_one() {
        for (int duration : new int[]{0, -5}) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("action.purchased-item.money-item.enable", true);
            yaml.set("action.purchased-item.money-item.duration", duration);

            assertEquals(1, ActionConfiguration.PurchaseNoMoneyConfiguration.of(plugin(yaml), yaml).duration(), "duree " + duration);
        }
        YamlConfiguration missing = new YamlConfiguration();
        missing.set("action.purchased-item.money-item.enable", true);
        assertEquals(1, ActionConfiguration.PurchaseNoMoneyConfiguration.of(plugin(missing), missing).duration());
    }

    @Test
    @DisplayName("item desactive : aucun chargement zMenu, duree 0, item null")
    void noMoney_disabled_noLoad() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("action.purchased-item.money-item.enable", false);
        yaml.set("action.purchased-item.money-item.duration", 45);
        AuctionPlugin plugin = plugin(yaml);

        ActionConfiguration.PurchaseNoMoneyConfiguration noMoney = ActionConfiguration.PurchaseNoMoneyConfiguration.of(plugin, yaml);

        assertFalse(noMoney.enable());
        assertEquals(0, noMoney.duration());
        assertNull(noMoney.menuItemStack());
        verify(plugin.getInventoriesLoader().getInventoryManager(), never()).loadItemStack(any(YamlConfiguration.class), anyString(), any(File.class));
    }
}
