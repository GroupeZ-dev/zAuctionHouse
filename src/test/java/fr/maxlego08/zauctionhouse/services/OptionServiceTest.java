package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de {@link OptionService} : cache en memoire + table {@code options} reelle (SQLite via un
 * vrai {@code ZStorageManager}). Chaque ecriture est verifiee en SQL brut, pas seulement via le
 * cache.
 */
class OptionServiceTest extends StorageManagerTestBase {

    private OptionService service;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUpService() {
        this.service = new OptionService(this.plugin);
        this.alice = newPlayer("Alice");
        this.bob = newPlayer("Bob");
    }

    // ------------------------------------------------------------------------------------------
    // Aides SQL
    // ------------------------------------------------------------------------------------------

    private String storedValue(UUID uniqueId, PlayerOption option) throws Exception {
        return queryString("SELECT option_value FROM " + Tables.OPTIONS + " WHERE player_unique_id = ? AND option_name = ?",
                uniqueId.toString(), option.getKey());
    }

    private long rowsOf(UUID uniqueId) throws Exception {
        return count(Tables.OPTIONS, "player_unique_id = ?", uniqueId.toString());
    }

    private void insertRaw(UUID uniqueId, String optionName, String value) throws Exception {
        execute("INSERT INTO " + Tables.OPTIONS + " (player_unique_id, option_name, option_value) VALUES (?, ?, ?)",
                uniqueId.toString(), optionName, value);
    }

    // ------------------------------------------------------------------------------------------
    // Valeurs par defaut
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("valeurs par defaut")
    class Defaults {

        @ParameterizedTest(name = "{0} vaut sa valeur par defaut pour un joueur jamais charge")
        @EnumSource(PlayerOption.class)
        void getOption_unknownPlayer_returnsDefault(PlayerOption option) {
            boolean expected = Boolean.parseBoolean(option.getDefaultValue());
            assertEquals(expected, service.getOption(alice, option));
        }

        @Test
        @DisplayName("getOption(Player) delegue sur l'UUID du joueur")
        void getOption_withPlayer_usesUniqueId() {
            Player player = MockPlugins.player(alice, "Alice");
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);

            assertFalse(service.getOption(player, PlayerOption.BROADCAST_SELL));
            assertTrue(service.getOption(player, PlayerOption.BROADCAST_PURCHASE));
        }

        @Test
        @DisplayName("getPlayerOptions d'un joueur jamais charge rend toutes les options a leur valeur par defaut")
        void getPlayerOptions_unknownPlayer_returnsAllDefaults() {
            Map<PlayerOption, String> options = service.getPlayerOptions(alice);

            assertEquals(PlayerOption.values().length, options.size());
            for (PlayerOption option : PlayerOption.values()) {
                assertEquals(option.getDefaultValue(), options.get(option));
            }
        }

        @Test
        @DisplayName("getPlayerOptions rend une copie : la modifier n'altere pas le cache")
        void getPlayerOptions_returnsDefensiveCopy() {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);

