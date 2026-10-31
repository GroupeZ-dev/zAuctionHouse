package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository.OrphanReservationSweep;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cycle de vie des RESERVATIONS de vente dans {@link ItemRepository} : creation, jalon
 * {@code pending_publish} (compare-and-set), publication, mise en reclamable, suppression et
 * balayage des orphelines.
 * <p>
 * Chaque assertion de fin d'etat est faite en SQL brut, independamment du repository.
 * <p>
 * Rappel des invariants (voir les Javadoc de la classe testee) :
 * <ul>
 *   <li>{@code pending_publish = 1} (RESERVEE) : le vendeur a ENCORE son lot, la ligne peut
 *   etre supprimee et ne doit JAMAIS lui etre rendue ;</li>
 *   <li>{@code pending_publish = 2} (ENGAGEE) : le vendeur a PERDU son lot, la ligne doit
 *   rester reclamable et ne doit JAMAIS etre supprimee ;</li>
 *   <li>{@code pending_publish = 0} : cycle de vie normal ; {@code NULL} : ligne anterieure,
 *   jamais consideree comme une reservation.</li>
 * </ul>
 */
class ItemRepositoryReservationTest extends DatabaseTestBase {

    private static final int UNKNOWN_ID = 987_654;
    private static final long EXPIRES_IN_ONE_HOUR = System.currentTimeMillis() + 3_600_000L;
    /** 1er janvier 2020, en millis (INTEGER) et au format TEXT de CURRENT_TIMESTAMP (SQLite). */
    private static final long OLD_CREATED_AT_MILLIS = 1_577_836_800_000L;
    private static final String OLD_CREATED_AT_TEXT = "2020-01-01 00:00:00";

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /** Cree une annonce publiee (LISTED, pending 0). */
    private int listed(UUID seller) {
        return items().create(seller, ItemType.AUCTION, BigDecimal.TEN, EXPIRES_IN_ONE_HOUR, this.economy);
    }

    /** Cree une reservation NON engagee (DELETED, pending 1). */
    private int reserved(UUID seller) {
        return items().create(seller, ItemType.AUCTION, BigDecimal.TEN, EXPIRES_IN_ONE_HOUR, this.economy, StorageType.DELETED);
    }

    /** Cree une reservation ENGAGEE (DELETED, pending 2). */
    private int committed(UUID seller) {
        int itemId = reserved(seller);
        assertEquals(1, items().markReservationCommitted(itemId), "pre-condition : le jalon doit se poser");
        return itemId;
    }

    /** Insere {@code amount} lignes de contenu pour l'annonce donnee. */
    private void addContents(UUID seller, int itemId, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        for (int index = 0; index < amount; index++) {
            stacks.add(TestItems.diamond(index + 1));
            payloads.add(TestItems.encodedPayload(index));
        }
        auctionItems().create(seller, "Seller", itemId, BigDecimal.TEN, EXPIRES_IN_ONE_HOUR, stacks, payloads, this.economy);
    }

    private String storageTypeOf(int itemId) throws SQLException {
        return queryString("SELECT storage_type FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private Object pendingOf(int itemId) throws SQLException {
        return queryObject("SELECT pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private long pendingLongOf(int itemId) throws SQLException {
        return queryLong("SELECT pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private long expiredAtOf(int itemId) throws SQLException {
        return queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private long rowsOf(int itemId) throws SQLException {
        return count(Tables.ITEMS, "id = ?", itemId);
    }

    private long contentsOf(int itemId) throws SQLException {
        return count(Tables.AUCTION_ITEMS, "item_id = ?", itemId);
    }

    private void setStorageType(int itemId, StorageType storageType) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET storage_type = ? WHERE id = ?", storageType.name(), itemId));
    }

    private void setPending(int itemId, Integer pending) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET pending_publish = ? WHERE id = ?", pending, itemId));
    }

    /** Vieillit la ligne comme la production la stocke : created_at en TEXT (CURRENT_TIMESTAMP). */
    private void ageAsText(int itemId) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET created_at = ? WHERE id = ?", OLD_CREATED_AT_TEXT, itemId));
    }

    /** Vieillit la ligne avec un created_at INTEGER (millis), le type que Sarah lie pour une Date. */
    private void ageAsInteger(int itemId) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET created_at = ? WHERE id = ?", OLD_CREATED_AT_MILLIS, itemId));
    }

