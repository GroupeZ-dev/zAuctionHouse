package fr.maxlego08.zauctionhouse.category;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.rule.ZRuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.testutil.BukkitMocks;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le tag custom "blocks" de production exige un serveur ({@code Material.isBlock()}) : il est
 * remplace par un faux tag dont les membres sont STONE, DIRT et OAK_LOG. Tout le reste
 * (suffixes, materiaux) est evalue par le vrai code.
 */
class ZCategoryManagerTest {

    private static final List<String> DEFAULT_IDS = List.of("blocks", "weapons", "armor", "tools", "consumables", "resources", "enchanted-books", "misc");

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;
    private AuctionManager auctionManager;
    private ZRuleLoaderRegistry registry;
    private ZCategoryManager manager;

    @BeforeAll
    static void initTags() {
        BukkitMocks.ensureTagsInitialized();
        BukkitMocks.overrideBlocksTag(Material.STONE, Material.DIRT, Material.OAK_LOG);
    }

    @BeforeEach
    void setUp() {
        // Collaborateurs construits AVANT le stub qui les rend
        auctionManager = mock(AuctionManager.class);
        plugin = MockPlugins.plugin(tempDir);
        lenient().when(plugin.getAuctionManager()).thenReturn(auctionManager);

        registry = new ZRuleLoaderRegistry(plugin);
        try (MockedStatic<Bukkit> ignored = BukkitMocks.mockBukkit()) {
            registry.registerDefaultLoaders();
        }
        manager = new ZCategoryManager(plugin, registry);
    }

    // ------------------------------------------------------------------ helpers

