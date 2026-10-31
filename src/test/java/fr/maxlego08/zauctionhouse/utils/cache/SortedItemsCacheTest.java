package fr.maxlego08.zauctionhouse.utils.cache;

import com.tcoded.folialib.impl.PlatformScheduler;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.utils.IntList;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le plugin mocke porte un ordonnanceur qui execute IMMEDIATEMENT (runAsync comme
 * runLaterAsync) : les reconstructions "asynchrones" sont donc terminees au retour de l'appel,
 * ce qui rend les assertions deterministes.
 */
class SortedItemsCacheTest {

    private static final long DAY = 86_400_000L;

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private AuctionEconomy economy;
    private final List<Item> store = new ArrayList<>();
    private final AtomicInteger supplierCalls = new AtomicInteger();
    private SortedItemsCache cache;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(dataFolder);
        this.economy = MockPlugins.economy("vault");
        configurePerformance(10_000, 5_000);
        this.cache = newCache();
    }

    @AfterEach
    void tearDown() {
        if (this.cache != null) this.cache.shutdown();
    }

    private void configurePerformance(int sortThreshold, int categoryThreshold) {
        Configuration configuration = plugin.getConfiguration();
        PerformanceConfiguration performance = new PerformanceConfiguration(sortThreshold, categoryThreshold, 2, 5000L, 5000L, 3000L, 3000L, 5000L);
        when(configuration.getPerformance()).thenReturn(performance);
    }

    private SortedItemsCache newCache() {
        return new SortedItemsCache(plugin, () -> {
            supplierCalls.incrementAndGet();
            return new ArrayList<>(store);
        });
    }

    private ZAuctionItem item(int id, long price, long expiredAt) {
        return TestItems.auctionItem(plugin, id, UUID.randomUUID(), "Seller", BigDecimal.valueOf(price), economy, expiredAt, List.of(TestItems.diamond(1)));
    }

    private ZAuctionItem add(int id, long price, long expiredAt, Category... categories) {
        ZAuctionItem item = item(id, price, expiredAt);
        if (categories.length > 0) item.setCategories(Set.of(categories));
        store.add(item);
        return item;
    }

    private static Category category(String id) {
        Category category = mock(Category.class);
        lenient().when(category.getId()).thenReturn(id);
        return category;
    }

    private static List<Integer> toList(IntList list) {
        List<Integer> result = new ArrayList<>();
        for (int value : list) result.add(value);
        return result;
    }

    @Test
    @DisplayName("un cache neuf est sale et vide avant toute reconstruction")
    void newCache_isDirtyAndEmpty() {
        SortedItemsCache fresh = newCache();
        try {
            assertTrue(fresh.isDirty());
            assertEquals(0L, fresh.getLastRebuildTime());
        } finally {
            fresh.shutdown();
        }
    }

    @Test
    @DisplayName("getSortedIds declenche la premiere reconstruction et trie par prix croissant/decroissant")
    void getSortedIds_priceOrders() {
        long now = System.currentTimeMillis();
        add(1, 50, now + DAY);
        add(2, 10, now + 2 * DAY);
        add(3, 30, now + 3 * DAY);

        assertEquals(List.of(2, 3, 1), toList(cache.getSortedIds(SortItem.ASCENDING_PRICE)));
        assertEquals(List.of(1, 3, 2), toList(cache.getSortedIds(SortItem.DECREASING_PRICE)));
        assertFalse(cache.isDirty());
        assertEquals(1, supplierCalls.get(), "une seule lecture du store pour toutes les listes");
        assertTrue(cache.getLastRebuildTime() > 0L);
    }

    @Test
    @DisplayName("tri par date : ASCENDING_DATE = expiration la plus proche d'abord, DECREASING_DATE inverse")
    void getSortedIds_dateOrders() {
        long now = System.currentTimeMillis();
        add(1, 1, now + 3 * DAY);
        add(2, 1, now + DAY);
        add(3, 1, now + 2 * DAY);

        assertEquals(List.of(2, 3, 1), toList(cache.getSortedIds(SortItem.ASCENDING_DATE)));
        assertEquals(List.of(1, 3, 2), toList(cache.getSortedIds(SortItem.DECREASING_DATE)));
    }

    @Test
    @DisplayName("les items expires ou non AVAILABLE sont exclus de toutes les listes")
    void rebuild_excludesExpiredAndNonAvailable() {
        long now = System.currentTimeMillis();
        add(1, 1, now + DAY);
        add(2, 1, now - 1_000L); // expire
        ZAuctionItem purchased = add(3, 1, now + DAY);
        purchased.setStatus(ItemStatus.PURCHASED);
        ZAuctionItem beingPurchased = add(4, 1, now + DAY);
        beingPurchased.setStatus(ItemStatus.IS_BEING_PURCHASED);

        for (SortItem sortItem : SortItem.values()) {
            assertEquals(List.of(1), toList(cache.getSortedIds(sortItem)), sortItem.name());
            assertEquals(1, cache.getTotalCount(sortItem));
        }
    }

    @Test
    @DisplayName("getSortedIds rend une copie : modifier le resultat n'altere pas le cache")
    void getSortedIds_returnsDefensiveCopy() {
        add(1, 1, System.currentTimeMillis() + DAY);

        IntList first = cache.getSortedIds(SortItem.ASCENDING_PRICE);
        first.add(999);
        IntList second = cache.getSortedIds(SortItem.ASCENDING_PRICE);

        assertNotSame(first, second);
        assertEquals(List.of(1), toList(second));
    }

    @Test
    @DisplayName("store vide : listes vides, compteur a zero, cache propre")
    void rebuild_emptyStore() {
        assertTrue(cache.getSortedIds(SortItem.DECREASING_DATE).isEmpty());
        assertEquals(0, cache.getTotalCount(SortItem.DECREASING_DATE));
        assertFalse(cache.isDirty());
        assertTrue(cache.getLastRebuildTime() > 0L);
    }

    @Nested
    @DisplayName("Categories")
    class Categories {

        @Test
        @DisplayName("getSortedIds(category) ne contient que les items de la categorie, tries")
        void getSortedIds_byCategory() {
            long now = System.currentTimeMillis();
            Category weapons = category("weapons");
            Category blocks = category("blocks");
            add(1, 30, now + DAY, weapons);
            add(2, 10, now + DAY, blocks);
            add(3, 20, now + DAY, weapons, blocks);
            add(4, 5, now + DAY); // aucune categorie

            assertEquals(List.of(3, 1), toList(cache.getSortedIds(weapons, SortItem.ASCENDING_PRICE)));
            assertEquals(List.of(1, 3), toList(cache.getSortedIds(weapons, SortItem.DECREASING_PRICE)));
            assertEquals(List.of(2, 3), toList(cache.getSortedIds(blocks, SortItem.ASCENDING_PRICE)));
            assertEquals(2, cache.getTotalCount(weapons, SortItem.ASCENDING_DATE));
            assertEquals(4, cache.getTotalCount(SortItem.ASCENDING_DATE));
        }

        @Test
        @DisplayName("categorie inconnue : liste vide et compteur a zero")
        void getSortedIds_unknownCategory_isEmpty() {
            add(1, 1, System.currentTimeMillis() + DAY, category("weapons"));
            Category unknown = category("nope");
            assertTrue(cache.getSortedIds(unknown, SortItem.ASCENDING_PRICE).isEmpty());
            assertEquals(0, cache.getTotalCount(unknown, SortItem.ASCENDING_PRICE));
        }

        @Test
        @DisplayName("categorie null : retombe sur la liste globale")
        void getSortedIds_nullCategory_fallsBackToAll() {
            long now = System.currentTimeMillis();
            add(1, 2, now + DAY, category("weapons"));
            add(2, 1, now + DAY);
            assertEquals(List.of(2, 1), toList(cache.getSortedIds(null, SortItem.ASCENDING_PRICE)));
            assertEquals(2, cache.getTotalCount(null, SortItem.ASCENDING_PRICE));
            assertEquals(List.of(2), toList(cache.getPage(null, SortItem.ASCENDING_PRICE, 0, 1)));
        }

        @Test
        @DisplayName("seuils de parallelisme bas : plus de deux categories passent par le ForkJoinPool prive")
        void rebuild_parallelCategoryPath() {
            configurePerformance(1, 1);
            cache.shutdown();
            cache = newCache();
            long now = System.currentTimeMillis();
            Category a = category("a");
            Category b = category("b");
            Category c = category("c");
            add(1, 3, now + DAY, a);
            add(2, 2, now + DAY, b);
            add(3, 1, now + DAY, c);
            add(4, 4, now + DAY, a, b, c);

            assertEquals(List.of(3, 2, 1, 4), toList(cache.getSortedIds(SortItem.ASCENDING_PRICE)));
            assertEquals(List.of(1, 4), toList(cache.getSortedIds(a, SortItem.ASCENDING_PRICE)));
            assertEquals(List.of(4, 2), toList(cache.getSortedIds(b, SortItem.DECREASING_PRICE)));
            assertEquals(List.of(3, 4), toList(cache.getSortedIds(c, SortItem.ASCENDING_DATE)));
        }

        @Test
        @DisplayName("pool arrete : le tri retombe en sequentiel sans erreur")
        void rebuild_afterShutdown_fallsBackToSequentialSort() {
            configurePerformance(1, 1);
            cache.shutdown();
            cache = newCache();
            long now = System.currentTimeMillis();
            add(1, 3, now + DAY, category("a"));
            add(2, 1, now + DAY, category("b"));
            add(3, 2, now + DAY, category("c"));

            cache.shutdown();
            cache.rebuild();

            assertEquals(List.of(2, 3, 1), toList(cache.getSortedIds(SortItem.ASCENDING_PRICE)));
            assertFalse(cache.isDirty());
        }
    }

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @BeforeEach
        void fill() {
            long now = System.currentTimeMillis();
            for (int id = 1; id <= 5; id++) add(id, id, now + DAY);
        }

        @Test
        @DisplayName("getPage decoupe la liste triee selon offset et limit")
        void getPage_basic() {
            assertEquals(List.of(1, 2), toList(cache.getPage(SortItem.ASCENDING_PRICE, 0, 2)));
            assertEquals(List.of(3, 4), toList(cache.getPage(SortItem.ASCENDING_PRICE, 2, 2)));
            assertEquals(List.of(5), toList(cache.getPage(SortItem.ASCENDING_PRICE, 4, 2)), "derniere page tronquee");
            assertEquals(List.of(5, 4, 3, 2, 1), toList(cache.getPage(SortItem.DECREASING_PRICE, 0, 100)));
        }

        @Test
        @DisplayName("offset au-dela de la taille : page vide")
        void getPage_offsetBeyondSize_isEmpty() {
            assertTrue(cache.getPage(SortItem.ASCENDING_PRICE, 5, 2).isEmpty());
            assertTrue(cache.getPage(SortItem.ASCENDING_PRICE, 50, 2).isEmpty());
        }

        @Test
        @DisplayName("limit 0 : page vide")
        void getPage_zeroLimit_isEmpty() {
            assertTrue(cache.getPage(SortItem.ASCENDING_PRICE, 0, 0).isEmpty());
        }

        @Test
        @Disabled("BUG: getSubList borne offset a 0 mais pas toIndex=offset+limit, new IntArrayList(negatif) leve IllegalArgumentException pour un offset negatif, SortedItemsCache.java:643-646")
        @DisplayName("offset negatif : traite comme 0")
        void getPage_negativeOffset_isClampedToZero() {
            assertEquals(List.of(1, 2), toList(cache.getPage(SortItem.ASCENDING_PRICE, -5, 2)));
        }

        @Test
        @DisplayName("getPage par categorie")
        void getPage_byCategory() {
            Category weapons = category("weapons");
            store.clear();
            long now = System.currentTimeMillis();
            for (int id = 1; id <= 4; id++) add(id, id, now + DAY, weapons);
            add(5, 0, now + DAY);
            cache.rebuild();

            assertEquals(List.of(1, 2), toList(cache.getPage(weapons, SortItem.ASCENDING_PRICE, 0, 2)));
            assertEquals(List.of(3, 4), toList(cache.getPage(weapons, SortItem.ASCENDING_PRICE, 2, 5)));
            assertTrue(cache.getPage(weapons, SortItem.ASCENDING_PRICE, 4, 2).isEmpty());
        }
    }

    @Nested
    @DisplayName("Invalidation et coalescence")
    class Invalidation {

        @Test
        @DisplayName("invalidate marque le cache sale sans relire le store")
        void invalidate_marksDirtyWithoutRebuild() {
            add(1, 1, System.currentTimeMillis() + DAY);
            cache.getSortedIds(SortItem.ASCENDING_PRICE);
            int callsAfterFirstBuild = supplierCalls.get();

            cache.invalidate();

            assertTrue(cache.isDirty());
            assertEquals(callsAfterFirstBuild, supplierCalls.get());
        }

        @Test
        @DisplayName("pendant la fenetre de coalescence, une lecture rend l'instantane perime et laisse le cache sale")
        void read_duringCooldown_returnsStaleSnapshot() {
            add(1, 1, System.currentTimeMillis() + DAY);
            cache.getSortedIds(SortItem.ASCENDING_PRICE);
            add(2, 2, System.currentTimeMillis() + DAY);
            cache.invalidate();

            // La reconstruction precedente vient de se terminer : on est dans les 250 ms.
            assertEquals(List.of(1), toList(cache.getSortedIds(SortItem.ASCENDING_PRICE)));
            assertEquals(1, cache.getTotalCount(SortItem.ASCENDING_PRICE));
            assertTrue(cache.isDirty());
        }

        @Test
        @DisplayName("rebuild() force une reconstruction synchrone meme sans invalidate prealable")
        void rebuild_forcesSynchronousRebuild() {
            ZAuctionItem item = add(1, 1, System.currentTimeMillis() + DAY);
            cache.getSortedIds(SortItem.ASCENDING_PRICE);
            item.setStatus(ItemStatus.PURCHASED); // mutation en place, sans invalidate()

            cache.rebuild();

            assertTrue(cache.getSortedIds(SortItem.ASCENDING_PRICE).isEmpty());
            assertFalse(cache.isDirty());
        }

        @Test
        @DisplayName("ensureCacheValidAsync sur un cache propre rend un future deja complete sans relire le store")
        void ensureCacheValidAsync_clean_isImmediate() {
            cache.rebuild();
            int calls = supplierCalls.get();

            CompletableFuture<Void> future = cache.ensureCacheValidAsync();

            assertTrue(future.isDone());
            assertEquals(calls, supplierCalls.get());
        }

        @Test
        @DisplayName("ensureCacheValidAsync planifie la reconstruction meme pendant la fenetre de coalescence")
        void ensureCacheValidAsync_duringCooldown_schedulesRebuild() {
            add(1, 1, System.currentTimeMillis() + DAY);
            cache.rebuild();
            add(2, 2, System.currentTimeMillis() + DAY);
            cache.invalidate();

            CompletableFuture<Void> future = cache.ensureCacheValidAsync();

            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
            assertFalse(cache.isDirty());
            assertEquals(List.of(1, 2), toList(cache.getSortedIds(SortItem.ASCENDING_PRICE)));
        }

        @Test
        @DisplayName("rebuildAsync reconstruit via l'ordonnanceur et nettoie le cache")
        void rebuildAsync_rebuilds() {
            add(1, 1, System.currentTimeMillis() + DAY);
            cache.rebuildAsync();
            assertFalse(cache.isDirty());
            assertEquals(List.of(1), toList(cache.getSortedIds(SortItem.DECREASING_DATE)));
        }

        @Test
        @DisplayName("ordonnanceur indisponible : le future est complete, un avertissement est journalise, le cache reste sale")
        void requestRebuild_schedulerFailure_completesFuture() {
            PlatformScheduler broken = mock(PlatformScheduler.class, invocation -> {
                throw new IllegalStateException("scheduler down");
            });
            java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
            when(plugin.getScheduler()).thenReturn(broken);
            when(plugin.getLogger()).thenReturn(logger);
            add(1, 1, System.currentTimeMillis() + DAY);

            CompletableFuture<Void> future = cache.ensureCacheValidAsync();

            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
            assertTrue(cache.isDirty());
            verify(logger).warning(anyString());

            // Une nouvelle demande doit pouvoir etre replanifiee (la place a ete liberee).
            CompletableFuture<Void> second = cache.ensureCacheValidAsync();
            assertTrue(second.isDone());
        }

        @Test
        @DisplayName("un store qui leve pendant la reconstruction complete le future en erreur et libere la place")
        void rebuild_supplierThrows_completesExceptionally() {
            SortedItemsCache failing = new SortedItemsCache(plugin, () -> {
                throw new IllegalStateException("boom");
            });
            try {
                CompletableFuture<Void> future = failing.ensureCacheValidAsync();
                assertTrue(future.isCompletedExceptionally());
                assertTrue(failing.isDirty());
                // La place est liberee : une seconde demande cree un nouveau future.
                CompletableFuture<Void> second = failing.ensureCacheValidAsync();
                assertNotSame(future, second);
            } finally {
                failing.shutdown();
            }
        }
    }

    @Test
    @DisplayName("shutdown est idempotent")
    void shutdown_isIdempotent() {
        cache.shutdown();
        cache.shutdown();
    }

    @Test
    @DisplayName("le supplier recoit une collection quelconque : un Set est accepte")
    void rebuild_acceptsAnyCollection() {
        long now = System.currentTimeMillis();
        Collection<Item> set = Set.of(item(1, 2, now + DAY), item(2, 1, now + DAY));
        SortedItemsCache setCache = new SortedItemsCache(plugin, () -> set);
        try {
            assertEquals(List.of(2, 1), toList(setCache.getSortedIds(SortItem.ASCENDING_PRICE)));
        } finally {
            setCache.shutdown();
        }
    }
}
