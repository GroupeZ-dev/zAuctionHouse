package fr.maxlego08.zauctionhouse.hooks.itemcontent;

import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code getContents} exige un BlockStateMeta reel (serveur) : seuls le nom, la priorite et la
 * detection du conteneur sont testables ici.
 */
class VanillaShulkerContentProviderTest {

    private final VanillaShulkerContentProvider provider = new VanillaShulkerContentProvider();

    @Test
    @DisplayName("nom 'Vanilla' et priorite 100 (consulte apres les hooks de plugins)")
    void nameAndPriority() {
        assertEquals("Vanilla", provider.getName());
        assertEquals(100, provider.getPriority());
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "SHULKER_BOX", "WHITE_SHULKER_BOX", "ORANGE_SHULKER_BOX", "MAGENTA_SHULKER_BOX", "LIGHT_BLUE_SHULKER_BOX",
            "YELLOW_SHULKER_BOX", "LIME_SHULKER_BOX", "PINK_SHULKER_BOX", "GRAY_SHULKER_BOX", "LIGHT_GRAY_SHULKER_BOX",
            "CYAN_SHULKER_BOX", "PURPLE_SHULKER_BOX", "BLUE_SHULKER_BOX", "BROWN_SHULKER_BOX", "GREEN_SHULKER_BOX",
            "RED_SHULKER_BOX", "BLACK_SHULKER_BOX"})
    @DisplayName("toutes les couleurs de shulker sont des conteneurs")
    void everyShulkerColor_isContainer(Material material) {
        assertTrue(provider.isContainer(TestItems.itemStack(material, 1)));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"DIAMOND", "CHEST", "BARREL", "BUNDLE", "ENDER_CHEST", "AIR"})
    @DisplayName("les autres materiaux (meme des conteneurs blocs) ne sont pas des shulkers")
    void otherMaterials_notContainer(Material material) {
        assertFalse(provider.isContainer(TestItems.itemStack(material, 1)));
    }

    @Test
    @DisplayName("ItemStack null -> false")
    void nullItemStack_notContainer() {
        assertFalse(provider.isContainer(null));
    }
}