    private void copyResource(String resource, File target) throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(resource), "ressource introuvable : " + resource)) {
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(in, target.toPath());
        }
    }

    private void writeCategories(String yaml) throws IOException {
        Files.writeString(new File(tempDir, "categories.yml").toPath(), yaml, StandardCharsets.UTF_8);
    }

    private void load() {
        try (MockedStatic<Bukkit> ignored = BukkitMocks.mockBukkit()) {
            manager.loadCategories();
        }
    }

    private void loadDefault() throws IOException {
        copyResource("/categories.yml", new File(tempDir, "categories.yml"));
        load();
    }

    private static List<String> ids(List<Category> categories) {
        return categories.stream().map(Category::getId).toList();
    }

    private static Set<String> ids(Set<Category> categories) {
        return categories.stream().map(Category::getId).collect(java.util.stream.Collectors.toSet());
    }

    private ZAuctionItem auctionItem(ItemStack... itemStacks) {
        return TestItems.auctionItem(plugin, 1, UUID.randomUUID(), "Seller", BigDecimal.TEN, MockPlugins.economy("vault"),
                System.currentTimeMillis() + 60_000L, List.of(itemStacks));
    }

    // ------------------------------------------------------------------ tests

    @Nested
    @DisplayName("loadCategories depuis le categories.yml de production")
    class DefaultFile {

        @Test
        @DisplayName("charge 8 categories dans l'ordre du fichier, misc en dernier")
        void loadsEightCategoriesInOrder() throws IOException {
            loadDefault();

            assertEquals(DEFAULT_IDS, ids(manager.getCategories()));
            assertEquals(8, manager.getCategoryCount());
            assertTrue(manager.isEnabled());
            assertEquals("Auction House", manager.getAllCategoryName());
            verify(plugin, never()).saveFile(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("misc est la categorie fallback declaree dans le fichier (pas celle par defaut)")
        void misc_isFromFile() throws IOException {
            loadDefault();

            Category misc = manager.getMiscCategory();
            assertNotNull(misc);
            assertEquals("misc", misc.getId());
            assertTrue(misc.isMiscellaneous());
            assertEquals("Miscellaneous", misc.getDisplayName());
            assertSame(misc, manager.getCategory("misc").orElseThrow());
        }

        @Test
        @DisplayName("display-name lus depuis le fichier")
        void displayNames_fromFile() throws IOException {
            loadDefault();

            assertEquals("Weapons", manager.getCategory("weapons").orElseThrow().getDisplayName());
            assertEquals("Enchanted Books", manager.getCategory("enchanted-books").orElseThrow().getDisplayName());
        }

        @Test
        @DisplayName("getCategory : insensible a la casse, vide si inconnue")
        void getCategory_caseInsensitive() throws IOException {
            loadDefault();

            assertTrue(manager.getCategory("WEAPONS").isPresent());
            assertTrue(manager.getCategory("Weapons").isPresent());
            assertTrue(manager.getCategory("nope").isEmpty());
        }

        @Test
        @DisplayName("chaque categorie non-misc porte au moins une regle valide")
        void everyRegularCategory_hasRules() throws IOException {
            loadDefault();

            for (Category category : manager.getCategories()) {
                if (category.isMiscellaneous()) continue;
                assertFalse(category.getRules().isEmpty(), category.getId());
                assertTrue(category.getRules().stream().allMatch(rule -> rule.isValid()), category.getId());
            }
        }

        @ParameterizedTest(name = "{0}/categories.yml")
        @ValueSource(strings = {"fr", "es", "it", "id", "th"})
        @DisplayName("les traductions definissent exactement les memes ids, dans le meme ordre")
        void translations_sameIds(String language) throws IOException {
            copyResource("/" + language + "/categories.yml", new File(tempDir, "categories.yml"));
            load();

            assertEquals(DEFAULT_IDS, ids(manager.getCategories()), language);
            assertEquals(8, manager.getCategoryCount(), language);
            assertTrue(manager.getMiscCategory().isMiscellaneous(), language);
            assertTrue(manager.isEnabled(), language);
        }
    }

    @Nested
    @DisplayName("getCategoryFor / getCategoriesFor / matches")
    class Matching {

        @BeforeEach
        void loadDefaults() throws IOException {
            loadDefault();
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "STONE, blocks",
                "OAK_LOG, blocks",
                "DIAMOND_SWORD, weapons",
                "WOODEN_SWORD, weapons",
                "BOW, weapons",
                "CROSSBOW, weapons",
                "TRIDENT, weapons",
                "IRON_HELMET, armor",
                "LEATHER_BOOTS, armor",
                "ELYTRA, armor",
                "SHIELD, armor",
                "TURTLE_HELMET, armor",
                "DIAMOND_PICKAXE, tools",
                "DIAMOND_AXE, tools",
                "NETHERITE_HOE, tools",
                "SHEARS, tools",
                "FISHING_ROD, tools",
                "POTION, consumables",
                "GOLDEN_APPLE, consumables",
                "COOKED_BEEF, consumables",
                "DIAMOND, resources",
                "RAW_IRON, resources",
                "ENCHANTED_BOOK, enchanted-books",
                "FEATHER, misc",
                "BOOK, misc",
                "DIAMOND_BLOCK, misc",
        })
        @DisplayName("getCategoryFor : premiere categorie qui matche, sinon misc")
        void getCategoryFor_mapping(Material material, String expectedId) {
            assertEquals(expectedId, manager.getCategoryFor(RuleTestItems.plain(material)).getId());
        }

        @Test
        @DisplayName("getCategoryFor(null) => misc")
        void getCategoryFor_null_returnsMisc() {
            assertSame(manager.getMiscCategory(), manager.getCategoryFor(null));
        }

        @Test
        @DisplayName("getCategoryFor : la meta n'empeche pas le classement par materiau")
        void getCategoryFor_ignoresMetaForMaterialRules() {
            assertEquals("weapons", manager.getCategoryFor(RuleTestItems.named(Material.DIAMOND_SWORD, "Excalibur")).getId());
        }

        @Test
        @DisplayName("getCategoriesFor : une seule categorie pour un item simple")
        void getCategoriesFor_single() {
            assertEquals(List.of("weapons"), ids(manager.getCategoriesFor(RuleTestItems.plain(Material.DIAMOND_SWORD))));
        }

        @Test
        @DisplayName("getCategoriesFor : [misc] pour un item hors categorie, et pour null")
        void getCategoriesFor_fallbackMisc() {
            assertEquals(List.of("misc"), ids(manager.getCategoriesFor(RuleTestItems.plain(Material.FEATHER))));
            assertEquals(List.of(manager.getMiscCategory()), manager.getCategoriesFor(null));
        }

        @Test
        @DisplayName("matches(item, category) : vrai/faux selon la categorie, faux si categorie null")
        void matches_itemAgainstCategory() {
            Category weapons = manager.getCategory("weapons").orElseThrow();
            Category armor = manager.getCategory("armor").orElseThrow();
            ItemStack sword = RuleTestItems.plain(Material.DIAMOND_SWORD);

            assertTrue(manager.matches(sword, weapons));
            assertFalse(manager.matches(sword, armor));
            assertFalse(manager.matches(sword, null));
        }

        @Test
        @DisplayName("matches(item, misc) : misc matche n'importe quel item, meme deja classe")
        void matches_misc_matchesEverything() {
            assertTrue(manager.matches(RuleTestItems.plain(Material.DIAMOND_SWORD), manager.getMiscCategory()));
            assertTrue(manager.matches(RuleTestItems.plain(Material.FEATHER), manager.getMiscCategory()));
        }
    }

    @Nested
    @DisplayName("fichiers custom")
    class CustomFiles {

        @Test
        @DisplayName("sans section misc : une categorie misc par defaut est creee et devient le fallback")
        void noMisc_defaultCreated() throws IOException {
            writeCategories("""
                    weapons:
                      display-name: "W"
                      rules:
                        - type: material-suffix
                          suffixes: ["_SWORD"]
                    """);
            load();

            assertEquals(List.of("weapons", "misc"), ids(manager.getCategories()));
            Category misc = manager.getMiscCategory();
            assertNotNull(misc);
            assertTrue(misc.isMiscellaneous());
            assertEquals("&8Miscellaneous", misc.getDisplayName());
            assertEquals("misc", manager.getCategoryFor(RuleTestItems.plain(Material.FEATHER)).getId());
        }

        @Test
        @DisplayName("deux categories qui se recouvrent : getCategoryFor rend la premiere, getCategoriesFor les deux dans l'ordre")
        void overlappingCategories_orderMatters() throws IOException {
            writeCategories("""
                    first:
                      rules:
                        - type: material
                          materials: [DIAMOND]
                    second:
                      rules:
                        - type: material-contains
                          patterns: [DIAMOND]
                    misc:
                      display-name: "Misc"
                    """);
            load();

            assertEquals("first", manager.getCategoryFor(RuleTestItems.plain(Material.DIAMOND)).getId());
            assertEquals(List.of("first", "second"), ids(manager.getCategoriesFor(RuleTestItems.plain(Material.DIAMOND))));
            assertEquals(List.of("second"), ids(manager.getCategoriesFor(RuleTestItems.plain(Material.DIAMOND_SWORD))));
        }

        @Test
        @DisplayName("banned-rules : l'item banni retombe dans misc")
        void bannedRules_excludeFromCategory() throws IOException {
            writeCategories("""
                    tools-example:
                      display-name: "Tools"
                      rules:
                        - type: material-suffix
                          suffixes: ["_HOE", "_PICKAXE"]
                      banned-rules:
                        - type: custom-model-data
                          values: [300]
                    misc:
                      display-name: "Misc"
                    """);
            load();

            assertEquals("tools-example", manager.getCategoryFor(RuleTestItems.plain(Material.NETHERITE_HOE)).getId());
            assertEquals("misc", manager.getCategoryFor(RuleTestItems.withModelData(Material.NETHERITE_HOE, 300)).getId());
            assertEquals("tools-example", manager.getCategoryFor(RuleTestItems.withModelData(Material.NETHERITE_HOE, 301)).getId());
            assertEquals(1, manager.getCategory("tools-example").orElseThrow().getBannedRules().size());
        }

        @Test
        @DisplayName("cle en majuscules : indexee en minuscules, mais l'id conserve la casse brute")
        void uppercaseKey_indexedLowercase() throws IOException {
            writeCategories("""
                    Weapons:
                      rules:
                        - type: material-suffix
                          suffixes: ["_SWORD"]
                    misc: {}
                    """);
            load();

            Category weapons = manager.getCategory("weapons").orElseThrow();
            assertEquals("Weapons", weapons.getId());
            assertSame(weapons, manager.getCategory("WEAPONS").orElseThrow());
        }

        @Test
        @DisplayName("display-name absent : l'id sert de nom")
        void missingDisplayName_fallsBackToId() throws IOException {
            writeCategories("""
                    weapons:
                      rules:
                        - type: material-suffix
                          suffixes: ["_SWORD"]
                    misc: {}
                    """);
            load();

            assertEquals("weapons", manager.getCategory("weapons").orElseThrow().getDisplayName());
            assertEquals("misc", manager.getMiscCategory().getDisplayName());
        }

        @Test
        @DisplayName("categorie non-misc sans regle : chargee (avec warning) mais ne matche jamais")
        void regularCategoryWithoutRules_neverMatches() throws IOException {
            writeCategories("""
                    empty:
                      display-name: "Empty"
                    misc: {}
                    """);
            load();

            Category empty = manager.getCategory("empty").orElseThrow();
            assertFalse(empty.isMiscellaneous());
            assertTrue(empty.getRules().isEmpty());
            assertEquals("misc", manager.getCategoryFor(RuleTestItems.plain(Material.STONE)).getId());
        }

        @Test
        @DisplayName("regles invalides ou de type inconnu dans une categorie : ignorees, les valides conservees")
        void invalidRules_areSkipped() throws IOException {
            writeCategories("""
                    mixed:
                      rules:
                        - type: no-such-type
                        - type: custom-model-data
                          ranges:
                            - min: 10
                              max: 1
                        - type: material
                          materials: [DIAMOND]
                    misc: {}
                    """);
            load();

            Category mixed = manager.getCategory("mixed").orElseThrow();
            assertEquals(1, mixed.getRules().size());
            assertEquals("mixed", manager.getCategoryFor(RuleTestItems.plain(Material.DIAMOND)).getId());
        }

        @Test
        @DisplayName("settings : enabled et all-category-name sont lus ; dynamic-categories / custom-items-support ne sont pas des categories")
        void settings_areRead() throws IOException {
            writeCategories("""
                    settings:
                      enabled: false
                      all-category-name: "Everything"
                    dynamic-categories:
                      foo: bar
                    custom-items-support:
                      foo: bar
                    weapons:
                      rules:
                        - type: material-suffix
                          suffixes: ["_SWORD"]
                    misc: {}
                    """);
            load();

            assertFalse(manager.isEnabled());
            assertEquals("Everything", manager.getAllCategoryName());
            assertEquals(List.of("weapons", "misc"), ids(manager.getCategories()));
        }

        @Test
        @DisplayName("dossier categories/ : les fichiers .yml sont fusionnes apres le fichier principal, les autres ignores")
        void categoriesDirectory_isMerged() throws IOException {
            writeCategories("""
                    weapons:
                      rules:
                        - type: material-suffix
                          suffixes: ["_SWORD"]
                    misc: {}
                    """);
            File directory = new File(tempDir, "categories");
            Files.createDirectories(directory.toPath());
            Files.writeString(new File(directory, "extra.yml").toPath(), """
                    gems:
                      display-name: "Gems"
                      rules:
                        - type: material
                          materials: [DIAMOND, EMERALD]
                    """, StandardCharsets.UTF_8);
            Files.writeString(new File(directory, "ignored.txt").toPath(), """
                    nope:
                      rules:
                        - type: material
                          materials: [STONE]
                    """, StandardCharsets.UTF_8);
            load();

            assertEquals(List.of("weapons", "misc", "gems"), ids(manager.getCategories()));
            assertEquals("gems", manager.getCategoryFor(RuleTestItems.plain(Material.EMERALD)).getId());
            assertTrue(manager.getCategory("nope").isEmpty());
        }

        @Test
        @DisplayName("fichier absent : saveFile demande ; sans fichier, seule la misc par defaut existe")
        void missingFile_savesDefaultAndFallsBack() {
            load();

            verify(plugin).saveFile("categories.yml", false);
            assertEquals(List.of("misc"), ids(manager.getCategories()));
            assertEquals("misc", manager.getCategoryFor(RuleTestItems.plain(Material.DIAMOND_SWORD)).getId());
        }

        @Test
        @DisplayName("rechargement : les anciennes categories disparaissent")
        void reload_replacesCategories() throws IOException {
            loadDefault();
            assertEquals(8, manager.getCategoryCount());

            writeCategoriesOverwrite("""
                    only:
                      rules:
                        - type: material
                          materials: [DIAMOND]
                    misc: {}
                    """);
            load();

            assertEquals(List.of("only", "misc"), ids(manager.getCategories()));
            assertEquals(2, manager.getCategoryCount());
            assertTrue(manager.getCategory("weapons").isEmpty());
        }

        private void writeCategoriesOverwrite(String yaml) throws IOException {
            Files.writeString(new File(tempDir, "categories.yml").toPath(), yaml, StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("applyCategories")
    class ApplyCategories {

        @BeforeEach
        void loadDefaults() throws IOException {
            loadDefault();
        }

        @Test
        @DisplayName("union des categories de chaque ItemStack de l'annonce")
        void unionOfEveryItemStack() {
            ZAuctionItem item = auctionItem(RuleTestItems.plain(Material.DIAMOND_SWORD), RuleTestItems.plain(Material.IRON_HELMET), RuleTestItems.plain(Material.WOODEN_SWORD));

            manager.applyCategories(item);

            assertEquals(Set.of("weapons", "armor"), ids(item.getCategories()));
            assertTrue(item.hasCategory("weapons"));
            assertTrue(item.hasCategory(manager.getCategory("armor").orElseThrow()));
            assertFalse(item.hasCategory("misc"));
        }

        @Test
        @DisplayName("item hors categorie => misc")
        void unmatched_getsMisc() {
            ZAuctionItem item = auctionItem(RuleTestItems.plain(Material.FEATHER));

            manager.applyCategories(item);

            assertEquals(Set.of("misc"), ids(item.getCategories()));
        }

        @Test
        @DisplayName("les categories precedentes sont effacees avant recalcul")
        void previousCategories_areCleared() {
            ZAuctionItem item = auctionItem(RuleTestItems.plain(Material.DIAMOND_SWORD));
            item.setCategories(new HashSet<>(Set.of(manager.getCategory("armor").orElseThrow())));

            manager.applyCategories(item);

            assertEquals(Set.of("weapons"), ids(item.getCategories()));
        }

        @Test
        @DisplayName("item null : aucune exception")
        void nullItem_isIgnored() {
            assertDoesNotThrow(() -> manager.applyCategories(null));
        }

        @Test
        @DisplayName("un Item qui n'est pas un AuctionItem recoit un ensemble vide")
        void nonAuctionItem_getsEmptySet() {
            Item item = mock(Item.class);
            Set<Category> existing = new HashSet<>();
            when(item.getCategories()).thenReturn(existing);

            manager.applyCategories(item);

            verify(item).setCategories(Set.of());
        }

        @Test
        @DisplayName("loadCategories reclasse les annonces LISTED connues du manager")
        void loadCategories_reappliesToListedItems() throws IOException {
            ZAuctionItem listed = auctionItem(RuleTestItems.plain(Material.ENCHANTED_BOOK));
            List<Item> listedItems = List.of(listed);
            when(auctionManager.getItems(StorageType.LISTED)).thenReturn(listedItems);

            load();

            assertEquals(Set.of("enchanted-books"), ids(listed.getCategories()));
            // Une fois par loadCategories : celui du @BeforeEach puis celui-ci
            verify(auctionManager, atLeast(2)).getItems(StorageType.LISTED);
        }
    }

    @Test
    @DisplayName("applyCategories : systeme desactive => les categories ne sont pas touchees et LISTED n'est pas parcouru")
    void applyCategories_disabled_isNoOp() throws IOException {
        writeCategories("""
                settings:
                  enabled: false
                weapons:
                  rules:
                    - type: material-suffix
                      suffixes: ["_SWORD"]
                misc: {}
                """);
        load();
        Item item = mock(Item.class);

        manager.applyCategories(item);

        verify(item, never()).setCategories(any());
        verify(item, never()).getCategories();
        verify(auctionManager, never()).getItems(StorageType.LISTED);
    }

    @Nested
    @DisplayName("getItemCountForCategory / invalidateCategoryCountCache")
    class Counting {

        @BeforeEach
        void loadDefaults() throws IOException {
            loadDefault();
        }

        @Test
        @DisplayName("'all' (quelle que soit la casse) => compteur global du manager")
        void all_returnsGlobalCount() {
            when(auctionManager.getListedItemCount()).thenReturn(7);

            assertEquals(7L, manager.getItemCountForCategory("all"));
            assertEquals(7L, manager.getItemCountForCategory("ALL"));
            verify(auctionManager, never()).getListedItemCount(any(Category.class));
        }

        @Test
        @DisplayName("categorie connue (casse ignoree) => compteur du manager pour CETTE categorie")
        void knownCategory_delegatesWithCategory() {
            Category weapons = manager.getCategory("weapons").orElseThrow();
            when(auctionManager.getListedItemCount(weapons)).thenReturn(3);

            assertEquals(3L, manager.getItemCountForCategory("weapons"));
            assertEquals(3L, manager.getItemCountForCategory("WEAPONS"));
            verify(auctionManager, never()).getListedItemCount();
        }

        @Test
        @DisplayName("categorie inconnue => 0 sans interroger le manager")
        void unknownCategory_returnsZero() {
            assertEquals(0L, manager.getItemCountForCategory("does-not-exist"));
            verify(auctionManager, never()).getListedItemCount(any(Category.class));
            verify(auctionManager, never()).getListedItemCount();
        }

        @Test
        @DisplayName("id null ou blanc => 0")
        void nullOrBlank_returnsZero() {
            assertEquals(0L, manager.getItemCountForCategory(null));
            assertEquals(0L, manager.getItemCountForCategory(""));
            assertEquals(0L, manager.getItemCountForCategory("   "));
        }

        @Test
        @DisplayName("aucune memoisation : une valeur changee cote manager est visible immediatement")
        void counts_areNotMemoized() {
            Category weapons = manager.getCategory("weapons").orElseThrow();
            when(auctionManager.getListedItemCount(weapons)).thenReturn(3);
            assertEquals(3L, manager.getItemCountForCategory("weapons"));

            when(auctionManager.getListedItemCount(weapons)).thenReturn(5);
            assertEquals(5L, manager.getItemCountForCategory("weapons"));

            assertDoesNotThrow(manager::invalidateCategoryCountCache);
            assertEquals(5L, manager.getItemCountForCategory("weapons"));
        }
    }

    @Test
    @DisplayName("getItemCountForCategory : systeme desactive => 0 meme pour 'all'")
    void getItemCountForCategory_disabled_returnsZero() throws IOException {
        writeCategories("""
                settings:
                  enabled: false
                misc: {}
                """);
        load();
        lenient().when(auctionManager.getListedItemCount()).thenReturn(9);

        assertEquals(0L, manager.getItemCountForCategory("all"));
        assertEquals(0L, manager.getItemCountForCategory("misc"));
    }
}
