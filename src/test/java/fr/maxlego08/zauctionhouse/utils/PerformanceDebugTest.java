package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceDebugConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.PerformanceDebugConfiguration.FilterMode;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerformanceDebugTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private Configuration configuration;
    private Logger logger;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(dataFolder);
        this.configuration = plugin.getConfiguration();
        this.logger = mock(Logger.class);
        // Le logger est capture par le constructeur : il doit etre en place AVANT new PerformanceDebug.
        when(plugin.getLogger()).thenReturn(logger);
        filter(FilterMode.DISABLED);
    }

    private void filter(FilterMode mode, String... operations) {
        PerformanceDebugConfiguration debugConfiguration = new PerformanceDebugConfiguration(mode, List.of(operations));
        when(configuration.getPerformanceDebug()).thenReturn(debugConfiguration);
    }

    private PerformanceDebug debug(boolean enabled) {
        when(configuration.isEnablePerformanceDebug()).thenReturn(enabled);
        return new PerformanceDebug(plugin);
    }

    private String lastInfo() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).info(captor.capture());
        return captor.getValue();
    }

    private String lastWarning() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).warning(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Desactive")
    class Disabled {

        @Test
        @DisplayName("isEnabled reflete la configuration")
        void isEnabled_false() {
            assertFalse(debug(false).isEnabled());
        }

        @Test
        @DisplayName("measure(Runnable) execute l'operation sans journaliser")
        void measureRunnable_runsWithoutLogging() {
            AtomicInteger runs = new AtomicInteger();
            debug(false).measure("op", () -> {
                runs.incrementAndGet();
            });
            assertEquals(1, runs.get());
            verify(logger, never()).info(anyString());
            verify(logger, never()).warning(anyString());
        }

        @Test
        @DisplayName("measure(Supplier) rend le resultat sans journaliser")
        void measureSupplier_returnsResult() {
            assertEquals("value", debug(false).measure("op", () -> "value"));
            verify(logger, never()).info(anyString());
        }

        @Test
        @DisplayName("measureWithContext rend le resultat et n'evalue pas le contexte")
        void measureWithContext_skipsContext() {
            AtomicInteger contextCalls = new AtomicInteger();
            int result = debug(false).measureWithContext("op", () -> 7, () -> "ctx" + contextCalls.incrementAndGet());
            assertEquals(7, result);
            assertEquals(0, contextCalls.get());
            verify(logger, never()).info(anyString());
        }

        @Test
        @DisplayName("start rend -1 et end(-1) ne journalise rien")
        void start_returnsMinusOne_endIsNoop() {
            PerformanceDebug debug = debug(false);
            long start = debug.start();
            assertEquals(-1L, start);
            debug.end("op", start);
            debug.end("op", start, "context");
            verify(logger, never()).info(anyString());
            verify(logger, never()).warning(anyString());
        }
    }

    @Nested
    @DisplayName("Active")
    class Enabled {

        @Test
        @DisplayName("isEnabled reflete la configuration")
        void isEnabled_true() {
            assertTrue(debug(true).isEnabled());
        }

        @Test
        @DisplayName("measure(Runnable) journalise le nom de l'operation et une duree en ms")
        void measureRunnable_logsInfo() {
            AtomicInteger runs = new AtomicInteger();
            debug(true).measure("cache.rebuild", () -> {
                runs.incrementAndGet();
            });
            assertEquals(1, runs.get());
            String message = lastInfo();
            assertTrue(message.startsWith("[Performance] cache.rebuild took "), message);
            assertTrue(message.endsWith("ms"), message);
            assertFalse(message.contains("("), "pas de contexte");
        }

        @Test
        @DisplayName("measure(Supplier) rend le resultat et journalise")
        void measureSupplier_logsAndReturns() {
            int result = debug(true).measure("op", () -> 42);
            assertEquals(42, result);
            assertTrue(lastInfo().contains("op took"));
        }

        @Test
        @DisplayName("measureWithContext ajoute le contexte entre parentheses")
        void measureWithContext_appendsContext() {
            String result = debug(true).measureWithContext("op", () -> "r", () -> "items=3");
            assertEquals("r", result);
            String message = lastInfo();
            assertTrue(message.endsWith("ms (items=3)"), message);
        }

        @Test
        @DisplayName("un contexte vide n'ajoute pas de parentheses")
        void end_emptyContext_noParentheses() {
            PerformanceDebug debug = debug(true);
            debug.end("op", debug.start(), "");
            assertFalse(lastInfo().contains("("));
        }

        @Test
        @DisplayName("start rend un instant positif et end journalise la duree")
        void start_end_logs() {
            PerformanceDebug debug = debug(true);
            long start = debug.start();
            assertTrue(start > 0L);
            debug.end("op", start);
            assertTrue(lastInfo().startsWith("[Performance] op took"));
        }

        @Test
        @DisplayName("une operation de plus de 100 ms est journalisee en WARNING")
        void end_slowOperation_warns() {
            PerformanceDebug debug = debug(true);
            long fakeStart = System.nanoTime() - 250_000_000L; // 250 ms dans le passe
            debug.end("slow", fakeStart);
            String message = lastWarning();
            assertTrue(message.startsWith("[Performance] slow took "), message);
            assertTrue(message.endsWith("ms"), message);
            verify(logger, never()).info(anyString());
        }

        @Test
        @DisplayName("au-dela d'une seconde, la duree est exprimee en secondes")
        void end_verySlowOperation_formatsSeconds() {
            PerformanceDebug debug = debug(true);
            long fakeStart = System.nanoTime() - 2_500_000_000L;
            debug.end("very-slow", fakeStart, "ctx");
            String message = lastWarning();
            assertTrue(message.endsWith("s (ctx)"), message);
            assertFalse(message.endsWith("ms (ctx)"), message);
        }
    }

    @Nested
    @DisplayName("Filtres")
    class Filters {

        @Test
        @DisplayName("WHITELIST : seules les operations listees (prefixe * accepte) sont journalisees")
        void whitelist_filtersOperations() {
            filter(FilterMode.WHITELIST, "db.*", "exact");
            PerformanceDebug debug = debug(true);

            debug.measure("db.select", () -> {
            });
            debug.measure("exact", () -> {
            });
            debug.measure("cache.rebuild", () -> {
            });
            debug.measure("exact.not", () -> {
            });

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(logger, org.mockito.Mockito.times(2)).info(captor.capture());
            List<String> messages = captor.getAllValues();
            assertTrue(messages.get(0).contains("db.select"));
            assertTrue(messages.get(1).contains("exact took"));
        }

        @Test
        @DisplayName("BLACKLIST : les operations listees sont tues")
        void blacklist_mutesOperations() {
            filter(FilterMode.BLACKLIST, "item.*");
            PerformanceDebug debug = debug(true);

            debug.measure("item.BuildItemStack", () -> "x");
            debug.measure("cache.rebuild", () -> "y");

            String message = lastInfo();
            assertTrue(message.contains("cache.rebuild"));
        }

        @Test
        @DisplayName("le filtre n'est consulte qu'une fois le debug actif : desactive, aucune operation n'est journalisee")
        void filter_ignoredWhenDisabled() {
            filter(FilterMode.WHITELIST, "op");
            debug(false).measure("op", () -> {
            });
            verify(logger, never()).info(anyString());
        }
    }
}
