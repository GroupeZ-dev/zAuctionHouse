package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.records.AutoClaimConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.event.events.stats.AuctionMoneyClaimEvent;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.result.ClaimResult;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.testutil.MessageRecorder;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ClaimService} sur une vraie base SQLite pilotee par un vrai
 * {@code ZStorageManager}.
 * <p>
 * L'identite du serveur est FORCEE par la propriete systeme lue par le service, ce qui rend le
 * prefixe des jetons de reservation previsible : on peut ainsi fabriquer des reservations
 * "abandonnees par ce serveur", "vivantes dans cette execution" ou "d'un autre serveur" et
 * verifier que seules les premieres sont recuperees.
 */
class ClaimServiceTest extends StorageManagerTestBase {

    private static final String SERVER_ID_PROPERTY = "zauctionhouse.claim.server-id";
    private static final String SERVER_ID = "zah-test-server";
    /**
     * Vingt caracteres hexadecimaux fixes : le suffixe aleatoire d'un jeton n'a aucune incidence
     * sur les comportements testes, autant le rendre deterministe.
     */
    private static final String FIXED_SUFFIX = "0123456789abcdef0123";
    private static final String TOKEN_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{6}-[0-9a-f]{20}$";

    private MessageRecorder recorder;
    private RecordingClaimService service;
    private EconomyManager economyManager;
    private UUID alice;
    private Player player;
    private String previousServerId;

    @BeforeEach
    void setUpService() {
        this.previousServerId = System.getProperty(SERVER_ID_PROPERTY);
        System.setProperty(SERVER_ID_PROPERTY, SERVER_ID);

        this.recorder = new MessageRecorder();
        this.service = new RecordingClaimService(this.plugin, this.recorder);
        this.economyManager = this.plugin.getEconomyManager();
        stubFormat(this.economyManager);
        stubDeposit(this.economy, true);
        autoClaim(false, 0, true, 0);

        this.alice = newPlayer("Alice");
        this.player = MockPlugins.player(this.alice, "Alice");
    }

