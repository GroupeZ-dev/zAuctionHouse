package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SQL du coeur du claim d'argent : chaque ecriture est verifiee sur l'etat REEL des lignes
 * (SQL brut) en plus des valeurs rendues par le repository.
 */
class TransactionRepositoryTest extends DatabaseTestBase {

    private static final String ECONOMY = "vault";

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Cree une annonce realiste (ligne items) puis une transaction qui la reference, et rend l'id
     * de la transaction inseree.
     */
    private int createTransaction(UUID player, BigDecimal before, BigDecimal after, BigDecimal value, TransactionStatus status) throws Exception {
        long expiredAt = System.currentTimeMillis() + 60_000L;
        int itemId = items().create(player, ItemType.AUCTION, value, expiredAt, this.economy);
        ZAuctionItem item = TestItems.auctionItem(this.plugin, itemId, player, this.economy, expiredAt);

        transactions().create(item, player, ECONOMY, before, after, value, status);

        return (int) queryLong("SELECT MAX(id) FROM " + Tables.TRANSACTIONS);
    }

    private int pending(UUID player, String value) throws Exception {
        return createTransaction(player, BigDecimal.ZERO, new BigDecimal(value), new BigDecimal(value), TransactionStatus.PENDING);
    }

    private int retrieved(UUID player, String value) throws Exception {
        return createTransaction(player, BigDecimal.ZERO, new BigDecimal(value), new BigDecimal(value), TransactionStatus.RETRIEVED);
    }

    private String statusOf(int id) throws Exception {
        return queryString("SELECT status FROM " + Tables.TRANSACTIONS + " WHERE id = ?", id);
    }

    private String tokenOf(int id) throws Exception {
        return queryString("SELECT claim_token FROM " + Tables.TRANSACTIONS + " WHERE id = ?", id);
    }

    private Object reservedAtOf(int id) throws Exception {
        return queryObject("SELECT claim_reserved_at FROM " + Tables.TRANSACTIONS + " WHERE id = ?", id);
    }

