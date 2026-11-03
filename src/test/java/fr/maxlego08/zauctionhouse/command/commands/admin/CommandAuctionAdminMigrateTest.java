package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.migration.ZMigrationRegistry;
import fr.maxlego08.zauctionhouse.migration.v3.V3MigrationProvider;
import fr.maxlego08.zauctionhouse.storage.ZStorageManager;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.MigrationStateRepository;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.V3SourceDatabase;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Flux de la commande {@code /ah admin migrate <source> [confirm] [force]} sur une base V4 SQLite
 * reelle : gardes synchrones (source, configuration, confirm, joueurs connectes, sentinelle) et
 * ecriture de la sentinelle d'idempotence UNIQUEMENT apres un succes.
 * <p>
 * La migration tourne sur un executor dedie : les tests attendent le message terminal
 * (MIGRATION_SUCCESS / MIGRATION_FAILED) par scrutation courte, jamais par un long sleep.
 * {@code loadItems()} est neutralise : il decoderait des ItemStack, impossible sans serveur.
 */
class CommandAuctionAdminMigrateTest extends StorageManagerTestBase {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());
    private static final String SECTION = "migration.zauctionhouse-v3";

    private CommandAuctionAdminMigrate command;
    private CommandSender sender;
    private Server server;
    private ZStorageManager spiedStorage;
    private V3SourceDatabase source;
    private List<Message> sent;

    @BeforeEach
    void setUpCommand() throws Exception {
        // Collaborateurs construits AVANT les stubs qui les rendent.
        ZMigrationRegistry registry = new ZMigrationRegistry(this.plugin);
        registry.register(new V3MigrationProvider());
        this.server = mock(Server.class);
        doReturn(List.of()).when(this.server).getOnlinePlayers();
        this.spiedStorage = spy(this.storageManager);
        doNothing().when(this.spiedStorage).loadItems();
        this.sent = new CopyOnWriteArrayList<>();
        this.sender = mock(CommandSender.class);

        lenient().when(this.plugin.getMigrationRegistry()).thenReturn(registry);
        lenient().when(this.plugin.getServer()).thenReturn(this.server);
        lenient().when(this.plugin.getStorageManager()).thenReturn(this.spiedStorage);
        doAnswer(invocation -> {
            Message message = invocation.getArgument(1);
            this.sent.add(message);
            return null;
        }).when(this.plugin).sendMessage(any(), any(Message.class), any(Object[].class));

        this.source = V3SourceDatabase.create(new File(this.dataFolder, "v3/database.db"));
        configureSqlite(this.source.path());

        this.command = new CommandAuctionAdminMigrate(this.plugin);
    }

    private void configureSqlite(String path) {
        this.plugin.getConfig().set(SECTION + ".source-type", "SQLITE");
        this.plugin.getConfig().set(SECTION + ".sqlite-path", path);
        this.plugin.getConfig().set(SECTION + ".table-prefix", V3SourceDatabase.DEFAULT_PREFIX);
    }

    private void seedSource() throws Exception {
        this.source.insert(V3SourceDatabase.item(ALICE, "Alice").itemstack("stack").storageType("STORAGE"));
        this.source.insert(V3SourceDatabase.transaction(ALICE, BOB).needMoney(true));
    }

    private CommandType run(String... args) {
        return this.command.prePerform(this.plugin, this.sender, args);
    }

    /**
     * Attend le verdict asynchrone de la migration (au plus 10 s, par pas de 20 ms).
     */
    private void awaitVerdict() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (this.sent.contains(Message.MIGRATION_SUCCESS) || this.sent.contains(Message.MIGRATION_FAILED)) return;
            Thread.sleep(20L);
        }
        fail("La migration n'a rendu aucun verdict dans le delai. Messages recus : " + this.sent);
    }

    private long countSent(Message message) {
        return this.sent.stream().filter(message::equals).count();
    }

    private long nonDeletedItems() throws Exception {
        return count(Tables.ITEMS, "storage_type != ?", StorageType.DELETED.name());
    }

    @Nested
    @DisplayName("gardes synchrones")
    class SynchronousGuards {

        @Test
        @DisplayName("source inconnue : message d'erreur + liste des sources, rien n'est lance")
        void unknownSource() throws Exception {
            seedSource();

            assertEquals(CommandType.SUCCESS, run("axauctions", "confirm"));

            assertEquals(List.of(Message.MIGRATION_INVALID_SOURCE, Message.MIGRATION_AVAILABLE_SOURCES), sent);
            assertEquals(0, count(Tables.ITEMS));
            assertTrue(migrationState().select("zauctionhousev3").isEmpty());
        }

        @Test
        @DisplayName("section de configuration absente")
        void notConfigured() throws Exception {
            plugin.getConfig().set("migration", null);

            run("v3", "confirm");

            assertEquals(List.of(Message.MIGRATION_NOT_CONFIGURED), sent);
            assertEquals(0, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("configuration invalide (fichier SQLite absent) : MIGRATION_FAILED immediat, rien n'est lance")
        void invalidConfiguration() throws Exception {
            configureSqlite(new File(dataFolder, "nope.db").getAbsolutePath());

            run("v3", "confirm");

            assertEquals(List.of(Message.MIGRATION_FAILED), sent);
            assertFalse(sent.contains(Message.MIGRATION_STARTED));
            assertEquals(0, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("sans 'confirm' : informations + demande de confirmation, aucune ecriture")
        void withoutConfirm() throws Exception {
            seedSource();

            run("zauctionhousev3");

            assertEquals(List.of(Message.MIGRATION_INFO, Message.MIGRATION_CONFIRM), sent);
            assertEquals(0, count(Tables.ITEMS));
            assertEquals(0, count(Tables.PLAYERS));
            assertTrue(migrationState().select("zauctionhousev3").isEmpty());
        }

        @Test
        @DisplayName("un mot autre que 'confirm' en deuxieme argument vaut absence de confirmation")
        void wrongConfirmWord() throws Exception {
            seedSource();

            run("v3", "yes");

            assertEquals(List.of(Message.MIGRATION_INFO, Message.MIGRATION_CONFIRM), sent);
            assertEquals(0, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("des joueurs connectes : refus, rien n'est lance")
        void onlinePlayers_refused() throws Exception {
            seedSource();
            doReturn(List.of(MockPlugins.player("Eve"))).when(server).getOnlinePlayers();

            run("v3", "confirm");

            assertEquals(List.of(Message.MIGRATION_FAILED), sent);
            assertFalse(sent.contains(Message.MIGRATION_STARTED));
            assertEquals(0, count(Tables.ITEMS));
            assertTrue(migrationState().select("zauctionhousev3").isEmpty());
        }

        @Test
        @DisplayName("une sentinelle deja posee bloque la relance tant que 'force' n'est pas donne")
        void existingSentinel_refused() throws Exception {
            seedSource();
            migrationState().markMigrated("zauctionhousev3", "other-node", 1, 1, 1);

            run("v3", "confirm");

            assertEquals(List.of(Message.MIGRATION_FAILED), sent);
            assertFalse(sent.contains(Message.MIGRATION_STARTED));
            assertEquals(0, count(Tables.ITEMS), "rien ne doit etre duplique");
            assertEquals("other-node", migrationState().select("zauctionhousev3").orElseThrow().server_name());
        }

        @Test
        @DisplayName("'CONFIRM' et 'v3' sont acceptes sans tenir compte de la casse")
        void caseInsensitiveArguments() throws Exception {
            seedSource();

            run("V3", "CONFIRM");

            awaitVerdict();
            assertTrue(sent.contains(Message.MIGRATION_STARTED));
            assertTrue(sent.contains(Message.MIGRATION_SUCCESS));
        }
    }

    @Nested
    @DisplayName("execution")
    class Execution {

        @Test
        @DisplayName("succes : la base est remplie, la sentinelle est ecrite et le catalogue recharge")
        void success_writesSentinelAndReloads() throws Exception {
            seedSource();

            assertEquals(CommandType.SUCCESS, run("zauctionhousev3", "confirm"));
            awaitVerdict();

            assertEquals(Message.MIGRATION_STARTED, sent.getFirst());
            assertTrue(sent.contains(Message.MIGRATION_PROGRESS));
            assertEquals(Message.MIGRATION_SUCCESS, sent.getLast());
            assertFalse(sent.contains(Message.MIGRATION_FAILED));

            assertEquals(1, nonDeletedItems());
            assertEquals(1, count(Tables.ITEMS, "storage_type = ?", StorageType.DELETED.name()));
            assertEquals(1, count(Tables.TRANSACTIONS));
            assertEquals(2, count(Tables.PLAYERS));

            MigrationStateRepository.MigrationStateDTO state = migrationState().select("zauctionhousev3").orElseThrow();
            assertEquals("test-server", state.server_name());
            assertEquals(2, state.players_imported());
            assertEquals(1, state.items_imported());
            assertEquals(1, state.transactions_imported());
            assertTrue(state.migrated_at() <= System.currentTimeMillis());

            verify(spiedStorage, timeout(2_000L)).loadItems();
        }

        @Test
        @DisplayName("apres un succes, la meme commande est refusee par la sentinelle : aucune duplication")
        void secondRun_refusedBySentinel() throws Exception {
            seedSource();
            run("v3", "confirm");
            awaitVerdict();
            sent.clear();

            run("v3", "confirm");

            assertEquals(List.of(Message.MIGRATION_FAILED), sent);
            assertFalse(sent.contains(Message.MIGRATION_STARTED));
            assertEquals(1, nonDeletedItems());
            assertEquals(1, count(Tables.TRANSACTIONS));
        }

        @Test
        @DisplayName("'force' passe outre la sentinelle : tout est duplique et la sentinelle est reecrite")
        void force_bypassesSentinel() throws Exception {
            seedSource();
            run("v3", "confirm");
            awaitVerdict();
            long firstStamp = migrationState().select("zauctionhousev3").orElseThrow().migrated_at();
            sent.clear();

            run("v3", "confirm", "force");
            awaitVerdict();

            assertTrue(sent.contains(Message.MIGRATION_STARTED));
            assertTrue(sent.contains(Message.MIGRATION_SUCCESS));
            assertEquals(2, nonDeletedItems(), "la relance forcee duplique les annonces");
            assertEquals(2, count(Tables.TRANSACTIONS), "la relance forcee duplique l'argent en attente");
            MigrationStateRepository.MigrationStateDTO state = migrationState().select("zauctionhousev3").orElseThrow();
            assertTrue(state.migrated_at() >= firstStamp);
            assertEquals(1, state.items_imported(), "la sentinelle porte les compteurs de la DERNIERE execution");
            assertEquals(1, count(MigrationStateRepository.TABLE_NAME));
        }

        @Test
        @DisplayName("'force' sans sentinelle prealable se comporte comme une premiere execution")
        void force_withoutSentinel() throws Exception {
            seedSource();

            run("v3", "confirm", "force");
            awaitVerdict();

            assertTrue(sent.contains(Message.MIGRATION_SUCCESS));
            assertEquals(1, nonDeletedItems());
            assertTrue(migrationState().select("zauctionhousev3").isPresent());
        }

        @Test
        @DisplayName("echec de la migration (base V3 vide) : MIGRATION_FAILED, AUCUNE sentinelle, la relance reste possible")
        void failure_doesNotWriteSentinel() throws Exception {
            // Le fichier existe (validateConfig passe) mais ne contient aucune donnee.
            run("v3", "confirm");
            awaitVerdict();

            assertEquals(Message.MIGRATION_STARTED, sent.getFirst());
            assertEquals(Message.MIGRATION_FAILED, sent.getLast());
            assertFalse(sent.contains(Message.MIGRATION_SUCCESS));
            assertTrue(migrationState().select("zauctionhousev3").isEmpty(), "une sentinelle apres echec bloquerait la reprise pour toujours");
            assertEquals(0, count(MigrationStateRepository.TABLE_NAME));
            assertEquals(0, count(Tables.ITEMS));
            verify(spiedStorage, never()).loadItems();

            // Reprise : la source est maintenant remplie, la commande repart sans 'force'.
            seedSource();
            sent.clear();
            run("v3", "confirm");
            awaitVerdict();

            assertTrue(sent.contains(Message.MIGRATION_STARTED));
            assertEquals(Message.MIGRATION_SUCCESS, sent.getLast());
            assertTrue(migrationState().select("zauctionhousev3").isPresent());
            assertEquals(1, nonDeletedItems());
        }

        @Test
        @DisplayName("la progression du service est relayee au sender")
        void progress_relayed() throws Exception {
            seedSource();

            run("v3", "confirm");
            awaitVerdict();

            assertTrue(countSent(Message.MIGRATION_PROGRESS) >= 5, "messages recus : " + sent);
        }
    }
}
