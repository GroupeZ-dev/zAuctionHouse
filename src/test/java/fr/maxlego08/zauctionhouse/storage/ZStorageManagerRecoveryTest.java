package fr.maxlego08.zauctionhouse.storage;

import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.testutil.ListingFixtures;
import fr.maxlego08.zauctionhouse.testutil.LogCapture;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recuperation des reservations de vente orphelines au demarrage
 * ({@code ZStorageManager.recoverOrphanReservations}, appelee par {@code onEnable} apres les
 * migrations et avant le chargement du catalogue).
 * <p>
 * Deux abandons OPPOSES :
 * <ul>
 *   <li>{@code pending_publish = 2} (engagee, le vendeur a perdu son lot) : la ligne devient
 *   EXPIRED, sans expiration, reclamable ;</li>
 *   <li>{@code pending_publish = 1} (reservee, le vendeur a encore son lot) : la ligne et ses
 *   contenus sont SUPPRIMES.</li>
 * </ul>
 * Seules les reservations plus vieilles que la fenetre de grace (10 minutes) sont touchees.
 * <p>
 * PIEGE SQLITE : {@code created_at} est rempli par {@code DEFAULT CURRENT_TIMESTAMP} (TEXT) alors
 * que la borne est une {@code java.util.Date} liee en INTEGER ; SQLite classe tout INTEGER avant
 * tout TEXT, donc {@code created_at < ?} n'est JAMAIS vrai sur une ligne reelle. Les scenarios
 * realistes (vieillissement en TEXT) sont donc desactives avec la mention du bug, et la machine
 * d'etats est verifiee a part avec un {@code created_at} INTEGER, comparable a la borne.
 */
class ZStorageManagerRecoveryTest extends StorageManagerTestBase {

    /**
     * Horodatage TEXT tel que SQLite l'ecrit lui-meme : la forme REELLE d'une ligne de production.
     */
    private static final String OLD_TEXT_TIMESTAMP = "2020-01-01 00:00:00";

    private UUID seller;

    @BeforeEach
    void setUpSeller() {
        this.seller = newPlayer("Seller");
    }

    /**
     * Reservation telle que {@code reserveAuctionItem} la laisse : DELETED, pending_publish = 1,
     * contenus inseres.
     */
    private int reserved(String... payloads) {
        return ListingFixtures.listing(items(), auctionItems(), this.economy, this.seller, StorageType.DELETED, payloads);
    }

    /**
     * Reservation ENGAGEE : le jalon pending_publish = 2 est pose par le chemin de production.
     */
    private int committed(String... payloads) {
        int itemId = reserved(payloads);
        assertEquals(1, items().markReservationCommitted(itemId));
        return itemId;
    }

    /**
     * Vieillit la ligne avec un horodatage TEXT, la forme reelle de created_at sous SQLite.
     */
    private void ageAsText(int itemId) throws Exception {
        execute("UPDATE " + Tables.ITEMS + " SET created_at = ? WHERE id = ?", OLD_TEXT_TIMESTAMP, itemId);
    }

    /**
     * Vieillit la ligne avec un horodatage INTEGER (millis), comparable a la borne liee par Sarah.
     */
    private void ageAsInteger(int itemId) throws Exception {
        execute("UPDATE " + Tables.ITEMS + " SET created_at = ? WHERE id = ?", 1_577_836_800_000L, itemId);
    }