            Map<PlayerOption, String> options = service.getPlayerOptions(alice);
            options.put(PlayerOption.BROADCAST_SELL, "true");
            options.put(PlayerOption.BROADCAST_PURCHASE, "false");

            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_PURCHASE));
        }
    }

    // ------------------------------------------------------------------------------------------
    // loadPlayerOptions
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("loadPlayerOptions")
    class Load {

        @Test
        @DisplayName("charge les lignes du joueur depuis la base et alimente le cache")
        void loadPlayerOptions_readsRowsIntoCache() throws Exception {
            options().upsertOption(alice, PlayerOption.BROADCAST_SELL.getKey(), "false");
            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL), "pas encore charge : valeur par defaut");

            service.loadPlayerOptions(alice).join();

            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_PURCHASE), "non stockee : valeur par defaut");
            assertEquals("false", service.getPlayerOptions(alice).get(PlayerOption.BROADCAST_SELL));
        }

        @Test
        @DisplayName("la cle est resolue sans tenir compte de la casse")
        void loadPlayerOptions_keyIsCaseInsensitive() throws Exception {
            insertRaw(alice, "BROADCAST_PURCHASE", "false");

            service.loadPlayerOptions(alice).join();

            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_PURCHASE));
        }

        @Test
        @DisplayName("une cle inconnue en base est ignoree sans erreur")
        void loadPlayerOptions_unknownKey_ignored() throws Exception {
            insertRaw(alice, "legacy_option", "false");
            options().upsertOption(alice, PlayerOption.BROADCAST_SELL.getKey(), "false");

            service.loadPlayerOptions(alice).join();

            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertFalse(service.getPlayerOptions(alice).containsValue("legacy_option"));
        }

        @Test
        @DisplayName("ne charge que les lignes du joueur demande")
        void loadPlayerOptions_isolatesPlayers() throws Exception {
            options().upsertOption(bob, PlayerOption.BROADCAST_SELL.getKey(), "false");

            service.loadPlayerOptions(alice).join();

            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertTrue(service.getOption(bob, PlayerOption.BROADCAST_SELL), "Bob n'est pas charge : valeur par defaut");
        }

        @Test
        @DisplayName("sans ligne en base : les options restent a leur valeur par defaut")
        void loadPlayerOptions_noRows_keepsDefaults() {
            service.loadPlayerOptions(alice).join();

            for (PlayerOption option : PlayerOption.values()) {
                assertEquals(Boolean.parseBoolean(option.getDefaultValue()), service.getOption(alice, option));
            }
        }

        @Test
        @DisplayName("un rechargement remplace la vue en cache par l'etat de la base")
        void loadPlayerOptions_reloadReplacesCache() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);
            // Suppression de la ligne par un autre serveur, hors de ce cache.
            execute("DELETE FROM " + Tables.OPTIONS + " WHERE player_unique_id = ?", alice.toString());

            service.loadPlayerOptions(alice).join();

            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL));
        }
    }

    // ------------------------------------------------------------------------------------------
    // setOption / setOptionAsync
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("setOption")
    class SetOption {

        @Test
        @DisplayName("une valeur non par defaut est persistee et mise en cache")
        void setOption_nonDefault_persistsAndCaches() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);

            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertEquals("false", storedValue(alice, PlayerOption.BROADCAST_SELL));
            assertEquals(1, rowsOf(alice));
        }

        @Test
        @DisplayName("deux ecritures successives ne creent qu'une seule ligne (upsert)")
        void setOption_twice_singleRow() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);

            assertEquals(1, rowsOf(alice));
            assertEquals(1, count(Tables.OPTIONS));
        }

        @Test
        @DisplayName("revenir a la valeur par defaut supprime la ligne et l'entree du cache")
        void setOption_backToDefault_deletesRow() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);
            service.setOption(alice, PlayerOption.BROADCAST_PURCHASE, false);

            service.setOption(alice, PlayerOption.BROADCAST_SELL, true);

            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertNull(storedValue(alice, PlayerOption.BROADCAST_SELL));
            assertEquals("false", storedValue(alice, PlayerOption.BROADCAST_PURCHASE), "l'autre option n'est pas touchee");
            assertEquals(1, rowsOf(alice));
        }

        @Test
        @DisplayName("ecrire la valeur par defaut sur un joueur sans cache ni ligne est sans effet")
        void setOption_defaultOnUnknownPlayer_noop() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, true);

            assertEquals(0, count(Tables.OPTIONS));
            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL));
        }

        @Test
        @DisplayName("les options sont isolees par joueur")
        void setOption_isolatesPlayers() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);

            assertTrue(service.getOption(bob, PlayerOption.BROADCAST_SELL));
            assertEquals(0, rowsOf(bob));
            assertEquals(1, rowsOf(alice));
        }

        @Test
        @DisplayName("la valeur persistee survit a un redemarrage du stockage et se recharge")
        void setOption_survivesStorageRestart() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_PURCHASE, false);

            restartStorageManager();
            OptionService fresh = new OptionService(plugin);
            fresh.loadPlayerOptions(alice).join();

            assertFalse(fresh.getOption(alice, PlayerOption.BROADCAST_PURCHASE));
        }
    }

    @Nested
    @DisplayName("setOptionAsync")
    class SetOptionAsync {

        @Test
        @DisplayName("une valeur non par defaut est persistee et mise en cache, le future est complete")
        void setOptionAsync_nonDefault_persistsAndCaches() throws Exception {
            CompletableFuture<Void> future = service.setOptionAsync(alice, PlayerOption.BROADCAST_SELL, "false");

            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertEquals("false", storedValue(alice, PlayerOption.BROADCAST_SELL));
        }

        @Test
        @DisplayName("la valeur par defaut (quelle que soit sa casse) supprime la ligne")
        void setOptionAsync_defaultValueCaseInsensitive_deletesRow() throws Exception {
            service.setOptionAsync(alice, PlayerOption.BROADCAST_SELL, "false").join();

            service.setOptionAsync(alice, PlayerOption.BROADCAST_SELL, "TRUE").join();

            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertNull(storedValue(alice, PlayerOption.BROADCAST_SELL));
            assertEquals(0, rowsOf(alice));
        }

        @Test
        @DisplayName("une valeur non booleenne est stockee telle quelle et lue comme false")
        void setOptionAsync_nonBooleanValue_storedVerbatimReadAsFalse() throws Exception {
            service.setOptionAsync(alice, PlayerOption.BROADCAST_SELL, "yes").join();

            assertEquals("yes", storedValue(alice, PlayerOption.BROADCAST_SELL));
            assertEquals("yes", service.getPlayerOptions(alice).get(PlayerOption.BROADCAST_SELL));
            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_SELL));
        }

        @Test
        @DisplayName("setOptionAsync remplace la valeur d'une ligne existante")
        void setOptionAsync_overwritesExistingRow() throws Exception {
            service.setOptionAsync(alice, PlayerOption.BROADCAST_SELL, "false").join();
            service.setOptionAsync(alice, PlayerOption.BROADCAST_SELL, "no").join();

            assertEquals(1, rowsOf(alice));
            assertEquals("no", storedValue(alice, PlayerOption.BROADCAST_SELL));
        }
    }

    // ------------------------------------------------------------------------------------------
    // resetPlayerOptions / clearPlayerOptions
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("reset / clear")
    class ResetAndClear {

        @Test
        @DisplayName("resetPlayerOptions supprime toutes les lignes du joueur et vide son cache")
        void resetPlayerOptions_deletesRowsAndCache() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);
            service.setOption(alice, PlayerOption.BROADCAST_PURCHASE, false);
            service.setOption(bob, PlayerOption.BROADCAST_SELL, false);

            service.resetPlayerOptions(alice);

            assertEquals(0, rowsOf(alice));
            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL));
            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_PURCHASE));
            assertEquals(1, rowsOf(bob), "Bob n'est pas concerne");
            assertFalse(service.getOption(bob, PlayerOption.BROADCAST_SELL));
        }

        @Test
        @DisplayName("resetPlayerOptions sur un joueur sans option est sans erreur")
        void resetPlayerOptions_unknownPlayer_noop() throws Exception {
            service.resetPlayerOptions(alice);
            assertEquals(0, count(Tables.OPTIONS));
        }

        @Test
        @DisplayName("clearPlayerOptions vide le cache mais conserve les lignes en base")
        void clearPlayerOptions_dropsCacheOnly() throws Exception {
            service.setOption(alice, PlayerOption.BROADCAST_SELL, false);

            service.clearPlayerOptions(alice);

            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL), "cache vide : valeur par defaut");
            assertEquals("false", storedValue(alice, PlayerOption.BROADCAST_SELL), "la base est intacte");

            service.loadPlayerOptions(alice).join();
            assertFalse(service.getOption(alice, PlayerOption.BROADCAST_SELL), "rechargeable depuis la base");
        }

        @Test
        @DisplayName("clearPlayerOptions sur un joueur jamais charge est sans erreur")
        void clearPlayerOptions_unknownPlayer_noop() {
            service.clearPlayerOptions(alice);
            assertTrue(service.getOption(alice, PlayerOption.BROADCAST_SELL));
        }
    }
}
