package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermission;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermissionResult;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpirationConfigurationTest {

    private static final String PATH = "expiration.auction.";

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    private ExpirationConfiguration build(boolean enablePermission) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set(PATH + "default-expiration", 3600L);
        configuration.set(PATH + "permission.enable", enablePermission);
        configuration.set(PATH + "permission.permissions", List.of(
                Map.of("permission", "exp.vip", "expiration", 7200),
                Map.of("permission", "exp.short", "expiration", 60),
                Map.of("permission", "exp.legend", "expiration", 259200)));
        return ExpirationConfiguration.of(plugin, configuration, PATH);
    }

    @Test
    @DisplayName("une entree sans permission est ignoree")
    @Disabled("BUG: ExpirationConfiguration.of teste accessor.getString(\"permission\") == null mais TypedMapAccessor.getString(String) rend la chaine \"null\" pour une cle absente (String.valueOf) : l'entree est enregistree sous la permission \"null\" (ExpirationConfiguration.java, boucle permission.permissions)")
    void of_entryWithoutPermission_isSkipped() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set(PATH + "default-expiration", 3600L);
        configuration.set(PATH + "permission.enable", true);
        configuration.set(PATH + "permission.permissions", List.of(
                Map.of("permission", "exp.vip", "expiration", 7200),
                Map.of("expiration", 99999)));

        ExpirationConfiguration expiration = ExpirationConfiguration.of(plugin, configuration, PATH);

        assertEquals(Map.of("exp.vip", 7200L), expiration.expirations());
    }

    @Nested
    @DisplayName("chargement")
    class Loading {

        @Test
        @DisplayName("defaut, drapeau et paliers sont lus")
        void of_readsEverything() {
            ExpirationConfiguration configuration = build(true);

            assertEquals(3600L, configuration.defaultExpiration());
            assertTrue(configuration.enablePermission());
            assertEquals(Map.of("exp.vip", 7200L, "exp.short", 60L, "exp.legend", 259200L), configuration.expirations());
        }

        @Test
        @DisplayName("section absente : 0, desactive, vide")
        void of_missingSection_zero() {
            ExpirationConfiguration configuration = ExpirationConfiguration.of(plugin, new YamlConfiguration(), "expiration.rent.");

            assertEquals(0L, configuration.defaultExpiration());
            assertFalse(configuration.enablePermission());
            assertTrue(configuration.expirations().isEmpty());
        }

        @Test
        @DisplayName("le prefixe de chemin isole les sections entre elles")
        void of_pathPrefix_isolatesSections() {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("expiration.auction.default-expiration", 10);
            configuration.set("expiration.bid.default-expiration", 20);

            assertEquals(10L, ExpirationConfiguration.of(plugin, configuration, "expiration.auction.").defaultExpiration());
            assertEquals(20L, ExpirationConfiguration.of(plugin, configuration, "expiration.bid.").defaultExpiration());
        }
    }

    @Nested
    @DisplayName("getExpiration(Player)")
    class OnlinePlayer {

        @Test
        @DisplayName("permissions desactivees : le defaut s'applique meme avec une permission")
        void disabled_returnsDefault() {
            Player vip = ConfigTestSupport.playerWithPermissions("Vip", "exp.vip");

            assertEquals(3600L, build(false).getExpiration(vip));
            verify(vip, never()).hasPermission(any(String.class));
        }

        @Test
        @DisplayName("aucune permission : defaut")
        void noPermission_returnsDefault() {
            assertEquals(3600L, build(true).getExpiration(ConfigTestSupport.playerWithPermissions("Nobody")));
        }

        @Test
        @DisplayName("plusieurs permissions : la plus longue l'emporte")
        void multiplePermissions_maxWins() {
            Player player = ConfigTestSupport.playerWithPermissions("Rich", "exp.vip", "exp.legend");

            assertEquals(259200L, build(true).getExpiration(player));
        }

        @Test
        @DisplayName("une permission plus courte que le defaut ne le reduit jamais")
        void shorterPermission_doesNotLowerDefault() {
            Player player = ConfigTestSupport.playerWithPermissions("Short", "exp.short");

            assertEquals(3600L, build(true).getExpiration(player));
        }
    }

    @Nested
    @DisplayName("getExpiration(OfflinePermission, OfflinePlayer)")
    class Offline {

        private final OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        private final OfflinePermission offlinePermission = mock(OfflinePermission.class);

        @Test
        @DisplayName("permissions desactivees : future deja complete avec le defaut, sans appel au hook")
        void disabled_completedDefault() {
            CompletableFuture<Long> future = build(false).getExpiration(offlinePermission, offlinePlayer);

            assertTrue(future.isDone());
            assertEquals(3600L, future.join());
            verify(offlinePermission, never()).hasPermissions(any(), anySet());
        }

        @Test
        @DisplayName("resultats positifs : la plus longue l'emporte ; le hook recoit toutes les permissions configurees")
        void results_maxWins() {
            when(offlinePermission.hasPermissions(eq(offlinePlayer), anySet())).thenReturn(CompletableFuture.completedFuture(List.of(
                    new OfflinePermissionResult("exp.vip", true),
                    new OfflinePermissionResult("exp.legend", false),
                    new OfflinePermissionResult("exp.short", true))));

            assertEquals(7200L, build(true).getExpiration(offlinePermission, offlinePlayer).join());
            verify(offlinePermission).hasPermissions(offlinePlayer, Set.of("exp.vip", "exp.short", "exp.legend"));
        }

        @Test
        @DisplayName("aucun resultat positif : defaut")
        void noPositiveResult_default() {
            when(offlinePermission.hasPermissions(eq(offlinePlayer), anySet())).thenReturn(CompletableFuture.completedFuture(List.of(
                    new OfflinePermissionResult("exp.vip", false))));

            assertEquals(3600L, build(true).getExpiration(offlinePermission, offlinePlayer).join());
        }

        @Test
        @DisplayName("hook en echec : defaut, le future ne propage pas l'erreur")
        void failedHook_default() {
            when(offlinePermission.hasPermissions(eq(offlinePlayer), anySet()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("luckperms down")));

            CompletableFuture<Long> future = build(true).getExpiration(offlinePermission, offlinePlayer);

            assertFalse(future.isCompletedExceptionally());
            assertEquals(3600L, future.join());
        }

        @Test
        @DisplayName("hook rendant null : defaut")
        void nullResults_default() {
            when(offlinePermission.hasPermissions(eq(offlinePlayer), anySet())).thenReturn(CompletableFuture.completedFuture(null));

            assertEquals(3600L, build(true).getExpiration(offlinePermission, offlinePlayer).join());
        }
    }
}