    @AfterEach
    void restoreServerId() {
        if (this.previousServerId == null) {
            System.clearProperty(SERVER_ID_PROPERTY);
        } else {
            System.setProperty(SERVER_ID_PROPERTY, this.previousServerId);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------------------------------

    private void autoClaim(boolean enabled, long delayTicks, boolean notifyPending, long notifyDelayTicks) {
        AutoClaimConfiguration configuration = new AutoClaimConfiguration(enabled, delayTicks, true, notifyPending, notifyDelayTicks, "test-claim");
        lenient().when(this.plugin.getConfiguration().getAutoClaimConfiguration()).thenReturn(configuration);
    }

    private static void stubFormat(EconomyManager manager) {
        lenient().when(manager.format(any(AuctionEconomy.class), any(Number.class)))
                .thenAnswer(invocation -> invocation.getArgument(1).toString() + "$");
    }

    private static void stubDeposit(AuctionEconomy target, boolean accepted) {
        lenient().when(target.depositChecked(any(UUID.class), any(BigDecimal.class), any())).thenReturn(accepted);
    }

    /**
     * Remplace le gestionnaire d'economies du plugin par un gestionnaire ne connaissant que les
     * economies donnees.
     */
    private void useEconomies(AuctionEconomy... economies) {
        EconomyManager manager = MockPlugins.economyManager(economies);
        stubFormat(manager);
        lenient().when(this.plugin.getEconomyManager()).thenReturn(manager);
        this.economyManager = manager;
    }

    /**
     * Insere une transaction PENDING (via le repository de production) et rend son identifiant.
     */
    private int newPending(UUID owner, String economyName, String value) throws Exception {
        long expiredAt = System.currentTimeMillis() + 60_000L;
        BigDecimal amount = new BigDecimal(value);
        int itemId = items().create(owner, ItemType.AUCTION, BigDecimal.TEN, expiredAt, this.economy);
        var item = TestItems.auctionItem(this.plugin, itemId, owner, this.economy, expiredAt);
        transactions().create(item, owner, economyName, BigDecimal.ZERO, amount, amount, TransactionStatus.PENDING);
        return (int) queryLong("SELECT MAX(id) FROM " + Tables.TRANSACTIONS);
    }

    private String statusOf(int transactionId) throws Exception {
        return queryString("SELECT status FROM " + Tables.TRANSACTIONS + " WHERE id = ?", transactionId);
    }

    private String tokenOf(int transactionId) throws Exception {
        return queryString("SELECT claim_token FROM " + Tables.TRANSACTIONS + " WHERE id = ?", transactionId);
    }

    private Object reservedAtOf(int transactionId) throws Exception {
        return queryObject("SELECT claim_reserved_at FROM " + Tables.TRANSACTIONS + " WHERE id = ?", transactionId);
    }

    private long countStatus(UUID owner, TransactionStatus status) throws Exception {
        return count(Tables.TRANSACTIONS, "player_unique_id = ? AND status = ?", owner.toString(), status.name());
    }

    /**
     * Le prefixe serveur tel que {@code ClaimService.computeServerFingerprint} le derive de la
     * propriete systeme forcee par ce test.
     */
    private static String serverPrefix() {
        return UUID.nameUUIDFromBytes(SERVER_ID.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8) + "-";
    }

    /**
     * Un prefixe de meme forme mais garanti DIFFERENT : celui d'un autre serveur du cluster.
     */
    private static String otherServerPrefix() {
        String mine = serverPrefix();
        char first = mine.charAt(0) == '0' ? '1' : '0';
        return first + mine.substring(1);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "attendu " + expected + " mais obtenu " + actual);
    }

    private static BigDecimal parseFormatted(String formatted) {
        assertNotNull(formatted);
        return new BigDecimal(formatted.replace("$", ""));
    }

    private ClaimResult claim() {
        return this.service.claimMoney(this.player).join();
    }

    // ------------------------------------------------------------------------------------------
    // claimMoney : cas nominaux
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("claimMoney")
    class ClaimMoney {

        @Test
        @DisplayName("sans transaction PENDING : rien a reclamer, aucun depot, message CLAIM_NO_PENDING")
        void claimMoney_noPendingTransaction_returnsNothingToClaim() throws Exception {
            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(0.0, result.amountClaimed());
            assertNull(result.economy());
            assertEquals("No pending transactions", result.message());
            assertTrue(recorder.has(Message.CLAIM_NO_PENDING));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
            assertEquals(0, count(Tables.TRANSACTIONS));
        }

        @Test
        @DisplayName("plusieurs PENDING d'une meme economie : un seul depot egal a la somme, lignes RETRIEVED")
        void claimMoney_severalPendingSameEconomy_depositsSumAndClosesRows() throws Exception {
            int first = newPending(alice, "vault", "10");
            int second = newPending(alice, "vault", "20.50");
            int third = newPending(alice, "vault", "5");

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(35.5, result.amountClaimed(), 1e-9);
            assertSame(economy, result.economy());
            assertEquals("Money claimed successfully", result.message());

            ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
            verify(economy, times(1)).depositChecked(eq(alice), amounts.capture(), eq("test-claim"));
            assertMoney("35.50", amounts.getValue());

            assertEquals(3, countStatus(alice, TransactionStatus.RETRIEVED));
            assertEquals(0, countStatus(alice, TransactionStatus.PENDING));
            // Le jeton reste sur les lignes cloturees (trace d'audit) et il est identique pour les
            // trois lignes remportees par ce claim.
            String token = tokenOf(first);
            assertNotNull(token);
            assertTrue(token.matches(TOKEN_PATTERN), "jeton inattendu : " + token);
            assertEquals(token, tokenOf(second));
            assertEquals(token, tokenOf(third));
            assertNotNull(reservedAtOf(first));

            assertEquals(1, recorder.count(Message.CLAIM_ECONOMY_SUCCESS));
            assertEquals(1, recorder.count(Message.CLAIM_SUCCESS));
            var economyMessage = recorder.first(Message.CLAIM_ECONOMY_SUCCESS).orElseThrow();
            assertMoney("35.50", parseFormatted(economyMessage.arg("%amount%")));
            assertEquals("vault", economyMessage.arg("%economy%"));
            assertSame(player, economyMessage.sender());
        }

        @Test
        @DisplayName("le jeton de claim porte l'empreinte du serveur force par la propriete systeme")
        void claimMoney_tokenStartsWithServerFingerprint() throws Exception {
            int id = newPending(alice, "vault", "10");

            claim();

            String token = tokenOf(id);
            assertNotNull(token);
            assertEquals(36, token.length());
            assertTrue(token.startsWith(serverPrefix()), "le jeton " + token + " devrait commencer par " + serverPrefix());
        }

        @Test
        @DisplayName("plusieurs economies : un depot par economie, total cumule")
        void claimMoney_severalEconomies_oneDepositPerEconomy() throws Exception {
            AuctionEconomy points = MockPlugins.economy("points");
            stubDeposit(points, true);
            useEconomies(economy, points);
            newPending(alice, "vault", "10");
            newPending(alice, "vault", "5");
            newPending(alice, "points", "7");

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(22.0, result.amountClaimed(), 1e-9);
            assertNotNull(result.economy());

            ArgumentCaptor<BigDecimal> vaultAmount = ArgumentCaptor.forClass(BigDecimal.class);
            verify(economy, times(1)).depositChecked(eq(alice), vaultAmount.capture(), any());
            assertMoney("15", vaultAmount.getValue());
            ArgumentCaptor<BigDecimal> pointsAmount = ArgumentCaptor.forClass(BigDecimal.class);
            verify(points, times(1)).depositChecked(eq(alice), pointsAmount.capture(), any());
            assertMoney("7", pointsAmount.getValue());

            assertEquals(3, countStatus(alice, TransactionStatus.RETRIEVED));
            assertEquals(2, recorder.count(Message.CLAIM_ECONOMY_SUCCESS));
            assertEquals(1, recorder.count(Message.CLAIM_SUCCESS));
        }

        @Test
        @DisplayName("economie introuvable : lignes liberees (jeton NULL, toujours PENDING), aucun depot")
        void claimMoney_unknownEconomy_releasesRowsWithoutDeposit() throws Exception {
            int id = newPending(alice, "ghost", "10");

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(0.0, result.amountClaimed());
            assertNull(result.economy());
            assertEquals("No positive amount to claim", result.message());
            assertEquals("PENDING", statusOf(id));
            assertNull(tokenOf(id));
            assertNull(reservedAtOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
            assertFalse(recorder.has(Message.CLAIM_SUCCESS));
        }

        @Test
        @DisplayName("depot refuse par l'economie : lignes liberees, argent toujours du")
        void claimMoney_depositRefused_releasesRows() throws Exception {
            stubDeposit(economy, false);
            int id = newPending(alice, "vault", "10");

            ClaimResult result = claim();

            assertEquals(0.0, result.amountClaimed());
            assertEquals("PENDING", statusOf(id));
            assertNull(tokenOf(id));
            verify(economy, times(1)).depositChecked(eq(alice), any(BigDecimal.class), any());
            assertFalse(recorder.has(Message.CLAIM_ECONOMY_SUCCESS));
            assertFalse(recorder.has(Message.CLAIM_SUCCESS));
        }

        @Test
        @DisplayName("depot en exception : lignes liberees, le claim n'echoue pas")
        void claimMoney_depositThrows_releasesRows() throws Exception {
            when(economy.depositChecked(any(UUID.class), any(BigDecimal.class), any())).thenThrow(new IllegalStateException("provider down"));
            int id = newPending(alice, "vault", "10");

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(0.0, result.amountClaimed());
            assertEquals("PENDING", statusOf(id));
            assertNull(tokenOf(id));
        }

        @Test
        @DisplayName("economie connue + inconnue : seule la connue est payee et cloturee")
        void claimMoney_mixedKnownAndUnknownEconomy_paysOnlyKnown() throws Exception {
            int paid = newPending(alice, "vault", "10");
            int ghost = newPending(alice, "ghost", "7");

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(10.0, result.amountClaimed(), 1e-9);
            assertEquals("RETRIEVED", statusOf(paid));
            assertNotNull(tokenOf(paid));
            assertEquals("PENDING", statusOf(ghost));
            assertNull(tokenOf(ghost));

            ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
            verify(economy, times(1)).depositChecked(eq(alice), amounts.capture(), any());
            assertMoney("10", amounts.getValue());
        }

        @Test
        @DisplayName("lignes de valeur nulle ou negative : cloturees sans depot")
        void claimMoney_nonPositiveRows_closedWithoutDeposit() throws Exception {
            int zero = newPending(alice, "vault", "0");
            int negative = newPending(alice, "vault", "-4");

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(0.0, result.amountClaimed());
            assertEquals("No positive amount to claim", result.message());
            assertEquals("RETRIEVED", statusOf(zero));
            assertEquals("RETRIEVED", statusOf(negative));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("joueur deconnecte pendant le claim : lignes liberees, aucun depot")
        void claimMoney_playerOffline_releasesRows() throws Exception {
            when(player.isOnline()).thenReturn(false);
            int id = newPending(alice, "vault", "10");

            ClaimResult result = claim();

            assertEquals(0.0, result.amountClaimed());
            assertEquals("PENDING", statusOf(id));
            assertNull(tokenOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("le claim d'Alice ne touche pas les transactions de Bob")
        void claimMoney_otherPlayerRows_untouched() throws Exception {
            UUID bob = newPlayer("Bob");
            newPending(alice, "vault", "10");
            int bobRow = newPending(bob, "vault", "99");

            claim();

            assertEquals("PENDING", statusOf(bobRow));
            assertNull(tokenOf(bobRow));
            ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
            verify(economy, times(1)).depositChecked(eq(alice), amounts.capture(), any());
            assertMoney("10", amounts.getValue());
        }

        @Test
        @DisplayName("serveur en cours d'arret : echec immediat sans toucher la base")
        void claimMoney_whileShuttingDown_fails() throws Exception {
            when(plugin.isShuttingDown()).thenReturn(true);
            int id = newPending(alice, "vault", "10");

            ClaimResult result = claim();

            assertFalse(result.success());
            assertEquals("Server is shutting down", result.message());
            assertTrue(recorder.has(Message.SERVER_SHUTTING_DOWN));
            assertEquals("PENDING", statusOf(id));
            assertNull(tokenOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());

            // La garde par joueur ne doit pas rester armee apres un refus (C-022).
            when(plugin.isShuttingDown()).thenReturn(false);
            assertTrue(claim().amountClaimed() > 0);
        }

        @Test
        @DisplayName("deux claims simultanes du meme joueur sur ce serveur : le second est refuse, la garde est relachee ensuite")
        void claimMoney_concurrentClaimSamePlayer_rejectedThenReleased() throws Exception {
            DeferredExecutor executor = new DeferredExecutor();
            lenient().when(plugin.getExecutorService()).thenReturn(executor);
            newPending(alice, "vault", "10");

            CompletableFuture<ClaimResult> first = service.claimMoney(player);
            assertFalse(first.isDone(), "le premier claim doit attendre l'executor");

            ClaimResult second = service.claimMoney(player).join();
            assertFalse(second.success());
            assertEquals("A claim is already in progress", second.message());

            assertEquals(1, executor.runAll());
            assertTrue(first.isDone());
            assertEquals(10.0, first.join().amountClaimed(), 1e-9);

            CompletableFuture<ClaimResult> third = service.claimMoney(player);
            executor.runAll();
            assertEquals("No pending transactions", third.join().message());
            verify(economy, times(1)).depositChecked(eq(alice), any(BigDecimal.class), any());
        }
    }

    // ------------------------------------------------------------------------------------------
    // claimMoney : reservations concurrentes et recuperation
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("reservations")
    class Reservations {

        @Test
        @DisplayName("lignes deja reservees par un autre serveur : rien reclame, rien paye, reservation intacte")
        void claimMoney_rowsReservedByOtherServer_claimsNothing() throws Exception {
            int id = newPending(alice, "vault", "10");
            String foreign = otherServerPrefix() + "abcdef-" + FIXED_SUFFIX;
            assertEquals(1, transactions().reservePending(alice, foreign));

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(0.0, result.amountClaimed());
            assertEquals("No pending transactions", result.message());
            assertEquals(foreign, tokenOf(id));
            assertEquals("PENDING", statusOf(id));
            assertNotNull(reservedAtOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
            assertTrue(recorder.has(Message.CLAIM_NO_PENDING));
        }

        @Test
        @DisplayName("reservation abandonnee par une execution precedente de CE serveur : liberee puis payee")
        void claimMoney_abandonedReservationOfThisServer_recoveredAndPaid() throws Exception {
            int id = newPending(alice, "vault", "10");
            String abandoned = serverPrefix() + "abcdef-" + FIXED_SUFFIX;
            assertEquals(1, transactions().reservePending(alice, abandoned));

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(10.0, result.amountClaimed(), 1e-9);
            assertEquals("RETRIEVED", statusOf(id));
            String token = tokenOf(id);
            assertNotNull(token);
            assertNotEquals(abandoned, token);
            assertTrue(token.startsWith(serverPrefix()));
            ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
            verify(economy, times(1)).depositChecked(eq(alice), amounts.capture(), any());
            assertMoney("10", amounts.getValue());
        }

        @Test
        @DisplayName("la recuperation ne libere que les lignes du joueur concerne")
        void claimMoney_abandonedReservationOfOtherPlayer_untouched() throws Exception {
            UUID bob = newPlayer("Bob");
            int bobRow = newPending(bob, "vault", "50");
            String abandoned = serverPrefix() + "abcdef-" + FIXED_SUFFIX;
            assertEquals(1, transactions().reservePending(bob, abandoned));

            ClaimResult result = claim();

            assertEquals(0.0, result.amountClaimed());
            assertEquals(abandoned, tokenOf(bobRow));
            assertEquals("PENDING", statusOf(bobRow));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("reservation portant le prefixe de l'execution COURANTE : jamais liberee")
        void claimMoney_reservationOfCurrentRun_leftUntouched() throws Exception {
            // Premier claim : il revele le prefixe d'execution (serveur + run) du service.
            int first = newPending(alice, "vault", "10");
            assertEquals(10.0, claim().amountClaimed(), 1e-9);
            String runPrefix = tokenOf(first).substring(0, 16);
            assertTrue(runPrefix.matches("^[0-9a-f]{8}-[0-9a-f]{6}-$"), "prefixe d'execution inattendu : " + runPrefix);

            int second = newPending(alice, "vault", "5");
            String live = runPrefix + FIXED_SUFFIX;
            assertEquals(1, transactions().reservePending(alice, live));
            recorder.clear();

            ClaimResult result = claim();

            assertEquals(0.0, result.amountClaimed());
            assertEquals("No pending transactions", result.message());
            assertEquals(live, tokenOf(second));
            assertEquals("PENDING", statusOf(second));
            verify(economy, times(1)).depositChecked(eq(alice), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("reservation abandonnee de ce serveur ET ligne libre : la ligne libre suffit, la reservation n'est pas touchee")
        void claimMoney_freeRowAvailable_doesNotRecover() throws Exception {
            int reserved = newPending(alice, "vault", "10");
            String abandoned = serverPrefix() + "abcdef-" + FIXED_SUFFIX;
            assertEquals(1, transactions().reservePending(alice, abandoned));
            int free = newPending(alice, "vault", "3");

            ClaimResult result = claim();

            assertEquals(3.0, result.amountClaimed(), 1e-9);
            assertEquals("RETRIEVED", statusOf(free));
            // La recuperation n'intervient que si la reservation n'a rien remporte.
            assertEquals(abandoned, tokenOf(reserved));
            assertEquals("PENDING", statusOf(reserved));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Evenement post-claim
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("AuctionMoneyClaimEvent")
    class MoneyClaimEvent {

        @Test
        @DisplayName("un claim paye declenche l'evenement avec les montants reellement deposes par economie")
        void claimMoney_success_firesEventWithDepositedAmounts() throws Exception {
            AuctionEconomy points = MockPlugins.economy("points");
            stubDeposit(points, true);
            useEconomies(economy, points);
            newPending(alice, "vault", "10");
            newPending(alice, "vault", "5");
            newPending(alice, "points", "7");
            newPending(alice, "ghost", "100");

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

                ClaimResult result = claim();
                assertEquals(22.0, result.amountClaimed(), 1e-9);

                ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
                verify(pluginManager, times(1)).callEvent(captor.capture());
                AuctionMoneyClaimEvent event = assertInstanceOf(AuctionMoneyClaimEvent.class, captor.getValue());
                assertSame(player, event.getPlayer());
                Map<String, BigDecimal> amounts = event.getAmountsByEconomy();
                assertEquals(2, amounts.size());
                assertMoney("15", amounts.get("vault"));
                assertMoney("7", amounts.get("points"));
                assertFalse(amounts.containsKey("ghost"));
            }
        }

        @Test
        @DisplayName("aucun depot (economie introuvable) : aucun evenement")
        void claimMoney_nothingPaid_doesNotFireEvent() throws Exception {
            newPending(alice, "ghost", "10");

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

                claim();

                verify(pluginManager, never()).callEvent(any(Event.class));
            }
        }

        @Test
        @DisplayName("sans serveur Bukkit, l'echec de livraison de l'evenement ne fait pas echouer le claim")
        void claimMoney_eventDispatchFailure_doesNotFailClaim() throws Exception {
            int id = newPending(alice, "vault", "10");

            ClaimResult result = claim();

            assertTrue(result.success());
            assertEquals(10.0, result.amountClaimed(), 1e-9);
            assertEquals("RETRIEVED", statusOf(id));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Lectures
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("lectures")
    class Reads {

        @Test
        @DisplayName("getPendingTransactions ne rend que les PENDING du joueur")
        void getPendingTransactions_returnsOnlyPendingOfPlayer() throws Exception {
            UUID bob = newPlayer("Bob");
            int kept1 = newPending(alice, "vault", "10");
            int kept2 = newPending(alice, "points", "7");
            int retrieved = newPending(alice, "vault", "99");
            transactions().updateStatus(retrieved, TransactionStatus.RETRIEVED);
            newPending(bob, "vault", "50");

            List<TransactionDTO> pending = service.getPendingTransactions(alice).join();

            assertEquals(2, pending.size());
            assertTrue(pending.stream().map(TransactionDTO::id).toList().containsAll(List.of(kept1, kept2)));
            assertTrue(pending.stream().allMatch(transaction -> transaction.status() == TransactionStatus.PENDING));
            assertTrue(pending.stream().allMatch(transaction -> alice.equals(transaction.player_unique_id())));
        }

        @Test
        @DisplayName("getPendingTransactions sans ligne rend une liste vide")
        void getPendingTransactions_noRows_returnsEmpty() {
            assertTrue(service.getPendingTransactions(alice).join().isEmpty());
        }

        @Test
        @DisplayName("getPendingMoneyByEconomy cumule les valeurs positives par economie et ignore le reste")
        void getPendingMoneyByEconomy_sumsPositiveValuesPerEconomy() throws Exception {
            newPending(alice, "vault", "10");
            newPending(alice, "vault", "5.25");
            newPending(alice, "points", "7");
            newPending(alice, "vault", "-3");
            newPending(alice, "vault", "0");
            int retrieved = newPending(alice, "points", "1000");
            transactions().updateStatus(retrieved, TransactionStatus.RETRIEVED);

            Map<String, BigDecimal> byEconomy = service.getPendingMoneyByEconomy(alice).join();

            assertEquals(2, byEconomy.size());
            assertMoney("15.25", byEconomy.get("vault"));
            assertMoney("7", byEconomy.get("points"));
        }

        @Test
        @DisplayName("getPendingMoneyByEconomy n'inclut pas une economie dont toutes les lignes sont non positives")
        void getPendingMoneyByEconomy_onlyNonPositive_returnsEmpty() throws Exception {
            newPending(alice, "vault", "0");
            newPending(alice, "vault", "-1");

            assertTrue(service.getPendingMoneyByEconomy(alice).join().isEmpty());
        }

        @Test
        @DisplayName("getTotalPendingMoney additionne toutes les economies")
        void getTotalPendingMoney_sumsAllEconomies() throws Exception {
            newPending(alice, "vault", "10");
            newPending(alice, "points", "7.5");
            newPending(alice, "vault", "-3");

            assertMoney("17.5", service.getTotalPendingMoney(alice).join());
        }

        @Test
        @DisplayName("getTotalPendingMoney rend ZERO sans transaction")
        void getTotalPendingMoney_noRows_returnsZero() {
            assertMoney("0", service.getTotalPendingMoney(alice).join());
        }
    }

    // ------------------------------------------------------------------------------------------
    // clearPendingTransactions
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("clearPendingTransactions")
    class ClearPending {

        @Test
        @DisplayName("giveMoney=false : lignes passees RETRIEVED sans paiement ni reservation")
        void clearPendingTransactions_withoutMoney_discardsRows() throws Exception {
            int first = newPending(alice, "vault", "10");
            int second = newPending(alice, "ghost", "7");

            service.clearPendingTransactions(alice, false).join();

            assertEquals("RETRIEVED", statusOf(first));
            assertEquals("RETRIEVED", statusOf(second));
            assertNull(tokenOf(first));
            assertNull(tokenOf(second));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("giveMoney=false : les lignes des autres joueurs sont intactes")
        void clearPendingTransactions_withoutMoney_otherRowsUntouched() throws Exception {
            UUID bob = newPlayer("Bob");
            int bobRow = newPending(bob, "vault", "50");
            newPending(alice, "vault", "10");

            service.clearPendingTransactions(alice, false).join();

            assertEquals("PENDING", statusOf(bobRow));
            assertEquals(1, countStatus(alice, TransactionStatus.RETRIEVED));
        }

        @Test
        @DisplayName("giveMoney=false sans ligne : aucune erreur")
        void clearPendingTransactions_withoutMoney_noRows_noop() throws Exception {
            service.clearPendingTransactions(alice, false).join();
            assertEquals(0, count(Tables.TRANSACTIONS));
        }

        @Test
        @DisplayName("giveMoney=true : un depot par economie puis cloture avec jeton")
        void clearPendingTransactions_withMoney_depositsAndCloses() throws Exception {
            int first = newPending(alice, "vault", "10");
            int second = newPending(alice, "vault", "5");

            service.clearPendingTransactions(alice, true).join();

            ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
            verify(economy, times(1)).depositChecked(eq(alice), amounts.capture(), eq("test-claim"));
            assertMoney("15", amounts.getValue());
            assertEquals("RETRIEVED", statusOf(first));
            assertEquals("RETRIEVED", statusOf(second));
            String token = tokenOf(first);
            assertNotNull(token);
            assertTrue(token.startsWith(serverPrefix()));
            assertEquals(token, tokenOf(second));
            // Aucun message n'est envoye : c'est un chemin administratif, pas un claim joueur.
            assertTrue(recorder.isEmpty());
        }

        @Test
        @DisplayName("giveMoney=true avec economie introuvable : lignes liberees, toujours PENDING")
        void clearPendingTransactions_withMoney_unknownEconomy_releases() throws Exception {
            int id = newPending(alice, "ghost", "10");

            service.clearPendingTransactions(alice, true).join();

            assertEquals("PENDING", statusOf(id));
            assertNull(tokenOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("giveMoney=true avec depot refuse : lignes liberees")
        void clearPendingTransactions_withMoney_depositRefused_releases() throws Exception {
            stubDeposit(economy, false);
            int id = newPending(alice, "vault", "10");

            service.clearPendingTransactions(alice, true).join();

            assertEquals("PENDING", statusOf(id));
            assertNull(tokenOf(id));
        }

        @Test
        @DisplayName("giveMoney=true : les lignes non positives sont cloturees sans depot")
        void clearPendingTransactions_withMoney_nonPositiveRows_closedWithoutDeposit() throws Exception {
            int id = newPending(alice, "vault", "0");

            service.clearPendingTransactions(alice, true).join();

            assertEquals("RETRIEVED", statusOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("giveMoney=true sur des lignes reservees par un autre serveur : rien n'est touche")
        void clearPendingTransactions_withMoney_reservedElsewhere_noop() throws Exception {
            int id = newPending(alice, "vault", "10");
            String foreign = otherServerPrefix() + "abcdef-" + FIXED_SUFFIX;
            assertEquals(1, transactions().reservePending(alice, foreign));

            service.clearPendingTransactions(alice, true).join();

            assertEquals("PENDING", statusOf(id));
            assertEquals(foreign, tokenOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("giveMoney=true sans ligne : aucune erreur, aucun depot")
        void clearPendingTransactions_withMoney_noRows_noop() throws Exception {
            service.clearPendingTransactions(alice, true).join();
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }
    }

    // ------------------------------------------------------------------------------------------
    // handlePlayerJoin
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("handlePlayerJoin")
    class PlayerJoin {

        @Test
        @DisplayName("auto-claim actif sans delai : l'argent est verse immediatement")
        void handlePlayerJoin_autoClaimEnabledNoDelay_claimsImmediately() throws Exception {
            autoClaim(true, 0, true, 0);
            int id = newPending(alice, "vault", "10");

            service.handlePlayerJoin(player);

            assertEquals("RETRIEVED", statusOf(id));
            ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
            verify(economy, times(1)).depositChecked(eq(alice), amounts.capture(), any());
            assertMoney("10", amounts.getValue());
            assertTrue(recorder.has(Message.CLAIM_SUCCESS));
            assertFalse(recorder.has(Message.CLAIM_PENDING_NOTIFY));
        }

        @Test
        @DisplayName("auto-claim actif avec delai : passe par runLater(delai) puis verse l'argent")
        void handlePlayerJoin_autoClaimEnabledWithDelay_claimsThroughScheduler() throws Exception {
            autoClaim(true, 20, true, 0);
            int id = newPending(alice, "vault", "10");

            service.handlePlayerJoin(player);

            verify(plugin.getScheduler()).runLater(ArgumentMatchers.<Consumer<WrappedTask>>any(), eq(20L));
            assertEquals("RETRIEVED", statusOf(id));
            verify(economy, times(1)).depositChecked(eq(alice), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("auto-claim inactif, notification active : message CLAIM_PENDING_NOTIFY, rien n'est verse")
        void handlePlayerJoin_autoClaimDisabledNotifyPending_sendsNotification() throws Exception {
            autoClaim(false, 0, true, 0);
            int first = newPending(alice, "vault", "10");
            newPending(alice, "vault", "5");

            service.handlePlayerJoin(player);

            var notification = recorder.first(Message.CLAIM_PENDING_NOTIFY).orElseThrow();
            assertSame(player, notification.sender());
            assertMoney("15", parseFormatted(notification.arg("%amount%")));
            assertEquals("1", notification.arg("%count%"));
            assertEquals("PENDING", statusOf(first));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("notification avec delai : passe par runLater(delai)")
        void handlePlayerJoin_notifyWithDelay_usesScheduler() throws Exception {
            autoClaim(false, 0, true, 40);
            newPending(alice, "vault", "10");

            service.handlePlayerJoin(player);

            verify(plugin.getScheduler()).runLater(ArgumentMatchers.<Consumer<WrappedTask>>any(), eq(40L));
            assertTrue(recorder.has(Message.CLAIM_PENDING_NOTIFY));
        }

        @Test
        @DisplayName("notification : %count% compte les economies et %amount% les liste toutes")
        void handlePlayerJoin_notify_countsEconomies() throws Exception {
            AuctionEconomy points = MockPlugins.economy("points");
            useEconomies(economy, points);
            autoClaim(false, 0, true, 0);
            newPending(alice, "vault", "10");
            newPending(alice, "points", "7");

            service.handlePlayerJoin(player);

            var notification = recorder.first(Message.CLAIM_PENDING_NOTIFY).orElseThrow();
            assertEquals("2", notification.arg("%count%"));
            String amount = notification.arg("%amount%");
            assertNotNull(amount);
            assertTrue(amount.contains(", "), "les economies doivent etre separees par une virgule : " + amount);
        }

        @Test
        @DisplayName("notification : une economie disparue est affichee avec son nom brut")
        void handlePlayerJoin_notify_unknownEconomyFallsBackToRawName() throws Exception {
            autoClaim(false, 0, true, 0);
            newPending(alice, "ghost", "10");

            service.handlePlayerJoin(player);

            var notification = recorder.first(Message.CLAIM_PENDING_NOTIFY).orElseThrow();
            String amount = notification.arg("%amount%");
            assertNotNull(amount);
            assertTrue(amount.endsWith(" ghost"), "format brut attendu '<montant> ghost' : " + amount);
        }

        @Test
        @DisplayName("auto-claim et notification inactifs : aucun message, aucun depot")
        void handlePlayerJoin_bothDisabled_doesNothing() throws Exception {
            autoClaim(false, 0, false, 0);
            int id = newPending(alice, "vault", "10");

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
            assertEquals("PENDING", statusOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }

        @Test
        @DisplayName("rien en attente : aucun message meme si la notification est active")
        void handlePlayerJoin_nothingPending_doesNothing() {
            autoClaim(false, 0, true, 0);

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
        }

        @Test
        @DisplayName("montant total non positif : aucun message, aucun claim")
        void handlePlayerJoin_nonPositivePending_doesNothing() throws Exception {
            autoClaim(true, 0, true, 0);
            int id = newPending(alice, "vault", "-2");

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
            assertEquals("PENDING", statusOf(id));
        }

        @Test
        @DisplayName("joueur deja deconnecte : aucun message, aucun claim")
        void handlePlayerJoin_playerOffline_doesNothing() throws Exception {
            autoClaim(true, 0, true, 0);
            when(player.isOnline()).thenReturn(false);
            int id = newPending(alice, "vault", "10");

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
            assertEquals("PENDING", statusOf(id));
            verify(economy, never()).depositChecked(any(UUID.class), any(BigDecimal.class), any());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Doublures locales
    // ------------------------------------------------------------------------------------------

    /**
     * {@link ClaimService} dont l'envoi de messages est capture au lieu de passer par
     * PlaceholderAPI / MiniMessage (qui exigent un serveur).
     */
    static final class RecordingClaimService extends ClaimService {

        private final MessageRecorder recorder;

        RecordingClaimService(AuctionPlugin plugin, MessageRecorder recorder) {
            super(plugin);
            this.recorder = recorder;
        }

        @Override
        protected void message(AuctionPlugin plugin, CommandSender sender, Message message, Object... args) {
            this.recorder.record(sender, message, args);
        }
    }

    /**
     * Executor qui met les taches en file sans les executer : permet d'observer un claim "en
     * cours" puis de le terminer a la demande.
     */
    static final class DeferredExecutor extends AbstractExecutorService {

        private final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            this.queue.add(command);
        }

        int runAll() {
            int executed = 0;
            Runnable runnable;
            while ((runnable = this.queue.poll()) != null) {
                runnable.run();
                executed++;
            }
            return executed;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
