package fr.maxlego08.zauctionhouse.migration;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.migration.MigrationCallback;
import fr.maxlego08.zauctionhouse.api.migration.MigrationProvider;
import fr.maxlego08.zauctionhouse.api.migration.MigrationResult;
import fr.maxlego08.zauctionhouse.migration.v3.V3MigrationProvider;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZMigrationRegistryTest {

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;
    private ZMigrationRegistry registry;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(this.tempDir);
        this.registry = new ZMigrationRegistry(this.plugin);
    }

    /**
     * Provider minimal : l'identifiant, le nom affiche et les alias sont les seules choses que le
     * registre lit. {@code matches} est la methode par defaut de l'interface, non surchargee.
     */
    private static MigrationProvider provider(String id, String displayName, String... aliases) {
        return new MigrationProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public String getDescription() {
                return displayName + " description";
            }

            @Override
            public List<String> getAliases() {
                return List.of(aliases);
            }

            @Override
            public String getDefaultSqlitePath() {
                return "plugins/" + id + "/database.db";
            }

            @Override
            public String getDefaultJsonFolder() {
                return "plugins/" + id;
            }

            @Override
            public String getDefaultTablePrefix() {
                return id + "_";
            }

            @Override
            public CompletableFuture<MigrationResult> migrate(AuctionPlugin plugin, ConfigurationSection config, MigrationCallback callback) {
                return CompletableFuture.completedFuture(MigrationResult.failure("not implemented"));
            }
        };
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("enregistre le provider et le rend accessible par son identifiant")
        void register_thenGetProviderById() {
            MigrationProvider provider = provider("crazyauctions", "CrazyAuctions");

            registry.register(provider);

            assertSame(provider, registry.getProvider("crazyauctions").orElseThrow());
            assertEquals(1, registry.getProviders().size());
            assertTrue(registry.hasProvider("crazyauctions"));
        }

        @Test
        @DisplayName("l'identifiant est normalise en minuscules a l'enregistrement")
        void register_lowercasesId() {
            registry.register(provider("CrazyAuctions", "CrazyAuctions"));

            assertTrue(registry.getProviderIds().contains("crazyauctions"));
            assertFalse(registry.getProviderIds().contains("CrazyAuctions"));
            assertTrue(registry.getProvider("CRAZYAUCTIONS").isPresent());
        }

        @Test
        @DisplayName("un provider null est refuse")
        void register_null_throws() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> registry.register(null));

            assertTrue(exception.getMessage().contains("null"));
            assertTrue(registry.getProviders().isEmpty());
        }

        @Test
        @DisplayName("un doublon d'identifiant (quelle que soit la casse) est refuse et le premier reste en place")
        void register_duplicateId_throws() {
            MigrationProvider first = provider("zahv3", "First");
            registry.register(first);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> registry.register(provider("ZAHV3", "Second")));

            assertTrue(exception.getMessage().contains("already registered"));
            assertEquals(1, registry.getProviders().size());
            assertSame(first, registry.getProvider("zahv3").orElseThrow());
        }

        @Test
        @DisplayName("plusieurs providers distincts cohabitent")
        void register_severalProviders() {
            registry.register(provider("a", "A"));
            registry.register(provider("b", "B"));
            registry.register(provider("c", "C"));

            assertEquals(3, registry.getProviders().size());
            assertTrue(registry.getProviderIds().containsAll(List.of("a", "b", "c")));
        }
    }

    @Nested
    @DisplayName("getProvider")
    class GetProvider {

        @Test
        @DisplayName("resout un alias, sans tenir compte de la casse")
        void getProvider_byAlias() {
            MigrationProvider provider = provider("zauctionhousev3", "zAuctionHouse V3", "zah", "v3");
            registry.register(provider);

            assertSame(provider, registry.getProvider("v3").orElseThrow());
            assertSame(provider, registry.getProvider("ZAH").orElseThrow());
        }

        @Test
        @DisplayName("resout le nom affiche, sans tenir compte de la casse")
        void getProvider_byDisplayName() {
            MigrationProvider provider = provider("zauctionhousev3", "zAuctionHouse V3");
            registry.register(provider);

            assertSame(provider, registry.getProvider("zauctionhouse v3").orElseThrow());
        }

        @Test
        @DisplayName("l'identifiant direct l'emporte sur l'alias d'un autre provider")
        void getProvider_directIdBeatsAlias() {
            MigrationProvider aliased = provider("other", "Other", "zah");
            MigrationProvider direct = provider("zah", "Zah");
            registry.register(aliased);
            registry.register(direct);

            assertSame(direct, registry.getProvider("zah").orElseThrow());
        }

        @Test
        @DisplayName("rend vide pour un identifiant inconnu ou null")
        void getProvider_unknownOrNull_empty() {
            registry.register(provider("zah", "Zah"));

            assertEquals(Optional.empty(), registry.getProvider("nope"));
            assertEquals(Optional.empty(), registry.getProvider(null));
            assertFalse(registry.hasProvider("nope"));
        }

        @Test
        @DisplayName("le vrai provider V3 est resolu par chacun de ses alias")
        void getProvider_realV3Provider() {
            V3MigrationProvider provider = new V3MigrationProvider();
            registry.register(provider);

            for (String alias : List.of("zauctionhousev3", "zah", "zahv3", "v3", "zauctionhouse", "zAuctionHouse V3")) {
                assertSame(provider, registry.getProvider(alias).orElseThrow(), alias);
            }
        }
    }

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("retire le provider, journalise et rend true")
        void unregister_existing_true() {
            registry.register(provider("zah", "Zah Display"));

            try (LogCapture logs = LogCapture.of(plugin.getLogger())) {
                assertTrue(registry.unregister("ZAH"));
                assertTrue(logs.contains(Level.INFO, "Unregistered migration provider: Zah Display"));
            }
            assertTrue(registry.getProviders().isEmpty());
            assertFalse(registry.hasProvider("zah"));
        }

        @Test
        @DisplayName("rend false pour un identifiant inconnu ou null")
        void unregister_unknownOrNull_false() {
            registry.register(provider("zah", "Zah"));

            assertFalse(registry.unregister("nope"));
            assertFalse(registry.unregister(null));
            assertEquals(1, registry.getProviders().size());
        }

        @Test
        @DisplayName("un alias ne suffit pas pour desenregistrer : seul l'identifiant compte")
        void unregister_byAlias_false() {
            registry.register(provider("zah", "Zah", "v3"));

            assertFalse(registry.unregister("v3"));
            assertTrue(registry.hasProvider("zah"));
        }

        @Test
        @DisplayName("apres desenregistrement, le meme identifiant peut etre reenregistre")
        void unregister_thenRegisterAgain() {
            registry.register(provider("zah", "Zah"));
            registry.unregister("zah");

            MigrationProvider replacement = provider("zah", "Zah 2");
            registry.register(replacement);

            assertSame(replacement, registry.getProvider("zah").orElseThrow());
        }
    }

    @Nested
    @DisplayName("vues")
    class Views {

        @Test
        @DisplayName("getProviders et getProviderIds sont des vues non modifiables")
        void views_areUnmodifiable() {
            registry.register(provider("zah", "Zah"));
            Collection<MigrationProvider> providers = registry.getProviders();
            Collection<String> ids = registry.getProviderIds();

            assertThrows(UnsupportedOperationException.class, providers::clear);
            assertThrows(UnsupportedOperationException.class, ids::clear);
            assertEquals(1, registry.getProviders().size());
        }

        @Test
        @DisplayName("un registre neuf est vide")
        void newRegistry_isEmpty() {
            assertTrue(registry.getProviders().isEmpty());
            assertTrue(registry.getProviderIds().isEmpty());
            assertInstanceOf(Collection.class, registry.getProviderIds());
        }
    }
}