    private String storageTypeOf(int itemId) throws Exception {
        return queryString("SELECT storage_type FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private Object pendingOf(int itemId) throws Exception {
        return queryObject("SELECT pending_publish FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private long expiredAtOf(int itemId) throws Exception {
        return queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private void assertRecovered(int itemId, int expectedContents) throws Exception {
        assertEquals("EXPIRED", storageTypeOf(itemId), "une reservation engagee doit redevenir reclamable");
        assertEquals(0, ((Number) pendingOf(itemId)).intValue());
        assertEquals(0L, expiredAtOf(itemId), "expired_at = 0 : n'expire jamais");
        assertEquals(expectedContents, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId), "les contenus doivent survivre");
        assertTrue(items().select(itemId).isPresent(), "la ligne est de nouveau visible");
    }

    private void assertPurged(int itemId) throws Exception {
        assertEquals(0, count(Tables.ITEMS, "id = ?", itemId), "une reservation non engagee doit disparaitre");
        assertEquals(0, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId), "ses contenus aussi");
    }

    private void assertUntouchedReservation(int itemId, int expectedPending, int expectedContents) throws Exception {
        assertEquals("DELETED", storageTypeOf(itemId));
        assertEquals(expectedPending, ((Number) pendingOf(itemId)).intValue());
        assertEquals(expectedContents, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId));
        assertTrue(items().select(itemId).isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // Scenarios realistes (created_at TEXT, comme en production) : BUG connu
    // ------------------------------------------------------------------------------------------

    @Test
    @Disabled("BUG: ItemRepository.recoverCommittedReservations compare created_at (TEXT, DEFAULT CURRENT_TIMESTAMP) a une java.util.Date liee en INTEGER ; sous SQLite INTEGER se classe avant TEXT donc 'created_at < ?' ne matche jamais et une reservation engagee orpheline n'est jamais rendue reclamable (ItemRepository.java, recoverCommittedReservations)")
    @DisplayName("au redemarrage, une reservation ENGAGEE orpheline redevient reclamable (EXPIRED, sans expiration)")
    void restart_oldCommittedReservation_becomesClaimable() throws Exception {
        int itemId = committed("payload-1", "payload-2");
        ageAsText(itemId);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            restartStorageManager();
            assertTrue(logs.contains(Level.WARNING, "1 listing reservation(s) were committed"));
        }

        assertRecovered(itemId, 2);
    }

    @Test
    @Disabled("BUG: ItemRepository.purgeUncommittedReservations compare created_at (TEXT, DEFAULT CURRENT_TIMESTAMP) a une java.util.Date liee en INTEGER ; sous SQLite INTEGER se classe avant TEXT donc 'created_at < ?' ne matche jamais et une reservation non engagee orpheline n'est jamais purgee (ItemRepository.java, purgeUncommittedReservations)")
    @DisplayName("au redemarrage, une reservation NON engagee orpheline est supprimee avec ses contenus")
    void restart_oldUncommittedReservation_isPurged() throws Exception {
        int itemId = reserved("payload-1");
        ageAsText(itemId);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            restartStorageManager();
            assertTrue(logs.contains(Level.SEVERE, "died BEFORE the seller handed over his items"));
        }

        assertPurged(itemId);
    }

    // ------------------------------------------------------------------------------------------
    // Machine d'etats du balayage (created_at INTEGER, comparable a la borne)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("[types comparables] au redemarrage : engagee -> EXPIRED reclamable, non engagee -> supprimee, journal distinct")
    void restart_comparableTimestamps_recoversCommittedAndPurgesUncommitted() throws Exception {
        int committed = committed("payload-1", "payload-2");
        int reserved = reserved("payload-3");
        int untouched = ListingFixtures.listed(items(), auctionItems(), this.economy, this.seller, "payload-4");
        ageAsInteger(committed);
        ageAsInteger(reserved);
        ageAsInteger(untouched);

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            restartStorageManager();
            assertTrue(logs.contains(Level.WARNING, "1 listing reservation(s) were committed"), () -> "WARNING attendu, recu : " + logs.messages(Level.WARNING));
            assertTrue(logs.contains(Level.SEVERE, "1 listing reservation(s) [" + reserved + "] died BEFORE"), () -> "SEVERE attendu, recu : " + logs.messages(Level.SEVERE));
        }

