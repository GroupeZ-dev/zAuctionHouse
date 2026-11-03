package fr.maxlego08.zauctionhouse.hooks.itemcontent;

import fr.maxlego08.zauctionhouse.api.hooks.itemcontent.ItemContentProvider;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZItemContentManagerTest {

    private ZItemContentManager manager;
    private ItemStack shulker;
    private ItemStack diamond;

    @BeforeEach
    void setUp() {
        this.manager = new ZItemContentManager();
        this.shulker = TestItems.itemStack(Material.SHULKER_BOX, 1);
        this.diamond = TestItems.diamond(1);
    }

    /**
     * Provider mocke qui reconnait exactement les ItemStacks passes en parametre.
     */
    private static ItemContentProvider provider(String name, int priority, List<ItemStack> contents, ItemStack... recognized) {
        ItemContentProvider provider = mock(ItemContentProvider.class);
        lenient().when(provider.getName()).thenReturn(name);
        lenient().when(provider.getPriority()).thenReturn(priority);
        lenient().when(provider.isContainer(any())).thenAnswer(invocation -> Arrays.asList(recognized).contains(invocation.<ItemStack>getArgument(0)));
        lenient().when(provider.getContents(any())).thenReturn(contents);
        return provider;
    }

    @Nested
    @DisplayName("Sans provider")
    class EmptyTests {

        @Test
        @DisplayName("rien n'est un conteneur et les contenus sont vides")
        void noProvider_nothingIsContainer() {
            assertFalse(manager.isContainer(shulker));
            assertTrue(manager.getContents(shulker).isEmpty());
            assertTrue(manager.getContainers(List.of(shulker, diamond)).isEmpty());
            assertFalse(manager.containsContainer(List.of(shulker, diamond)));
        }
    }

    @Nested
    @DisplayName("Gardes null")
    class NullTests {

        @Test
        @DisplayName("ItemStack null -> false / liste vide, sans interroger les providers")
        void nullItemStack() {
            ItemContentProvider provider = provider("P", 10, List.of(diamond), shulker);
            manager.registerProvider(provider);

            assertFalse(manager.isContainer(null));
            assertTrue(manager.getContents(null).isEmpty());
            verify(provider, never()).isContainer(any());
        }

        @Test
        @DisplayName("liste null -> liste vide / false")
        void nullList() {
            manager.registerProvider(provider("P", 10, List.of(), shulker));

            assertTrue(manager.getContainers(null).isEmpty());
            assertFalse(manager.containsContainer(null));
        }
    }

    @Nested
    @DisplayName("Enregistrement et priorite")
    class RegistrationTests {

        @Test
        @DisplayName("un provider enregistre reconnait ses conteneurs et fournit leur contenu")
        void registeredProvider_isUsed() {
            List<ItemStack> contents = List.of(diamond, TestItems.diamond(3));
            manager.registerProvider(provider("Vanilla", 100, contents, shulker));

            assertTrue(manager.isContainer(shulker));
            assertFalse(manager.isContainer(diamond));
            assertSame(contents, manager.getContents(shulker));
            assertTrue(manager.getContents(diamond).isEmpty());
        }

        @Test
        @DisplayName("le provider de plus faible priorite est consulte en premier, quel que soit l'ordre d'enregistrement")
        void lowerPriority_checkedFirst() {
            List<ItemStack> lowContents = List.of(TestItems.diamond(1));
            List<ItemStack> highContents = List.of(TestItems.diamond(2));
            ItemContentProvider high = provider("High", 100, highContents, shulker);
            ItemContentProvider low = provider("Low", 10, lowContents, shulker);
            manager.registerProvider(high);
            manager.registerProvider(low);

            assertSame(lowContents, manager.getContents(shulker));
            verify(high, never()).getContents(any());
        }

        @Test
        @DisplayName("si le premier provider ne reconnait pas l'item, le suivant est consulte")
        void fallsThroughToNextProvider() {
            List<ItemStack> contents = List.of(TestItems.diamond(9));
            manager.registerProvider(provider("First", 1, List.of(), diamond));
            manager.registerProvider(provider("Second", 2, contents, shulker));

            assertSame(contents, manager.getContents(shulker));
            assertTrue(manager.isContainer(shulker));
        }

        @Test
        @DisplayName("unregisterProvider retire par nom, sans tenir compte de la casse")
        void unregister_byNameIgnoringCase() {
            manager.registerProvider(provider("Vanilla", 100, List.of(), shulker));

            manager.unregisterProvider("vAnIlLa");

            assertFalse(manager.isContainer(shulker));
        }

        @Test
        @DisplayName("unregisterProvider d'un nom inconnu ne change rien")
        void unregister_unknownName_noop() {
            manager.registerProvider(provider("Vanilla", 100, List.of(), shulker));

            manager.unregisterProvider("Unknown");

            assertTrue(manager.isContainer(shulker));
        }

        @Test
        @DisplayName("deux providers de meme nom sont tous deux retires")
        void unregister_removesDuplicates() {
            manager.registerProvider(provider("Dup", 1, List.of(), shulker));
            manager.registerProvider(provider("Dup", 2, List.of(), diamond));

            manager.unregisterProvider("Dup");

            assertFalse(manager.isContainer(shulker));
            assertFalse(manager.isContainer(diamond));
        }
    }

    @Nested
    @DisplayName("Operations sur listes")
    class ListTests {

        @BeforeEach
        void registerVanilla() {
            manager.registerProvider(provider("Vanilla", 100, List.of(), shulker));
        }

        @Test
        @DisplayName("getContainers filtre en conservant l'ordre et les doublons")
        void getContainers_filtersKeepingOrder() {
            ItemStack otherShulker = TestItems.itemStack(Material.SHULKER_BOX, 1);
            manager.registerProvider(provider("Other", 50, List.of(), otherShulker));

            List<ItemStack> containers = manager.getContainers(List.of(diamond, shulker, otherShulker, shulker));

            assertEquals(List.of(shulker, otherShulker, shulker), containers);
        }

        @Test
        @DisplayName("getContainers sur une liste vide -> vide")
        void getContainers_emptyList() {
            assertTrue(manager.getContainers(List.of()).isEmpty());
        }

        @Test
        @DisplayName("containsContainer vrai des qu'un element est un conteneur")
        void containsContainer() {
            assertTrue(manager.containsContainer(List.of(diamond, shulker)));
            assertFalse(manager.containsContainer(List.of(diamond, TestItems.diamond(2))));
            assertFalse(manager.containsContainer(List.of()));
        }
    }
}