    private void assertReservedState(int itemId, StorageType storageType, Integer pending) throws SQLException {
        assertEquals(storageType.name(), storageTypeOf(itemId), "storage_type de " + itemId);
        if (pending == null) {
            assertNull(pendingOf(itemId), "pending_publish de " + itemId + " doit etre NULL");
        } else {
            assertEquals(pending.longValue(), pendingLongOf(itemId), "pending_publish de " + itemId);
        }
    }

    // ------------------------------------------------------------------------------------------
    // create(...)
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("sans etat initial : LISTED et pending_publish = 0")
        void create_default_isListedWithNoPending() throws Exception {
            UUID seller = newPlayer("Seller");

            int itemId = items().create(seller, ItemType.AUCTION, BigDecimal.TEN, EXPIRES_IN_ONE_HOUR, economy);

            assertTrue(itemId > 0, "l'identifiant genere doit etre strictement positif");
            assertEquals(1, count(Tables.ITEMS));
            assertReservedState(itemId, StorageType.LISTED, 0);
        }

        @Test
        @DisplayName("avec DELETED : ligne RESERVEE, pending_publish = 1, invisible de select()")
        void create_deleted_isReserved() throws Exception {
            UUID seller = newPlayer("Seller");

            int itemId = items().create(seller, ItemType.AUCTION, BigDecimal.TEN, EXPIRES_IN_ONE_HOUR, economy, StorageType.DELETED);

            assertTrue(itemId > 0);
            assertReservedState(itemId, StorageType.DELETED, 1);
            assertTrue(items().select().isEmpty(), "une reservation ne doit pas etre une annonce vivante");
            assertTrue(items().select(itemId).isEmpty(), "une reservation ne doit pas se lire par id");
        }

        @Test
        @DisplayName("avec un etat initial autre que DELETED (EXPIRED) : pending_publish = 0")
        void create_expiredInitialState_hasNoPending() throws Exception {
            UUID seller = newPlayer("Seller");

            int itemId = items().create(seller, ItemType.AUCTION, BigDecimal.ONE, EXPIRES_IN_ONE_HOUR, economy, StorageType.EXPIRED);

            assertReservedState(itemId, StorageType.EXPIRED, 0);
        }