        assertRecovered(committed, 2);
        assertPurged(reserved);
        assertEquals("LISTED", storageTypeOf(untouched), "une annonce publiee n'est jamais touchee par le balayage");
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", untouched));
    }

    @Test
    @DisplayName("[types comparables] le balayage direct rend le bilan exact (recovered, purged)")
    void recoverOrphanReservations_comparableTimestamps_returnsSweep() throws Exception {
        int committedA = committed("payload-1");
        int committedB = committed("payload-2");
        int reservedA = reserved("payload-3");
        int reservedB = reserved("payload-4");
        for (int itemId : List.of(committedA, committedB, reservedA, reservedB)) ageAsInteger(itemId);

        ItemRepository.OrphanReservationSweep sweep = items().recoverOrphanReservations(System.currentTimeMillis() - 10L * 60L * 1000L);

        assertEquals(2, sweep.recovered());
        assertEquals(List.of(reservedA, reservedB), sweep.purged());
        assertRecovered(committedA, 1);
        assertRecovered(committedB, 1);
        assertPurged(reservedA);
        assertPurged(reservedB);
    }

    @Test
    @DisplayName("[types comparables] une ligne DELETED anterieure (pending_publish NULL) n'est ni ressuscitee ni supprimee")
    void restart_legacyDeletedRow_isNeverTouched() throws Exception {
        int legacy = reserved("payload-1");
        execute("UPDATE " + Tables.ITEMS + " SET pending_publish = NULL WHERE id = ?", legacy);
        ageAsInteger(legacy);

        restartStorageManager();

        assertEquals("DELETED", storageTypeOf(legacy));
        assertNull(pendingOf(legacy));
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", legacy));
    }

    @Test
    @DisplayName("[types comparables] une ligne non DELETED portant un jalon incoherent n'est pas touchee")
    void restart_nonDeletedRowWithPendingFlag_isNeverTouched() throws Exception {
        int listed = ListingFixtures.listed(items(), auctionItems(), this.economy, this.seller, "payload-1");
        execute("UPDATE " + Tables.ITEMS + " SET pending_publish = ? WHERE id = ?", ItemRepository.PENDING_RESERVED, listed);
        int purchased = ListingFixtures.listing(items(), auctionItems(), this.economy, this.seller, StorageType.PURCHASED, "payload-2");
        execute("UPDATE " + Tables.ITEMS + " SET pending_publish = ? WHERE id = ?", ItemRepository.PENDING_COMMITTED, purchased);
        ageAsInteger(listed);
        ageAsInteger(purchased);

        restartStorageManager();

        assertEquals("LISTED", storageTypeOf(listed));
        assertEquals(1, count(Tables.ITEMS, "id = ?", listed));
        assertEquals("PURCHASED", storageTypeOf(purchased));
        assertEquals(1, count(Tables.AUCTION_ITEMS, "item_id = ?", purchased));
    }

    // ------------------------------------------------------------------------------------------
    // Fenetre de grace
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("une reservation RECENTE (engagee ou non) n'est jamais touchee au redemarrage")
    void restart_recentReservations_areLeftAlone() throws Exception {
        int committed = committed("payload-1");
        int reserved = reserved("payload-2", "payload-3");

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            restartStorageManager();
            assertEquals(0, logs.count(Level.WARNING));
            assertEquals(0, logs.count(Level.SEVERE));
        }

        assertUntouchedReservation(committed, ItemRepository.PENDING_COMMITTED, 1);
        assertUntouchedReservation(reserved, ItemRepository.PENDING_RESERVED, 2);
    }

    @Test
    @DisplayName("[types comparables] une reservation vieille de 9 minutes est encore dans la fenetre de grace")
    void restart_reservationJustInsideGraceWindow_isLeftAlone() throws Exception {
        int reserved = reserved("payload-1");
        int committed = committed("payload-2");
        long nineMinutesAgo = System.currentTimeMillis() - 9L * 60L * 1000L;
        execute("UPDATE " + Tables.ITEMS + " SET created_at = ? WHERE id IN (?, ?)", nineMinutesAgo, reserved, committed);

        restartStorageManager();

        assertUntouchedReservation(reserved, ItemRepository.PENDING_RESERVED, 1);
        assertUntouchedReservation(committed, ItemRepository.PENDING_COMMITTED, 1);
    }

    @Test
    @DisplayName("[types comparables] une reservation vieille de 11 minutes est hors de la fenetre de grace")
    void restart_reservationJustOutsideGraceWindow_isSwept() throws Exception {
        int reserved = reserved("payload-1");
        int committed = committed("payload-2");
        long elevenMinutesAgo = System.currentTimeMillis() - 11L * 60L * 1000L;
        execute("UPDATE " + Tables.ITEMS + " SET created_at = ? WHERE id IN (?, ?)", elevenMinutesAgo, reserved, committed);

        restartStorageManager();

        assertPurged(reserved);
        assertRecovered(committed, 1);
    }

    @Test
    @DisplayName("base sans aucune reservation : le redemarrage ne journalise rien et ne touche a rien")
    void restart_noReservation_isSilent() throws Exception {
        int listed = ListingFixtures.listed(items(), auctionItems(), this.economy, this.seller, "payload-1");

        try (LogCapture logs = LogCapture.of(this.plugin.getLogger())) {
            restartStorageManager();
            assertEquals(0, logs.count(Level.WARNING));
            assertEquals(0, logs.count(Level.SEVERE));
        }

        assertEquals("LISTED", storageTypeOf(listed));
    }
}
