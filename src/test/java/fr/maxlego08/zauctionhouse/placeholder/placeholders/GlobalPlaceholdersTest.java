package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.placeholder.AutoPlaceholder;
import fr.maxlego08.zauctionhouse.placeholder.LocalPlaceholder;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalPlaceholdersTest {

    AuctionPlugin plugin;
    AuctionManager manager;
    CategoryManager categoryManager;
    LocalPlaceholder placeholder;
    Player player;

    @BeforeEach
    void setUp() {
        manager = mock(AuctionManager.class);
        categoryManager = mock(CategoryManager.class);
        plugin = mock(AuctionPlugin.class);
        when(plugin.getAuctionManager()).thenReturn(manager);
        when(plugin.getCategoryManager()).thenReturn(categoryManager);
        player = mock(Player.class);

        placeholder = new LocalPlaceholder(plugin);
        new GlobalPlaceholders().register(placeholder, plugin);
    }

    String resolve(String name) {
        return placeholder.setPlaceholders(player, "%zauctionhouse_" + name + "%");
    }

    @Test
    @DisplayName("register enregistre exactement listed_items et category_count_, documentes")
    void register_declaresTwoPlaceholders() {
        List<AutoPlaceholder> all = placeholder.getAutoPlaceholders();
        assertEquals(List.of("listed_items", "category_count_"), all.stream().map(AutoPlaceholder::getStartWith).toList());
        assertTrue(all.stream().noneMatch(auto -> auto.getDescription() == null || auto.getDescription().isBlank()));
        assertEquals(List.of("<category_id>"), all.get(1).getArgs());
        assertTrue(all.get(0).getArgs().isEmpty());
    }

    @Test
    @DisplayName("listed_items rend le compteur O(1) du manager, pas une copie du store")
    void listedItems_usesManagerCounter() {
        when(manager.getListedItemCount()).thenReturn(1234);
        assertEquals("1234", resolve("listed_items"));
        verify(manager).getListedItemCount();
        verify(manager, never()).getItems(StorageType.LISTED);
    }

    @Test
    @DisplayName("listed_items vaut 0 sans aucun item")
    void listedItems_zero() {
        when(manager.getListedItemCount()).thenReturn(0);
        assertEquals("0", resolve("listed_items"));
    }

    @Test
    @DisplayName("category_count_<id> interroge le CategoryManager avec l'identifiant residuel")
    void categoryCount_delegatesToCategoryManager() {
        when(categoryManager.getItemCountForCategory("blocks")).thenReturn(7L);
        when(categoryManager.getItemCountForCategory("enchanted-books")).thenReturn(0L);

        assertEquals("7", resolve("category_count_blocks"));
        assertEquals("0", resolve("category_count_enchanted-books"));
        assertEquals("0", resolve("category_count_unknown"), "categorie inconnue : le manager rend 0");
    }

    @Test
    @DisplayName("category_count_ sans identifiant rend 0 sans interroger le manager")
    void categoryCount_emptyId_returnsZero() {
        assertEquals("0", resolve("category_count_"));
        verify(categoryManager, never()).getItemCountForCategory(anyString());
    }

    @Test
    @DisplayName("un nom proche mais inconnu n'est pas resolu")
    void unknownPlaceholder_isLeftUntouched() {
        assertEquals("%zauctionhouse_listed_item%", resolve("listed_item"));
        assertNull(placeholder.onRequest(player, "listed_items_count"));
        assertFalse(resolve("listed_items").contains("%"));
    }
}