    /**
     * Pose un jeton "a la main", comme l'aurait fait un autre serveur du cluster.
     */
    private void setToken(int id, String token) throws Exception {
        execute("UPDATE " + Tables.TRANSACTIONS + " SET claim_token = ?, claim_reserved_at = ? WHERE id = ?", token, new Timestamp(System.currentTimeMillis()), id);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "attendu " + expected + " mais lu " + actual);
    }

    // ------------------------------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("ecrit toutes les colonnes et laisse claim_token / claim_reserved_at a NULL")
        void create_writesEveryColumn_andLeavesClaimColumnsNull() throws Exception {
            UUID player = newPlayer("Alice");
            long expiredAt = System.currentTimeMillis() + 60_000L;
            int itemId = items().create(player, ItemType.AUCTION, new BigDecimal("12.50"), expiredAt, economy);
            ZAuctionItem item = TestItems.auctionItem(plugin, itemId, player, economy, expiredAt);

            transactions().create(item, player, ECONOMY, new BigDecimal("100.00"), new BigDecimal("112.50"), new BigDecimal("12.50"), TransactionStatus.PENDING);

            assertEquals(1, count(Tables.TRANSACTIONS));
            List<Object[]> rows = queryRows("SELECT item_id, player_unique_id, economy_name, `before`, `after`, `value`, status, claim_token, claim_reserved_at, created_at FROM " + Tables.TRANSACTIONS);
            assertEquals(1, rows.size());
            Object[] row = rows.get(0);
            assertEquals(itemId, ((Number) row[0]).intValue());
            assertEquals(player.toString(), row[1]);
            assertEquals(ECONOMY, row[2]);
            assertEquals(0, new BigDecimal("100.00").compareTo(new BigDecimal(row[3].toString())));
            assertEquals(0, new BigDecimal("112.50").compareTo(new BigDecimal(row[4].toString())));
            assertEquals(0, new BigDecimal("12.50").compareTo(new BigDecimal(row[5].toString())));
            assertEquals("PENDING", row[6]);
            assertNull(row[7], "claim_token doit etre NULL a la creation");
            assertNull(row[8], "claim_reserved_at doit etre NULL a la creation");
            assertNotNull(row[9], "created_at doit etre rempli par defaut");
        }

        @Test
        @DisplayName("le statut RETRIEVED est stocke par son nom d'enum")
        void create_storesStatusName() throws Exception {
            UUID player = newPlayer("Alice");
            int id = retrieved(player, "5");

            assertEquals("RETRIEVED", statusOf(id));
        }

        @Test
        @DisplayName("deux creations produisent deux lignes avec des identifiants croissants")
        void create_twice_insertsTwoRowsWithIncreasingIds() throws Exception {
            UUID player = newPlayer("Alice");
            int first = pending(player, "1");
            int second = pending(player, "2");

            assertEquals(2, count(Tables.TRANSACTIONS));
            assertTrue(second > first);
        }
    }

    // ------------------------------------------------------------------------------------------
    // selectByPlayer / selectByPlayerAndStatus
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("selectByPlayer / selectByPlayerAndStatus")
    class Select {

        @Test
        @DisplayName("selectByPlayer rend uniquement les lignes du joueur, avec un mapping complet")
        void selectByPlayer_filtersByPlayer_andMapsDto() throws Exception {
            UUID alice = newPlayer("Alice");
            UUID bob = newPlayer("Bob");
            int id = createTransaction(alice, new BigDecimal("10.00"), new BigDecimal("25.50"), new BigDecimal("15.50"), TransactionStatus.PENDING);
            retrieved(alice, "3");
            pending(bob, "99");

            List<TransactionDTO> rows = transactions().selectByPlayer(alice);

            assertEquals(2, rows.size());
            assertTrue(rows.stream().allMatch(dto -> alice.equals(dto.player_unique_id())));
            TransactionDTO dto = rows.stream().filter(row -> row.id() == id).findFirst().orElseThrow();
            assertEquals(ECONOMY, dto.economy_name());
            assertMoney("10.00", dto.before());
            assertMoney("25.50", dto.after());
            assertMoney("15.50", dto.value());
            assertEquals(TransactionStatus.PENDING, dto.status());
            assertNotNull(dto.created_at());
        }

        @Test
        @DisplayName("selectByPlayer rend une liste vide pour un joueur inconnu")
        void selectByPlayer_unknownPlayer_returnsEmpty() throws Exception {
            pending(newPlayer("Alice"), "1");

            assertTrue(transactions().selectByPlayer(UUID.randomUUID()).isEmpty());
        }

        @Test
        @DisplayName("selectByPlayerAndStatus filtre exactement sur le joueur ET le statut")
        void selectByPlayerAndStatus_filtersBoth() throws Exception {
            UUID alice = newPlayer("Alice");
            UUID bob = newPlayer("Bob");
            int p1 = pending(alice, "1");
            int p2 = pending(alice, "2");
            int r1 = retrieved(alice, "3");
            pending(bob, "4");

            List<TransactionDTO> pendings = transactions().selectByPlayerAndStatus(alice, TransactionStatus.PENDING);
            List<TransactionDTO> retrieveds = transactions().selectByPlayerAndStatus(alice, TransactionStatus.RETRIEVED);

            assertEquals(2, pendings.size());
            assertEquals(Set.of(p1, p2), Set.of(pendings.get(0).id(), pendings.get(1).id()));
            assertTrue(pendings.stream().allMatch(dto -> dto.status() == TransactionStatus.PENDING));
            assertEquals(1, retrieveds.size());
            assertEquals(r1, retrieveds.get(0).id());
            assertEquals(TransactionStatus.RETRIEVED, retrieveds.get(0).status());
        }

        @Test
        @DisplayName("selectByPlayerAndStatus rend vide quand aucun statut ne correspond")
        void selectByPlayerAndStatus_noMatch_returnsEmpty() throws Exception {
            UUID alice = newPlayer("Alice");
            pending(alice, "1");

            assertTrue(transactions().selectByPlayerAndStatus(alice, TransactionStatus.RETRIEVED).isEmpty());
        }
    }

    // ------------------------------------------------------------------------------------------
    // updateStatus
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("updateStatus(int) cloture une PENDING")
        void updateStatus_single_closesPending() throws Exception {
            UUID alice = newPlayer("Alice");
            int id = pending(alice, "1");
            int other = pending(alice, "2");

            transactions().updateStatus(id, TransactionStatus.RETRIEVED);

            assertEquals("RETRIEVED", statusOf(id));
            assertEquals("PENDING", statusOf(other), "l'autre ligne ne doit pas bouger");
        }

        @Test
        @DisplayName("updateStatus(int) ne remet jamais une RETRIEVED en PENDING (CAS sur PENDING)")
        void updateStatus_single_cannotReopenRetrieved() throws Exception {
            UUID alice = newPlayer("Alice");
            int id = retrieved(alice, "1");

            transactions().updateStatus(id, TransactionStatus.PENDING);

            assertEquals("RETRIEVED", statusOf(id));
        }

        @Test
        @DisplayName("updateStatus(Collection) rend le nombre de lignes reellement modifiees")
        void updateStatus_collection_returnsActualCount() throws Exception {
            UUID alice = newPlayer("Alice");
            int p1 = pending(alice, "1");
            int p2 = pending(alice, "2");
            int r1 = retrieved(alice, "3");

            int updated = transactions().updateStatus(List.of(p1, p2, r1, 999_999), TransactionStatus.RETRIEVED);

            assertEquals(2, updated, "seules les deux PENDING doivent compter");
            assertEquals("RETRIEVED", statusOf(p1));
            assertEquals("RETRIEVED", statusOf(p2));
            assertEquals("RETRIEVED", statusOf(r1));
            assertEquals(3, count(Tables.TRANSACTIONS), "aucune ligne ne doit etre creee pour l'id inconnu");
        }

        @Test
        @DisplayName("updateStatus(Collection) sur des lignes deja RETRIEVED rend 0")
        void updateStatus_collection_alreadyRetrieved_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int r1 = retrieved(alice, "1");
            int r2 = retrieved(alice, "2");

            assertEquals(0, transactions().updateStatus(List.of(r1, r2), TransactionStatus.RETRIEVED));
        }

        @Test
        @DisplayName("updateStatus(Collection) avec une collection vide ou null rend 0 sans toucher la base")
        void updateStatus_collection_emptyOrNull_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int id = pending(alice, "1");

            assertEquals(0, transactions().updateStatus(List.of(), TransactionStatus.RETRIEVED));
            assertEquals(0, transactions().updateStatus((Collection<Integer>) null, TransactionStatus.RETRIEVED));
            assertEquals("PENDING", statusOf(id));
        }

        @Test
        @DisplayName("updateStatus(Collection) avec des ids inconnus rend 0")
        void updateStatus_collection_unknownIds_returnsZero() throws Exception {
            newPlayer("Alice");

            assertEquals(0, transactions().updateStatus(List.of(1, 2, 3), TransactionStatus.RETRIEVED));
        }

        @Test
        @DisplayName("updateStatus(Collection) avec un id en double ne compte la ligne qu'une fois")
        void updateStatus_collection_duplicateIds_countsRowOnce() throws Exception {
            UUID alice = newPlayer("Alice");
            int id = pending(alice, "1");

            int updated = transactions().updateStatus(List.of(id, id, id), TransactionStatus.RETRIEVED);

            // Le premier UPDATE du batch cloture la ligne ; les deux suivants matchent 0 ligne.
            assertEquals(1, updated);
            assertEquals("RETRIEVED", statusOf(id));
        }

        @Test
        @DisplayName("updateStatus(Collection) sur un lot de plusieurs dizaines de lignes cloture tout et compte exactement")
        void updateStatus_collection_largeBatch_updatesEveryRow() throws Exception {
            UUID alice = newPlayer("Alice");
            List<Integer> ids = new ArrayList<>();
            for (int index = 0; index < 60; index++) ids.add(pending(alice, "1"));
            ids.add(retrieved(alice, "1")); // une RETRIEVED glissee dans le lot : ne compte pas

            int updated = transactions().updateStatus(ids, TransactionStatus.RETRIEVED);

            assertEquals(60, updated);
            assertEquals(61, count(Tables.TRANSACTIONS, "status = ?", "RETRIEVED"));
            assertEquals(0, count(Tables.TRANSACTIONS, "status = ?", "PENDING"));
        }
    }

    // ------------------------------------------------------------------------------------------
    // reservePending
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("reservePending")
    class ReservePending {

        @Test
        @DisplayName("reserve toutes les PENDING libres du joueur et seulement elles")
        void reservePending_reservesOnlyFreePendingOfPlayer() throws Exception {
            UUID alice = newPlayer("Alice");
            UUID bob = newPlayer("Bob");
            int free1 = pending(alice, "1");
            int free2 = pending(alice, "2");
            int free3 = pending(alice, "3");
            int closed = retrieved(alice, "4");
            int taken = pending(alice, "5");
            setToken(taken, "other-server-token");
            int bobRow = pending(bob, "6");

            int reserved = transactions().reservePending(alice, "tok-1");

            assertEquals(3, reserved);
            assertEquals(3, count(Tables.TRANSACTIONS, "claim_token = ?", "tok-1"));
            for (int id : List.of(free1, free2, free3)) {
                assertEquals("tok-1", tokenOf(id));
                assertNotNull(reservedAtOf(id), "claim_reserved_at doit etre ecrit pour " + id);
                assertEquals("PENDING", statusOf(id), "la reservation ne change pas le statut");
            }
            assertNull(tokenOf(closed), "une RETRIEVED ne doit pas etre reservee");
            assertEquals("other-server-token", tokenOf(taken), "une ligne deja reservee garde son jeton");
            assertNull(tokenOf(bobRow), "la ligne d'un autre joueur ne doit pas etre reservee");
        }

        @Test
        @DisplayName("un second claim concurrent (autre jeton) obtient 0")
        void reservePending_secondClaim_getsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            pending(alice, "1");
            pending(alice, "2");

            assertEquals(2, transactions().reservePending(alice, "tok-1"));
            assertEquals(0, transactions().reservePending(alice, "tok-2"));

            assertEquals(2, count(Tables.TRANSACTIONS, "claim_token = ?", "tok-1"));
            assertEquals(0, count(Tables.TRANSACTIONS, "claim_token = ?", "tok-2"));
        }

        @Test
        @DisplayName("rend 0 quand le joueur n'a aucune PENDING")
        void reservePending_noPending_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            retrieved(alice, "1");

            assertEquals(0, transactions().reservePending(alice, "tok-1"));
            assertEquals(0, transactions().reservePending(UUID.randomUUID(), "tok-2"));
            assertEquals(0, count(Tables.TRANSACTIONS, "claim_token IS NOT NULL"));
        }

        @Test
        @DisplayName("une ligne liberee redevient reservable par un nouveau jeton")
        void reservePending_afterRelease_canBeReservedAgain() throws Exception {
            UUID alice = newPlayer("Alice");
            int id = pending(alice, "1");

            assertEquals(1, transactions().reservePending(alice, "tok-1"));
            assertEquals(1, transactions().releaseReservation("tok-1"));
            assertEquals(1, transactions().reservePending(alice, "tok-2"));

            assertEquals("tok-2", tokenOf(id));
        }
    }

    // ------------------------------------------------------------------------------------------
    // selectByClaimToken
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("selectByClaimToken")
    class SelectByClaimToken {

        @Test
        @DisplayName("relit exactement les lignes portant le jeton")
        void selectByClaimToken_returnsReservedRows() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1.50");
            int b = pending(alice, "2.25");
            int other = pending(alice, "3");
            setToken(other, "tok-other");
            transactions().reservePending(alice, "tok-1");

            List<TransactionDTO> rows = transactions().selectByClaimToken("tok-1");

            assertEquals(2, rows.size());
            assertEquals(Set.of(a, b), Set.of(rows.get(0).id(), rows.get(1).id()));
            assertTrue(rows.stream().allMatch(dto -> dto.status() == TransactionStatus.PENDING));
            BigDecimal total = rows.stream().map(TransactionDTO::value).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertMoney("3.75", total);
        }

        @Test
        @DisplayName("rend vide pour un jeton inconnu")
        void selectByClaimToken_unknownToken_returnsEmpty() throws Exception {
            UUID alice = newPlayer("Alice");
            pending(alice, "1");
            transactions().reservePending(alice, "tok-1");

            assertTrue(transactions().selectByClaimToken("tok-unknown").isEmpty());
        }
    }

    // ------------------------------------------------------------------------------------------
    // finishClaim
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("finishClaim")
    class FinishClaim {

        @Test
        @DisplayName("cloture les lignes du jeton et rend le nombre exact")
        void finishClaim_closesReservedRows() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            int b = pending(alice, "2");
            transactions().reservePending(alice, "tok-1");

            int closed = transactions().finishClaim(List.of(a, b), "tok-1");

            assertEquals(2, closed);
            assertEquals("RETRIEVED", statusOf(a));
            assertEquals("RETRIEVED", statusOf(b));
            assertEquals("tok-1", tokenOf(a), "le jeton est conserve sur une ligne cloturee (trace)");
        }

        @Test
        @DisplayName("un mauvais jeton ne cloture rien")
        void finishClaim_wrongToken_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            transactions().reservePending(alice, "tok-1");

            assertEquals(0, transactions().finishClaim(List.of(a), "tok-2"));
            assertEquals("PENDING", statusOf(a));
            assertEquals("tok-1", tokenOf(a));
        }

        @Test
        @DisplayName("une ligne deja RETRIEVED n'est pas recomptee")
        void finishClaim_alreadyRetrieved_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            transactions().reservePending(alice, "tok-1");
            assertEquals(1, transactions().finishClaim(List.of(a), "tok-1"));

            assertEquals(0, transactions().finishClaim(List.of(a), "tok-1"));
        }

        @Test
        @DisplayName("une ligne non reservee (jeton NULL) n'est pas cloturee")
        void finishClaim_unreservedRow_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");

            assertEquals(0, transactions().finishClaim(List.of(a), "tok-1"));
            assertEquals("PENDING", statusOf(a));
        }

        @Test
        @DisplayName("ne compte que les lignes qui portent bien le jeton dans une liste mixte")
        void finishClaim_mixedIds_returnsExactCount() throws Exception {
            UUID alice = newPlayer("Alice");
            int mine = pending(alice, "1");
            int theirs = pending(alice, "2");
            setToken(theirs, "tok-other");
            transactions().reservePending(alice, "tok-1");

            int closed = transactions().finishClaim(List.of(mine, theirs, 424_242), "tok-1");

            assertEquals(1, closed);
            assertEquals("RETRIEVED", statusOf(mine));
            assertEquals("PENDING", statusOf(theirs));
        }

        @Test
        @DisplayName("collection vide ou null rend 0")
        void finishClaim_emptyOrNull_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            transactions().reservePending(alice, "tok-1");

            assertEquals(0, transactions().finishClaim(List.of(), "tok-1"));
            assertEquals(0, transactions().finishClaim(null, "tok-1"));
            assertEquals("PENDING", statusOf(a));
        }
    }

    // ------------------------------------------------------------------------------------------
    // releaseClaim
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("releaseClaim")
    class ReleaseClaim {

        @Test
        @DisplayName("remet claim_token et claim_reserved_at a NULL pour les lignes du jeton")
        void releaseClaim_clearsTokenAndReservedAt() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            int b = pending(alice, "2");
            transactions().reservePending(alice, "tok-1");
            assertNotNull(reservedAtOf(a));

            int released = transactions().releaseClaim(List.of(a, b), "tok-1");

            assertEquals(2, released);
            assertNull(tokenOf(a));
            assertNull(tokenOf(b));
            assertNull(reservedAtOf(a));
            assertNull(reservedAtOf(b));
            assertEquals("PENDING", statusOf(a), "le statut reste PENDING : l'argent est de nouveau reclamable");
        }

        @Test
        @DisplayName("ne touche pas une ligne portant un autre jeton")
        void releaseClaim_otherToken_untouched() throws Exception {
            UUID alice = newPlayer("Alice");
            int mine = pending(alice, "1");
            int theirs = pending(alice, "2");
            setToken(theirs, "tok-other");
            transactions().reservePending(alice, "tok-1");

            int released = transactions().releaseClaim(List.of(mine, theirs), "tok-1");

            assertEquals(1, released);
            assertNull(tokenOf(mine));
            assertEquals("tok-other", tokenOf(theirs));
            assertNotNull(reservedAtOf(theirs));
        }

        @Test
        @DisplayName("un mauvais jeton rend 0 et ne libere rien")
        void releaseClaim_wrongToken_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            transactions().reservePending(alice, "tok-1");

            assertEquals(0, transactions().releaseClaim(List.of(a), "tok-2"));
            assertEquals("tok-1", tokenOf(a));
        }

        @Test
        @DisplayName("collection vide ou null rend 0")
        void releaseClaim_emptyOrNull_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            transactions().reservePending(alice, "tok-1");

            assertEquals(0, transactions().releaseClaim(List.of(), "tok-1"));
            assertEquals(0, transactions().releaseClaim(null, "tok-1"));
            assertEquals("tok-1", tokenOf(a));
        }
    }

    // ------------------------------------------------------------------------------------------
    // releaseReservation
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("releaseReservation")
    class ReleaseReservation {

        @Test
        @DisplayName("libere uniquement les PENDING du jeton, les RETRIEVED conservent leur jeton")
        void releaseReservation_onlyPendingRows() throws Exception {
            UUID alice = newPlayer("Alice");
            int paid = pending(alice, "1");
            int unpaid1 = pending(alice, "2");
            int unpaid2 = pending(alice, "3");
            int other = pending(alice, "4");
            setToken(other, "tok-other");
            transactions().reservePending(alice, "tok-1");
            assertEquals(1, transactions().finishClaim(List.of(paid), "tok-1"));

            int released = transactions().releaseReservation("tok-1");

            assertEquals(2, released);
            assertNull(tokenOf(unpaid1));
            assertNull(tokenOf(unpaid2));
            assertNull(reservedAtOf(unpaid1));
            assertEquals("tok-1", tokenOf(paid), "une ligne cloturee garde son jeton");
            assertEquals("RETRIEVED", statusOf(paid));
            assertEquals("tok-other", tokenOf(other), "le jeton d'un autre claim n'est pas touche");
        }

        @Test
        @DisplayName("jeton null ou vide rend 0 sans toucher la base")
        void releaseReservation_nullOrEmptyToken_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            int a = pending(alice, "1");
            transactions().reservePending(alice, "tok-1");

            assertEquals(0, transactions().releaseReservation(null));
            assertEquals(0, transactions().releaseReservation(""));
            assertEquals("tok-1", tokenOf(a));
        }

        @Test
        @DisplayName("jeton inconnu rend 0")
        void releaseReservation_unknownToken_returnsZero() throws Exception {
            UUID alice = newPlayer("Alice");
            pending(alice, "1");
            transactions().reservePending(alice, "tok-1");

            assertEquals(0, transactions().releaseReservation("tok-unknown"));
            assertEquals(1, count(Tables.TRANSACTIONS, "claim_token = ?", "tok-1"));
        }
    }

    // ------------------------------------------------------------------------------------------
    // selectReservedTokens
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("selectReservedTokens")
    class SelectReservedTokens {

        @Test
        @DisplayName("rend les jetons distincts du prefixe, en excluant autre joueur / autre prefixe / RETRIEVED / NULL")
        void selectReservedTokens_filtersAndDistinct() throws Exception {
            UUID alice = newPlayer("Alice");
            UUID bob = newPlayer("Bob");
            setToken(pending(alice, "1"), "srv1-aaa");
            setToken(pending(alice, "2"), "srv1-aaa"); // doublon : un seul jeton attendu
            setToken(pending(alice, "3"), "srv1-bbb");
            setToken(pending(alice, "4"), "srv2-ccc"); // autre prefixe
            setToken(retrieved(alice, "5"), "srv1-ddd"); // RETRIEVED
            pending(alice, "6"); // jeton NULL
            setToken(pending(bob, "7"), "srv1-eee"); // autre joueur

            List<String> tokens = transactions().selectReservedTokens(alice, "srv1-");

            assertEquals(2, tokens.size());
            assertEquals(Set.of("srv1-aaa", "srv1-bbb"), Set.copyOf(tokens));
        }

        @Test
        @DisplayName("prefixe null ou vide rend une liste vide")
        void selectReservedTokens_nullOrEmptyPrefix_returnsEmpty() throws Exception {
            UUID alice = newPlayer("Alice");
            setToken(pending(alice, "1"), "srv1-aaa");

            assertTrue(transactions().selectReservedTokens(alice, null).isEmpty());
            assertTrue(transactions().selectReservedTokens(alice, "").isEmpty());
        }

        @Test
        @DisplayName("le filtre est sensible a la casse : 'ABC-...' ne matche pas le prefixe 'abc-'")
        void selectReservedTokens_isCaseSensitive() throws Exception {
            UUID alice = newPlayer("Alice");
            setToken(pending(alice, "1"), "ABC-0001");
            setToken(pending(alice, "2"), "abc-0002");

            List<String> tokens = transactions().selectReservedTokens(alice, "abc-");

            assertEquals(List.of("abc-0002"), tokens);
        }

        @Test
        @DisplayName("aucune reservation rend une liste vide")
        void selectReservedTokens_noReservation_returnsEmpty() throws Exception {
            UUID alice = newPlayer("Alice");
            pending(alice, "1");

            assertTrue(transactions().selectReservedTokens(alice, "srv1-").isEmpty());
            assertTrue(transactions().selectReservedTokens(UUID.randomUUID(), "srv1-").isEmpty());
        }

        @Test
        @DisplayName("un jeton pose par reservePending est retrouve par le prefixe serveur")
        void selectReservedTokens_afterReservePending() throws Exception {
            UUID alice = newPlayer("Alice");
            pending(alice, "1");
            String token = "srv1-" + UUID.randomUUID();
            assertEquals(1, transactions().reservePending(alice, token));

            assertEquals(List.of(token), transactions().selectReservedTokens(alice, "srv1-"));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Scenario complet
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("scenario claim complet : create x3 -> reservePending -> selectByClaimToken -> finishClaim partiel -> releaseClaim du reste")
    void fullClaimScenario_endsInConsistentState() throws Exception {
        UUID alice = newPlayer("Alice");
        int t1 = pending(alice, "10");
        int t2 = pending(alice, "20");
        int t3 = pending(alice, "30");
        String token = "srv1-" + UUID.randomUUID();

        // 1. reservation atomique
        assertEquals(3, transactions().reservePending(alice, token));
        assertEquals(0, transactions().reservePending(alice, "srv2-concurrent"), "un claim concurrent ne gagne rien");

        // 2. relecture des lignes reservees
        List<TransactionDTO> reserved = transactions().selectByClaimToken(token);
        assertEquals(3, reserved.size());
        assertEquals(Set.of(t1, t2, t3), Set.of(reserved.get(0).id(), reserved.get(1).id(), reserved.get(2).id()));

        // 3. paiement partiel : t1 et t2 credites, t3 en echec
        assertEquals(2, transactions().finishClaim(List.of(t1, t2), token));

        // 4. liberation du reste
        assertEquals(1, transactions().releaseClaim(List.of(t3), token));

        // 5. etat final en SQL brut
        assertEquals("RETRIEVED", statusOf(t1));
        assertEquals("RETRIEVED", statusOf(t2));
        assertEquals("PENDING", statusOf(t3));
        assertEquals(token, tokenOf(t1));
        assertEquals(token, tokenOf(t2));
        assertNull(tokenOf(t3));
        assertNull(reservedAtOf(t3));
        assertEquals(2, count(Tables.TRANSACTIONS, "status = ? AND claim_token = ?", "RETRIEVED", token));
        assertEquals(1, count(Tables.TRANSACTIONS, "status = ? AND claim_token IS NULL", "PENDING"));

        // 6. plus aucune reservation vivante pour ce serveur, et t3 est de nouveau reclamable
        assertTrue(transactions().selectReservedTokens(alice, "srv1-").isEmpty());
        assertEquals(1, transactions().selectByPlayerAndStatus(alice, TransactionStatus.PENDING).size());
        assertEquals(1, transactions().reservePending(alice, "srv1-retry"));
        assertFalse(transactions().selectByClaimToken("srv1-retry").isEmpty());
    }
}