        @Test
        @DisplayName("ecrit toutes les colonnes : server_name, economy_name, price, expired_at, item_type, seller, buyer NULL")
        void create_writesEveryColumn() throws Exception {
            UUID seller = newPlayer("Seller");
            BigDecimal price = new BigDecimal("1234.56");
            long expiredAt = 1_900_000_000_123L;

            int itemId = items().create(seller, ItemType.BID, price, expiredAt, economy);

            assertEquals(MockPlugins.SERVER_NAME, queryString("SELECT server_name FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
            assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
            assertEquals("BID", queryString("SELECT item_type FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
            assertEquals(seller.toString(), queryString("SELECT seller_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
            assertNull(queryObject("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
            assertEquals(expiredAt, expiredAtOf(itemId), "expired_at doit etre stocke en millis");

            Object rawPrice = queryObject("SELECT price FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
            assertNotNull(rawPrice);
            assertEquals(0, price.compareTo(new BigDecimal(rawPrice.toString())), "price doit conserver les deux decimales : " + rawPrice);

            assertNotNull(queryString("SELECT created_at FROM " + Tables.ITEMS + " WHERE id = ?", itemId), "created_at doit etre rempli par defaut");
        }

        @Test
        @DisplayName("create(Player, ...) utilise l'UUID du joueur")
        void create_fromBukkitPlayer_usesPlayerUniqueId() throws Exception {
            UUID seller = newPlayer("Alice");
            Player player = MockPlugins.player("Alice");
            assertEquals(seller, player.getUniqueId(), "pre-condition : meme UUID deterministe");

            int itemId = items().create(player, ItemType.AUCTION, BigDecimal.TEN, EXPIRES_IN_ONE_HOUR, economy);

            assertEquals(seller.toString(), queryString("SELECT seller_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
            assertReservedState(itemId, StorageType.LISTED, 0);
        }

        @Test
        @DisplayName("deux creations rendent deux identifiants distincts et deux lignes")
        void create_twice_generatesDistinctIds() throws Exception {
            UUID seller = newPlayer("Seller");

            int first = listed(seller);
            int second = reserved(seller);

            assertNotEquals(first, second);
            assertEquals(2, count(Tables.ITEMS));
        }
    }

    // ------------------------------------------------------------------------------------------
    // markReservationCommitted
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("markReservationCommitted")
    class MarkCommitted {

        @Test
        @DisplayName("nominal : 1 -> 2, rend 1, la ligne reste DELETED")
        void markCommitted_reserved_movesToCommitted() throws Exception {
            int itemId = reserved(newPlayer("Seller"));

            assertEquals(1, items().markReservationCommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, 2);
        }

        @Test
        @DisplayName("idempotence : un second appel rend 0 et laisse pending a 2")
        void markCommitted_twice_secondReturnsZero() throws Exception {
            int itemId = committed(newPlayer("Seller"));

            assertEquals(0, items().markReservationCommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, 2);
        }

        @Test
        @DisplayName("id inconnu : 0")
        void markCommitted_unknownId_returnsZero() {
            assertEquals(0, items().markReservationCommitted(UNKNOWN_ID));
        }

        @Test
        @DisplayName("mauvais storage_type (LISTED avec pending 1) : 0 et rien ne bouge")
        void markCommitted_wrongStorageType_returnsZero() throws Exception {
            int itemId = listed(newPlayer("Seller"));
            setPending(itemId, 1);

            assertEquals(0, items().markReservationCommitted(itemId));

            assertReservedState(itemId, StorageType.LISTED, 1);
        }

        @Test
        @DisplayName("mauvais pending (0 sur DELETED) : 0")
        void markCommitted_pendingNone_returnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            setPending(itemId, 0);

            assertEquals(0, items().markReservationCommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, 0);
        }

        @Test
        @DisplayName("pending NULL (ligne anterieure) : 0, jamais consideree comme une reservation")
        void markCommitted_pendingNull_returnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            setPending(itemId, null);

            assertEquals(0, items().markReservationCommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, null);
        }

        @Test
        @DisplayName("ne touche que la ligne ciblee")
        void markCommitted_onlyTargetRow() throws Exception {
            UUID seller = newPlayer("Seller");
            int target = reserved(seller);
            int other = reserved(seller);

            assertEquals(1, items().markReservationCommitted(target));

            assertReservedState(other, StorageType.DELETED, 1);
        }
    }

    // ------------------------------------------------------------------------------------------
    // markReservationUncommitted
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("markReservationUncommitted")
    class MarkUncommitted {

        @Test
        @DisplayName("nominal : 2 -> 1, rend 1")
        void markUncommitted_committed_movesBackToReserved() throws Exception {
            int itemId = committed(newPlayer("Seller"));

            assertEquals(1, items().markReservationUncommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, 1);
        }

        @Test
        @DisplayName("idempotence : un second appel rend 0 et laisse pending a 1")
        void markUncommitted_twice_secondReturnsZero() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            assertEquals(1, items().markReservationUncommitted(itemId));

            assertEquals(0, items().markReservationUncommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, 1);
        }

        @Test
        @DisplayName("reservation non engagee (pending 1) : 0")
        void markUncommitted_reserved_returnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));

            assertEquals(0, items().markReservationUncommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, 1);
        }

        @Test
        @DisplayName("id inconnu : 0")
        void markUncommitted_unknownId_returnsZero() {
            assertEquals(0, items().markReservationUncommitted(UNKNOWN_ID));
        }

        @Test
        @DisplayName("mauvais storage_type (EXPIRED avec pending 2) : 0")
        void markUncommitted_wrongStorageType_returnsZero() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            setStorageType(itemId, StorageType.EXPIRED);

            assertEquals(0, items().markReservationUncommitted(itemId));

            assertReservedState(itemId, StorageType.EXPIRED, 2);
        }

        @Test
        @DisplayName("pending NULL : 0")
        void markUncommitted_pendingNull_returnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            setPending(itemId, null);

            assertEquals(0, items().markReservationUncommitted(itemId));

            assertReservedState(itemId, StorageType.DELETED, null);
        }

        @Test
        @DisplayName("aller-retour : reserve -> engage -> recule -> supprimable")
        void markUncommitted_thenDeleteReservation_succeeds() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            assertEquals(0, items().deleteReservation(itemId), "une reservation engagee ne doit pas etre supprimable");

            assertEquals(1, items().markReservationUncommitted(itemId));
            assertEquals(1, items().deleteReservation(itemId));

            assertEquals(0, rowsOf(itemId));
        }
    }

    // ------------------------------------------------------------------------------------------
    // publishListing
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("publishListing")
    class Publish {

        @Test
        @DisplayName("nominal : reservation engagee -> LISTED, pending 0, rend 1, visible de select()")
        void publish_committed_becomesListed() throws Exception {
            int itemId = committed(newPlayer("Seller"));

            assertEquals(1, items().publishListing(itemId));

            assertReservedState(itemId, StorageType.LISTED, 0);
            assertEquals(1, items().select().size());
            assertEquals(itemId, items().select(itemId).orElseThrow().id());
        }

        @Test
        @DisplayName("idempotence : un second appel rend 0, la ligne reste LISTED")
        void publish_twice_secondReturnsZero() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            assertEquals(1, items().publishListing(itemId));

            assertEquals(0, items().publishListing(itemId));

            assertReservedState(itemId, StorageType.LISTED, 0);
        }

        @Test
        @DisplayName("transition interdite : une reservation NON engagee (pending 1) ne se publie pas")
        void publish_reserved_isRefused() throws Exception {
            int itemId = reserved(newPlayer("Seller"));

            assertEquals(0, items().publishListing(itemId));

            assertReservedState(itemId, StorageType.DELETED, 1);
            assertTrue(items().select().isEmpty(), "rien ne doit etre devenu achetable");
        }

        @Test
        @DisplayName("id inconnu : 0")
        void publish_unknownId_returnsZero() {
            assertEquals(0, items().publishListing(UNKNOWN_ID));
        }

        @Test
        @DisplayName("mauvais storage_type (EXPIRED avec pending 2) : 0")
        void publish_wrongStorageType_returnsZero() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            setStorageType(itemId, StorageType.EXPIRED);

            assertEquals(0, items().publishListing(itemId));

            assertReservedState(itemId, StorageType.EXPIRED, 2);
        }

