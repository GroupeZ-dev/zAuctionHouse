package fr.maxlego08.zauctionhouse.items;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemDisplayConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.ItemLoreConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZAuctionItemTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private Configuration configuration;
    private AuctionEconomy economy;
    private final UUID seller = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(dataFolder);
        this.configuration = plugin.getConfiguration();
        this.economy = MockPlugins.economy("vault");
    }

    private ZAuctionItem item(List<ItemStack> itemStacks) {
        return TestItems.auctionItem(plugin, 1, seller, "Seller", BigDecimal.TEN, economy, System.currentTimeMillis() + 60_000L, itemStacks);
    }

    /**
     * Item mocke avec une cle de traduction stubbee (le vrai {@code Material#translationKey()}
     * exige le registre du serveur).
     */
    private static ItemStack stack(Material material, int amount, String translationKey) {
        ItemStack itemStack = TestItems.itemStack(material, amount);
        lenient().when(itemStack.translationKey()).thenReturn(translationKey);
        return itemStack;
    }

    private static ItemStack namedStack(int amount, String name) {
        ItemStack itemStack = TestItems.itemStack(Material.DIAMOND_SWORD, amount);
        ItemMeta meta = mock(ItemMeta.class);
        lenient().when(meta.hasDisplayName()).thenReturn(true);
        lenient().when(meta.displayName()).thenReturn(Component.text(name));
        lenient().when(itemStack.hasItemMeta()).thenReturn(true);
        lenient().when(itemStack.getItemMeta()).thenReturn(meta);
        return itemStack;
    }

    private void itemLore() {
        ItemLoreConfiguration lore = new ItemLoreConfiguration(false,
                List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(), List.of(), Set.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(),
                "seller-status", "buyer-status", "right-seller-status", "right-buyer-status");
        when(configuration.getItemLore()).thenReturn(lore);
    }

    private void itemDisplay(boolean mergeSimilar) {
        ItemDisplayConfiguration display = new ItemDisplayConfiguration(
                "x%amount% <lang:%item-translation-key%>", "x%amount% %item-name%", " and ", ", ", mergeSimilar);
        when(configuration.getItemDisplay()).thenReturn(display);
    }

    @Test
    @DisplayName("getItemStacks rend la liste fournie au constructeur")
    void getItemStacks_returnsGivenList() {
        List<ItemStack> stacks = List.of(TestItems.diamond(1));
        assertSame(stacks, item(stacks).getItemStacks());
    }

    @Nested
    @DisplayName("Quantite et cle de traduction")
    class AmountAndTranslationKey {

        @Test
        @DisplayName("un seul stack : getAmount rend sa quantite")
        void getAmount_singleStack() {
            assertEquals(17, item(List.of(TestItems.diamond(17))).getAmount());
        }

        @Test
        @DisplayName("plusieurs stacks (lot) : getAmount rend 0")
        void getAmount_multipleStacks_zero() {
            assertEquals(0, item(List.of(TestItems.diamond(3), TestItems.diamond(4))).getAmount());
        }

        @Test
        @DisplayName("stack unique null (donnee corrompue) : getAmount rend 0 sans NPE")
        void getAmount_nullSingleStack_zero() {
            List<ItemStack> corrupted = new ArrayList<>();
            corrupted.add(null);
            assertEquals(0, item(corrupted).getAmount());
        }

        @Test
        @DisplayName("getTranslationKey rend une chaine vide pour un lot ou un stack null")
        void getTranslationKey_lotOrNull_empty() {
            assertEquals("", item(List.of(TestItems.diamond(1), TestItems.diamond(1))).getTranslationKey());
            List<ItemStack> corrupted = new ArrayList<>();
            corrupted.add(null);
            assertEquals("", item(corrupted).getTranslationKey());
        }
    }

    @Test
    @DisplayName("getItemsAsString liste quantite et materiau de chaque stack, en ignorant les null")
    void getItemsAsString() {
        List<ItemStack> stacks = Arrays.asList(TestItems.diamond(3), null, TestItems.itemStack(Material.STONE, 64));
        assertEquals("x3 DIAMOND, x64 STONE", item(stacks).getItemsAsString());
        assertEquals("", item(List.of()).getItemsAsString());
    }

    @Nested
    @DisplayName("createStatus")
    class CreateStatus {

        @Test
        @DisplayName("stack unique : statut vendeur pour le vendeur, acheteur pour les autres")
        void single_sellerOrBuyer() {
            itemLore();
            ZAuctionItem item = item(List.of(TestItems.diamond(1)));
            assertEquals("seller-status", item.createStatus(MockPlugins.player(seller, "Seller")));
            assertEquals("buyer-status", item.createStatus(MockPlugins.player("Someone")));
        }

        @Test
        @DisplayName("lot : statuts « clic droit » vendeur / acheteur")
        void lot_rightClickVariants() {
            itemLore();
            ZAuctionItem item = item(List.of(TestItems.diamond(1), TestItems.diamond(2)));
            assertEquals("right-seller-status", item.createStatus(MockPlugins.player(seller, "Seller")));
            assertEquals("right-buyer-status", item.createStatus(MockPlugins.player("Someone")));
        }
    }

    @Nested
    @DisplayName("getItemDisplay")
    class ItemDisplay {

        @Test
        @DisplayName("stack unique sans nom : affichage par cle de traduction et quantite")
        void single_translationKey() {
            itemDisplay(true);
            ZAuctionItem item = item(List.of(stack(Material.DIAMOND, 5, "item.minecraft.diamond")));
            assertEquals("x5 <lang:item.minecraft.diamond>", item.getItemDisplay());
        }

        @Test
        @DisplayName("stack nomme : affichage par nom (MiniMessage) et quantite")
        void single_displayName() {
            itemDisplay(true);
            ZAuctionItem item = item(List.of(namedStack(1, "Excalibur")));
            assertEquals("x1 Excalibur", item.getItemDisplay());
        }

        @Test
        @DisplayName("sans fusion : chaque stack est affiche, separes par « , » puis « and » avant le dernier")
        void multiple_noMerge_separators() {
            itemDisplay(false);
            ItemStack a = stack(Material.DIAMOND, 3, "item.a");
            ItemStack b = stack(Material.STONE, 2, "block.b");
            ItemStack c = stack(Material.APPLE, 1, "item.c");
            lenient().when(a.isSimilar(b)).thenReturn(true);

            String display = item(List.of(a, b, c)).getItemDisplay();

            assertEquals("x3 <lang:item.a>, x2 <lang:block.b> and x1 <lang:item.c>", display);
            verify(a, never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("deux stacks : seul « and » separe")
        void two_noMerge_andOnly() {
            itemDisplay(false);
            ItemStack a = stack(Material.DIAMOND, 3, "item.a");
            ItemStack b = stack(Material.STONE, 2, "block.b");
            assertEquals("x3 <lang:item.a> and x2 <lang:block.b>", item(List.of(a, b)).getItemDisplay());
        }

        @Test
        @DisplayName("avec fusion : les stacks similaires sont cumules sur le premier et affiches une seule fois")
        void multiple_merge_similarStacksAreSummed() {
            itemDisplay(true);
            ItemStack a = stack(Material.DIAMOND, 3, "item.a");
            ItemStack b = stack(Material.DIAMOND, 2, "item.a");
            ItemStack c = stack(Material.STONE, 1, "block.c");
            when(a.isSimilar(b)).thenReturn(true);
            lenient().when(a.isSimilar(c)).thenReturn(false);

            String display = item(List.of(a, b, c)).getItemDisplay();

            verify(a).setAmount(5);
            // Les doublures ne changent pas de quantite : seul le nombre d'entrees est observable.
            assertEquals("x3 <lang:item.a> and x1 <lang:block.c>", display);
        }

        @Test
        @DisplayName("les stacks null sont ignores avant la fusion et le rendu")
        void nullStacks_areFiltered() {
            itemDisplay(true);
            ItemStack a = stack(Material.DIAMOND, 1, "item.a");
            assertEquals("x1 <lang:item.a>", item(Arrays.asList(null, a, null)).getItemDisplay());
        }

        @Test
        @DisplayName("aucun stack exploitable : chaine vide")
        void noStacks_empty() {
            itemDisplay(true);
            assertEquals("", item(List.of()).getItemDisplay());
        }
    }

    @Test
    @DisplayName("le constructeur accepte une economie et en memorise le nom")
    void constructor_economyName() {
        ZAuctionItem item = item(List.of(TestItems.diamond(1)));
        assertEquals("vault", item.getEconomyName());
        assertSame(economy, item.getAuctionEconomy());
    }

    @Test
    @DisplayName("un spectateur quelconque est accepte par createStatus meme pour un lot vide")
    void createStatus_emptyLot() {
        itemLore();
        Player player = MockPlugins.player("P");
        assertEquals("right-buyer-status", item(List.of()).createStatus(player));
    }
}
