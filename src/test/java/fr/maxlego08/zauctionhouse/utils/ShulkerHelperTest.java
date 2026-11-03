package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Les {@code ItemStack} sont des doublures : sans serveur, le contenu reel d'une shulker
 * (lecture de {@code Material#isAir()} via le registre) n'est pas accessible, seuls les
 * chemins de garde de {@code getShulkerContent} sont donc couverts.
 */
class ShulkerHelperTest {

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "SHULKER_BOX", "WHITE_SHULKER_BOX", "ORANGE_SHULKER_BOX", "MAGENTA_SHULKER_BOX",
            "LIGHT_BLUE_SHULKER_BOX", "YELLOW_SHULKER_BOX", "LIME_SHULKER_BOX", "PINK_SHULKER_BOX",
            "GRAY_SHULKER_BOX", "LIGHT_GRAY_SHULKER_BOX", "CYAN_SHULKER_BOX", "PURPLE_SHULKER_BOX",
            "BLUE_SHULKER_BOX", "BROWN_SHULKER_BOX", "GREEN_SHULKER_BOX", "RED_SHULKER_BOX", "BLACK_SHULKER_BOX"})
    @DisplayName("les 17 variantes de shulker box sont reconnues")
    void isShulkerBox_allColors(Material material) {
        assertTrue(ShulkerHelper.isShulkerBox(material));
        assertTrue(ShulkerHelper.isShulkerBox(TestItems.itemStack(material, 1)));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"DIAMOND", "CHEST", "BUNDLE", "SHULKER_SHELL", "ENDER_CHEST", "AIR"})
    @DisplayName("les autres materiaux (dont la coquille et le bundle) ne sont pas des shulker box")
    void isShulkerBox_otherMaterials(Material material) {
        assertFalse(ShulkerHelper.isShulkerBox(material));
        assertFalse(ShulkerHelper.isShulkerBox(TestItems.itemStack(material, 1)));
    }

    @Test
    @DisplayName("isShulkerBox(ItemStack null) rend false")
    void isShulkerBox_nullItem_false() {
        assertFalse(ShulkerHelper.isShulkerBox((ItemStack) null));
    }

    @Test
    @DisplayName("getShulkerBoxes ne conserve que les shulkers, dans l'ordre, et tolere null")
    void getShulkerBoxes_filters() {
        ItemStack red = TestItems.itemStack(Material.RED_SHULKER_BOX, 1);
        ItemStack plain = TestItems.itemStack(Material.SHULKER_BOX, 1);
        ItemStack diamond = TestItems.diamond(3);
        List<ItemStack> input = Arrays.asList(diamond, red, null, plain);

        List<ItemStack> result = ShulkerHelper.getShulkerBoxes(input);

        assertEquals(2, result.size());
        assertSame(red, result.get(0));
        assertSame(plain, result.get(1));
        assertTrue(ShulkerHelper.getShulkerBoxes(null).isEmpty());
        assertTrue(ShulkerHelper.getShulkerBoxes(List.of()).isEmpty());
        assertTrue(ShulkerHelper.getShulkerBoxes(List.of(diamond)).isEmpty());
    }

    @Test
    @DisplayName("containsShulkerBox detecte au moins une shulker, et rend false pour null/vide/sans shulker")
    void containsShulkerBox() {
        ItemStack shulker = TestItems.itemStack(Material.BLUE_SHULKER_BOX, 1);
        assertTrue(ShulkerHelper.containsShulkerBox(Arrays.asList(TestItems.diamond(1), null, shulker)));
        assertFalse(ShulkerHelper.containsShulkerBox(List.of(TestItems.diamond(1))));
        assertFalse(ShulkerHelper.containsShulkerBox(List.of()));
        assertFalse(ShulkerHelper.containsShulkerBox(null));
    }

    @Test
    @DisplayName("getShulkerContent rend une liste vide pour null ou un item qui n'est pas une shulker")
    void getShulkerContent_notShulker_empty() {
        assertTrue(ShulkerHelper.getShulkerContent(null).isEmpty());
        assertTrue(ShulkerHelper.getShulkerContent(TestItems.diamond(1)).isEmpty());
        assertEquals(0, ShulkerHelper.getShulkerContentCount(TestItems.diamond(1)));
        assertTrue(ShulkerHelper.isShulkerEmpty(null));
    }

    @Test
    @DisplayName("getShulkerContent rend une liste vide quand la meta n'est pas une BlockStateMeta")
    void getShulkerContent_wrongMeta_empty() {
        ItemStack shulker = TestItems.itemStack(Material.SHULKER_BOX, 1);
        when(shulker.getItemMeta()).thenReturn(mock(ItemMeta.class));
        assertTrue(ShulkerHelper.getShulkerContent(shulker).isEmpty());

        ItemStack noMeta = TestItems.itemStack(Material.SHULKER_BOX, 1);
        when(noMeta.getItemMeta()).thenReturn(null);
        assertTrue(ShulkerHelper.getShulkerContent(noMeta).isEmpty());
    }

    @Test
    @DisplayName("getShulkerContent rend une liste vide quand l'etat de bloc n'est pas une ShulkerBox")
    void getShulkerContent_wrongBlockState_empty() {
        ItemStack shulker = TestItems.itemStack(Material.SHULKER_BOX, 1);
        BlockStateMeta meta = mock(BlockStateMeta.class);
        when(meta.getBlockState()).thenReturn(mock(BlockState.class));
        when(shulker.getItemMeta()).thenReturn(meta);

        assertTrue(ShulkerHelper.getShulkerContent(shulker).isEmpty());
        assertTrue(ShulkerHelper.isShulkerEmpty(shulker));
    }

    @Test
    @DisplayName("une shulker dont tous les slots sont null est vide")
    void getShulkerContent_allNullSlots_empty() {
        ItemStack shulker = TestItems.itemStack(Material.PURPLE_SHULKER_BOX, 1);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[27]);
        ShulkerBox shulkerBox = mock(ShulkerBox.class);
        when(shulkerBox.getInventory()).thenReturn(inventory);
        BlockStateMeta meta = mock(BlockStateMeta.class);
        when(meta.getBlockState()).thenReturn(shulkerBox);
        when(shulker.getItemMeta()).thenReturn(meta);

        assertTrue(ShulkerHelper.getShulkerContent(shulker).isEmpty());
        assertEquals(0, ShulkerHelper.getShulkerContentCount(shulker));
        assertTrue(ShulkerHelper.isShulkerEmpty(shulker));
    }
}