        @Test
        @DisplayName("pending NULL : 0")
        void publish_pendingNull_returnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            setPending(itemId, null);

            assertEquals(0, items().publishListing(itemId));

            assertReservedState(itemId, StorageType.DELETED, null);
        }

        @Test
        @DisplayName("ne modifie ni le prix, ni l'expiration, ni le vendeur")
        void publish_keepsOtherColumns() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = items().create(seller, ItemType.AUCTION, new BigDecimal("42.00"), 1_900_000_000_000L, economy, StorageType.DELETED);
            assertEquals(1, items().markReservationCommitted(itemId));

            assertEquals(1, items().publishListing(itemId));

            var dto = items().select(itemId).orElseThrow();
            assertEquals(seller, dto.seller_unique_id());
            assertEquals(0, new BigDecimal("42.00").compareTo(dto.price()));
            assertEquals(1_900_000_000_000L, dto.expired_at().getTime());
            assertEquals(StorageType.LISTED, dto.storage_type());
        }
    }

    // ------------------------------------------------------------------------------------------
    // makeReservationClaimable
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("makeReservationClaimable")
    class MakeClaimable {

        @Test
        @DisplayName("nominal : reservation engagee -> EXPIRED, expired_at = 0 (n'expire jamais), pending 0, rend 1")
        void makeClaimable_committed_becomesExpiredForever() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            assertNotEquals(0L, expiredAtOf(itemId), "pre-condition : la reservation portait une expiration");

            assertEquals(1, items().makeReservationClaimable(itemId));

            assertReservedState(itemId, StorageType.EXPIRED, 0);
            assertEquals(0L, expiredAtOf(itemId), "expired_at doit valoir 0 : reclamable sans limite");
            var dto = items().select(itemId).orElseThrow();
            assertEquals(StorageType.EXPIRED, dto.storage_type());
            assertEquals(0L, dto.expired_at().getTime());
        }

        @Test
        @DisplayName("idempotence : un second appel rend 0")
        void makeClaimable_twice_secondReturnsZero() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            assertEquals(1, items().makeReservationClaimable(itemId));

            assertEquals(0, items().makeReservationClaimable(itemId));

            assertReservedState(itemId, StorageType.EXPIRED, 0);
        }

        @Test
        @DisplayName("transition interdite : une reservation NON engagee (le vendeur a encore son lot) n'est pas rendue reclamable")
        void makeClaimable_reserved_isRefused() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            long expiredAt = expiredAtOf(itemId);

            assertEquals(0, items().makeReservationClaimable(itemId));

            assertReservedState(itemId, StorageType.DELETED, 1);
            assertEquals(expiredAt, expiredAtOf(itemId), "expired_at ne doit pas avoir ete reecrit");
        }

        @Test
        @DisplayName("id inconnu : 0")
        void makeClaimable_unknownId_returnsZero() {
            assertEquals(0, items().makeReservationClaimable(UNKNOWN_ID));
        }

        @Test
        @DisplayName("mauvais storage_type (LISTED avec pending 2) : 0")
        void makeClaimable_wrongStorageType_returnsZero() throws Exception {
            int itemId = listed(newPlayer("Seller"));
            setPending(itemId, 2);
            long expiredAt = expiredAtOf(itemId);

            assertEquals(0, items().makeReservationClaimable(itemId));

            assertReservedState(itemId, StorageType.LISTED, 2);
            assertEquals(expiredAt, expiredAtOf(itemId));
        }

        @Test
        @DisplayName("pending NULL : 0")
        void makeClaimable_pendingNull_returnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            setPending(itemId, null);

            assertEquals(0, items().makeReservationClaimable(itemId));

            assertReservedState(itemId, StorageType.DELETED, null);
        }

        @Test
        @DisplayName("apres mise en reclamable, la publication n'est plus possible")
        void makeClaimable_thenPublish_isRefused() throws Exception {
            int itemId = committed(newPlayer("Seller"));
            assertEquals(1, items().makeReservationClaimable(itemId));

            assertEquals(0, items().publishListing(itemId));

            assertReservedState(itemId, StorageType.EXPIRED, 0);
        }
    }

    // ------------------------------------------------------------------------------------------
    // deleteReservation
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteReservation")
    class DeleteReservation {

        @Test
        @DisplayName("nominal : reservation non engagee supprimee, rend 1")
        void delete_reserved_removesRow() throws Exception {
            int itemId = reserved(newPlayer("Seller"));

            assertEquals(1, items().deleteReservation(itemId));

            assertEquals(0, rowsOf(itemId));
            assertEquals(0, count(Tables.ITEMS));
        }

        @Test
        @DisplayName("supprime aussi les lignes auction_items du lot")
        void delete_reserved_removesContents() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = reserved(seller);
            addContents(seller, itemId, 3);
            assertEquals(3, contentsOf(itemId), "pre-condition : contenus inseres");

            assertEquals(1, items().deleteReservation(itemId));

            assertEquals(0, rowsOf(itemId));
            assertEquals(0, contentsOf(itemId), "les contenus doivent partir avec la ligne parente");
        }

        @Test
        @DisplayName("ne touche pas aux contenus des autres annonces")
        void delete_reserved_keepsOtherContents() throws Exception {
            UUID seller = newPlayer("Seller");
            int target = reserved(seller);
            int other = listed(seller);
            addContents(seller, target, 2);
            addContents(seller, other, 4);

            assertEquals(1, items().deleteReservation(target));

            assertEquals(0, contentsOf(target));
            assertEquals(4, contentsOf(other));
            assertEquals(1, rowsOf(other));
        }

        @Test
        @DisplayName("idempotence : un second appel rend 0")
        void delete_twice_secondReturnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            assertEquals(1, items().deleteReservation(itemId));

            assertEquals(0, items().deleteReservation(itemId));
        }

        @Test
        @DisplayName("transition interdite : une reservation ENGAGEE survit, avec ses contenus")
        void delete_committed_survivesWithContents() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = committed(seller);
            addContents(seller, itemId, 2);

            assertEquals(0, items().deleteReservation(itemId));

            assertEquals(1, rowsOf(itemId));
            assertReservedState(itemId, StorageType.DELETED, 2);
            assertEquals(2, contentsOf(itemId), "le DELETE parent n'a rien matche : les contenus doivent rester");
        }

        @Test
        @DisplayName("id inconnu : 0")
        void delete_unknownId_returnsZero() {
            assertEquals(0, items().deleteReservation(UNKNOWN_ID));
        }

        @Test
        @DisplayName("mauvais storage_type : une annonce LISTED avec pending 1 n'est pas supprimee")
        void delete_wrongStorageType_returnsZero() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = listed(seller);
            setPending(itemId, 1);
            addContents(seller, itemId, 1);

            assertEquals(0, items().deleteReservation(itemId));

            assertEquals(1, rowsOf(itemId));
            assertEquals(1, contentsOf(itemId));
        }

        @Test
        @DisplayName("pending 0 sur une ligne DELETED (annonce retiree normalement) : 0")
        void delete_pendingNone_returnsZero() throws Exception {
            int itemId = reserved(newPlayer("Seller"));
            setPending(itemId, 0);

            assertEquals(0, items().deleteReservation(itemId));

            assertEquals(1, rowsOf(itemId));
        }

        @Test
        @DisplayName("pending NULL (ligne anterieure) : 0, la ligne et ses contenus restent")
        void delete_pendingNull_returnsZero() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = reserved(seller);
            setPending(itemId, null);
            addContents(seller, itemId, 1);

            assertEquals(0, items().deleteReservation(itemId));

            assertEquals(1, rowsOf(itemId));
            assertEquals(1, contentsOf(itemId));
        }
    }

    // ------------------------------------------------------------------------------------------
    // recoverOrphanReservations
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("recoverOrphanReservations")
    class Sweep {

        @Test
        @DisplayName("table vide : bilan (0, [])")
        void sweep_emptyTable_returnsEmptySweep() {
            OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis());

            assertEquals(0, sweep.recovered());
            assertTrue(sweep.purged().isEmpty());
        }

        @Test
        @DisplayName("lignes recentes (createdBefore dans le passe) : rien n'est touche")
        void sweep_recentRows_areUntouched() throws Exception {
            UUID seller = newPlayer("Seller");
            int reservedId = reserved(seller);
            int committedId = committed(seller);
            addContents(seller, reservedId, 1);

            OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

            assertEquals(0, sweep.recovered());
            assertTrue(sweep.purged().isEmpty());
            assertReservedState(reservedId, StorageType.DELETED, 1);
            assertReservedState(committedId, StorageType.DELETED, 2);
            assertEquals(1, contentsOf(reservedId));
        }

        /**
         * Les lignes de production portent un {@code created_at} TEXT (DEFAULT CURRENT_TIMESTAMP de
         * SQLite). Sarah lie la borne {@code createdBefore} en {@code java.sql.Timestamp}, que
         * sqlite-jdbc stocke/compare en INTEGER (millis) ; SQLite classe tout INTEGER avant tout
         * TEXT, donc {@code created_at < ?} est toujours FAUX et le balayage ne recupere ni ne purge
         * jamais rien sur SQLite.
         */
        @Nested
        @DisplayName("avec created_at TEXT (format reel de production sur SQLite)")
        class WithTextCreatedAt {

            @Test
            @Disabled("BUG: sur SQLite created_at est TEXT (CURRENT_TIMESTAMP) alors que Sarah lie la Date en INTEGER millis, " +
                    "donc 'created_at < ?' ne matche jamais : aucune reservation engagee n'est recuperee - " +
                    "ItemRepository.java:301 (recoverCommittedReservations)")
            @DisplayName("reservation ENGAGEE vieille : EXPIRED, expired_at 0, pending 0, recovered = 1")
            void sweep_oldCommitted_isRecovered() throws Exception {
                UUID seller = newPlayer("Seller");
                int itemId = committed(seller);
                addContents(seller, itemId, 2);
                ageAsText(itemId);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(1, sweep.recovered());
                assertTrue(sweep.purged().isEmpty());
                assertReservedState(itemId, StorageType.EXPIRED, 0);
                assertEquals(0L, expiredAtOf(itemId));
                assertEquals(2, contentsOf(itemId), "le lot perdu par le vendeur doit rester reclamable");
            }

            @Test
            @Disabled("BUG: sur SQLite created_at est TEXT (CURRENT_TIMESTAMP) alors que Sarah lie la Date en INTEGER millis, " +
                    "donc 'created_at < ?' ne matche jamais : aucune reservation non engagee n'est purgee - " +
                    "ItemRepository.java:328 (purgeUncommittedReservations)")
            @DisplayName("reservation RESERVEE vieille : supprimee avec ses contenus, purged = [id]")
            void sweep_oldReserved_isPurgedWithContents() throws Exception {
                UUID seller = newPlayer("Seller");
                int itemId = reserved(seller);
                addContents(seller, itemId, 2);
                ageAsText(itemId);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(0, sweep.recovered());
                assertEquals(List.of(itemId), sweep.purged());
                assertEquals(0, rowsOf(itemId));
                assertEquals(0, contentsOf(itemId));
            }

            @Test
            @Disabled("BUG: sur SQLite created_at est TEXT (CURRENT_TIMESTAMP) alors que Sarah lie la Date en INTEGER millis, " +
                    "donc 'created_at < ?' ne matche jamais : le bilan du balayage reste (0, []) quel que soit l'age des lignes - " +
                    "ItemRepository.java:301 et :328")
            @DisplayName("bilan exact : melange de reservations vieilles, recentes, NULL, LISTED et EXPIRED")
            void sweep_mixedRows_exactReport() throws Exception {
                UUID seller = newPlayer("Seller");
                int oldCommittedA = committed(seller);
                int oldCommittedB = committed(seller);
                int oldReservedA = reserved(seller);
                int oldReservedB = reserved(seller);
                int recentCommitted = committed(seller);
                int recentReserved = reserved(seller);
                int oldLegacyNull = reserved(seller);
                int oldListed = listed(seller);
                int oldExpiredPending2 = listed(seller);
                for (int id : List.of(oldCommittedA, oldCommittedB, oldReservedA, oldReservedB, oldLegacyNull, oldListed, oldExpiredPending2)) {
                    ageAsText(id);
                }
                setPending(oldLegacyNull, null);
                setStorageType(oldExpiredPending2, StorageType.EXPIRED);
                setPending(oldExpiredPending2, 2);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(2, sweep.recovered());
                assertEquals(Set.of(oldReservedA, oldReservedB), Set.copyOf(sweep.purged()));
                assertEquals(2, sweep.purged().size());
                assertReservedState(oldCommittedA, StorageType.EXPIRED, 0);
                assertReservedState(oldCommittedB, StorageType.EXPIRED, 0);
                assertEquals(0, rowsOf(oldReservedA));
                assertEquals(0, rowsOf(oldReservedB));
                assertReservedState(recentCommitted, StorageType.DELETED, 2);
                assertReservedState(recentReserved, StorageType.DELETED, 1);
                assertReservedState(oldLegacyNull, StorageType.DELETED, null);
                assertReservedState(oldListed, StorageType.LISTED, 0);
                assertReservedState(oldExpiredPending2, StorageType.EXPIRED, 2);
            }
        }

        /**
         * Meme balayage avec un {@code created_at} INTEGER : la comparaison matche alors sur SQLite
         * et l'on peut verifier le TRI par {@code pending_publish}, qui est le coeur anti-duplication.
         * Ces tests ne prouvent PAS que la production fonctionne (ses lignes sont en TEXT, voir
         * {@link WithTextCreatedAt}) : ils verifient la logique de branchement du balayage.
         */
        @Nested
        @DisplayName("avec created_at INTEGER (verifie le tri par pending_publish)")
        class WithIntegerCreatedAt {

            @Test
            @DisplayName("reservation ENGAGEE vieille : EXPIRED, expired_at 0, pending 0, contenus conserves, recovered = 1")
            void sweep_oldCommitted_isRecovered() throws Exception {
                UUID seller = newPlayer("Seller");
                int itemId = committed(seller);
                addContents(seller, itemId, 2);
                ageAsInteger(itemId);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(1, sweep.recovered());
                assertTrue(sweep.purged().isEmpty());
                assertReservedState(itemId, StorageType.EXPIRED, 0);
                assertEquals(0L, expiredAtOf(itemId), "0 = n'expire jamais");
                assertEquals(2, contentsOf(itemId));
                assertEquals(1, items().select().size(), "le lot recupere doit redevenir une annonce vivante (reclamable)");
            }

            @Test
            @DisplayName("reservation RESERVEE vieille : supprimee avec ses contenus, purged = [id]")
            void sweep_oldReserved_isPurgedWithContents() throws Exception {
                UUID seller = newPlayer("Seller");
                int itemId = reserved(seller);
                addContents(seller, itemId, 2);
                ageAsInteger(itemId);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(0, sweep.recovered());
                assertEquals(List.of(itemId), sweep.purged());
                assertEquals(0, rowsOf(itemId));
                assertEquals(0, contentsOf(itemId));
            }

            @Test
            @DisplayName("pending NULL vieille : jamais touchee, ni recuperee ni purgee")
            void sweep_oldLegacyNull_isUntouched() throws Exception {
                UUID seller = newPlayer("Seller");
                int itemId = reserved(seller);
                setPending(itemId, null);
                addContents(seller, itemId, 1);
                ageAsInteger(itemId);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(0, sweep.recovered());
                assertTrue(sweep.purged().isEmpty());
                assertReservedState(itemId, StorageType.DELETED, null);
                assertEquals(1, contentsOf(itemId));
            }

            @Test
            @DisplayName("lignes LISTED / EXPIRED vieilles avec pending 1 ou 2 : jamais touchees (garde storage_type)")
            void sweep_oldNonDeletedRows_areUntouched() throws Exception {
                UUID seller = newPlayer("Seller");
                int listedPending1 = listed(seller);
                int listedPending2 = listed(seller);
                int expiredPending1 = listed(seller);
                int expiredPending2 = listed(seller);
                setPending(listedPending1, 1);
                setPending(listedPending2, 2);
                setStorageType(expiredPending1, StorageType.EXPIRED);
                setPending(expiredPending1, 1);
                setStorageType(expiredPending2, StorageType.EXPIRED);
                setPending(expiredPending2, 2);
                for (int id : List.of(listedPending1, listedPending2, expiredPending1, expiredPending2)) {
                    addContents(seller, id, 1);
                    ageAsInteger(id);
                }

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(0, sweep.recovered());
                assertTrue(sweep.purged().isEmpty());
                assertReservedState(listedPending1, StorageType.LISTED, 1);
                assertReservedState(listedPending2, StorageType.LISTED, 2);
                assertReservedState(expiredPending1, StorageType.EXPIRED, 1);
                assertReservedState(expiredPending2, StorageType.EXPIRED, 2);
                assertEquals(4, count(Tables.AUCTION_ITEMS));
            }

            @Test
            @DisplayName("borne createdBefore : une ligne vieille n'est pas touchee si la borne est plus ancienne qu'elle")
            void sweep_boundOlderThanRow_isUntouched() throws Exception {
                UUID seller = newPlayer("Seller");
                int committedId = committed(seller);
                int reservedId = reserved(seller);
                ageAsInteger(committedId);
                ageAsInteger(reservedId);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(OLD_CREATED_AT_MILLIS - 1_000L);

                assertEquals(0, sweep.recovered());
                assertTrue(sweep.purged().isEmpty());
                assertReservedState(committedId, StorageType.DELETED, 2);
                assertReservedState(reservedId, StorageType.DELETED, 1);
            }

            @Test
            @DisplayName("bilan exact sur un melange de lignes")
            void sweep_mixedRows_exactReport() throws Exception {
                UUID seller = newPlayer("Seller");
                int oldCommittedA = committed(seller);
                int oldCommittedB = committed(seller);
                int oldReservedA = reserved(seller);
                int oldReservedB = reserved(seller);
                int recentCommitted = committed(seller);
                int recentReserved = reserved(seller);
                int oldLegacyNull = reserved(seller);
                int oldListed = listed(seller);
                for (int id : List.of(oldCommittedA, oldCommittedB, oldReservedA, oldReservedB, oldLegacyNull, oldListed)) {
                    ageAsInteger(id);
                }
                setPending(oldLegacyNull, null);
                addContents(seller, oldReservedA, 1);
                addContents(seller, oldCommittedA, 1);

                OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(2, sweep.recovered());
                assertEquals(2, sweep.purged().size());
                assertEquals(Set.of(oldReservedA, oldReservedB), Set.copyOf(sweep.purged()));
                assertReservedState(oldCommittedA, StorageType.EXPIRED, 0);
                assertReservedState(oldCommittedB, StorageType.EXPIRED, 0);
                assertEquals(1, contentsOf(oldCommittedA));
                assertEquals(0, rowsOf(oldReservedA));
                assertEquals(0, rowsOf(oldReservedB));
                assertEquals(0, contentsOf(oldReservedA));
                assertReservedState(recentCommitted, StorageType.DELETED, 2);
                assertReservedState(recentReserved, StorageType.DELETED, 1);
                assertReservedState(oldLegacyNull, StorageType.DELETED, null);
                assertReservedState(oldListed, StorageType.LISTED, 0);
                assertFalse(items().select().stream().anyMatch(dto -> dto.storage_type() == StorageType.DELETED));
            }

            @Test
            @DisplayName("un second balayage ne trouve plus rien")
            void sweep_twice_secondIsEmpty() throws Exception {
                UUID seller = newPlayer("Seller");
                int committedId = committed(seller);
                int reservedId = reserved(seller);
                ageAsInteger(committedId);
                ageAsInteger(reservedId);
                OrphanReservationSweep first = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);
                assertEquals(1, first.recovered());
                assertEquals(List.of(reservedId), first.purged());

                OrphanReservationSweep second = items().recoverOrphanReservations(System.currentTimeMillis() - 60_000L);

                assertEquals(0, second.recovered());
                assertTrue(second.purged().isEmpty());
            }
        }
    }
}
