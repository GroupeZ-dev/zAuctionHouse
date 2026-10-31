package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SQL de {@link LogRepository} sur une base reelle.
 * <p>
 * Pour distinguer l'ordre des lignes, {@code created_at} est VIEILLI par UPDATE brut au format
 * TEXT de SQLite ({@code yyyy-MM-dd HH:mm:ss}), le meme que celui ecrit par
 * {@code DEFAULT CURRENT_TIMESTAMP}. Les lignes non vieillies portent la date courante, donc
 * toujours posterieure aux constantes ci-dessous.
 */
class LogRepositoryTest extends DatabaseTestBase {

    private static final String OLDEST = "2020-01-01 00:00:00";
    private static final String MIDDLE = "2021-06-15 12:30:00";
    private static final String NEWEST = "2022-12-31 23:59:59";

    /** Horodatage de lecture arbitraire mais fixe (2020-09-13), compare en millisecondes exactes. */
    private static final long READ_AT = 1_600_000_000_000L;
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    private UUID seller;
    private UUID buyer;
    private UUID other;
    private int itemA;
    private int itemB;

    @BeforeEach
    void setUpActors() {
        this.seller = newPlayer("Seller");
        this.buyer = newPlayer("Buyer");
        this.other = newPlayer("Other");
        long expiredAt = System.currentTimeMillis() + 60_000L;
        this.itemA = items().create(this.seller, ItemType.AUCTION, BigDecimal.TEN, expiredAt, this.economy);
        this.itemB = items().create(this.seller, ItemType.AUCTION, BigDecimal.ONE, expiredAt, this.economy);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /** Insere un log et rend son identifiant genere (createLog ne le rend pas). */
    private int log(LogType type, int itemId, UUID player, UUID target, Date readedAt) throws SQLException {
        logs().createLog(type, itemId, player, target, "payload", BigDecimal.TEN, "vault", null, readedAt);
        return (int) queryLong("SELECT MAX(id) FROM " + Tables.LOGS);
    }

    private int log(LogType type, int itemId, UUID player, UUID target) throws SQLException {
        return log(type, itemId, player, target, null);
    }

    /** Vieillit created_at en TEXT, le format reel de DEFAULT CURRENT_TIMESTAMP sous SQLite. */
    private void age(int logId, String createdAt) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.LOGS + " SET created_at = ? WHERE id = ?", createdAt, logId));
    }

    private Object readedAtOf(int logId) throws SQLException {
        return queryObject("SELECT readed_at FROM " + Tables.LOGS + " WHERE id = ?", logId);
    }

    private static List<Integer> ids(List<LogDTO> logs) {
        return logs.stream().map(LogDTO::id).toList();
    }

    // ------------------------------------------------------------------------------------------
    // createLog
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("createLog persiste chaque colonne et le DTO relu restitue log_type, UUID, prix et dates")
    void createLog_allFields_persistsEveryColumn() throws Exception {
        logs().createLog(LogType.PURCHASE, this.itemA, this.buyer, this.seller, "encoded-item", new BigDecimal("12.50"), "vault", "extra-data", new Date(READ_AT));

        assertEquals(1, count(Tables.LOGS));
        assertEquals("PURCHASE", queryString("SELECT log_type FROM " + Tables.LOGS));
        assertEquals(this.itemA, queryLong("SELECT item_id FROM " + Tables.LOGS));
        assertEquals(this.buyer.toString(), queryString("SELECT player_unique_id FROM " + Tables.LOGS));
        assertEquals(this.seller.toString(), queryString("SELECT target_unique_id FROM " + Tables.LOGS));
        assertEquals("encoded-item", queryString("SELECT itemstack FROM " + Tables.LOGS));
        assertEquals(0, new BigDecimal("12.50").compareTo(new BigDecimal(queryString("SELECT price FROM " + Tables.LOGS))));
        assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.LOGS));
        assertEquals("extra-data", queryString("SELECT additional_data FROM " + Tables.LOGS));
        assertEquals(READ_AT, queryLong("SELECT readed_at FROM " + Tables.LOGS));
        assertNotNull(queryString("SELECT created_at FROM " + Tables.LOGS), "created_at doit etre rempli par la base");

        List<LogDTO> rows = logs().selectByPlayer(this.buyer);
        assertEquals(1, rows.size());
        LogDTO dto = rows.get(0);
        assertTrue(dto.id() > 0);
        assertEquals(LogType.PURCHASE, dto.log_type());
        assertEquals(this.itemA, dto.item_id());
        assertEquals(this.buyer, dto.player_unique_id());
        assertEquals(this.seller, dto.target_unique_id());
        assertEquals("encoded-item", dto.itemstack());
        assertEquals(0, new BigDecimal("12.50").compareTo(dto.price()));
        assertEquals("vault", dto.economy_name());
        assertEquals("extra-data", dto.additional_data());
        assertNotNull(dto.readed_at());
        assertEquals(READ_AT, dto.readed_at().getTime());
        assertTrue(dto.isRead());
        assertNotNull(dto.created_at());
        assertNotNull(dto.updated_at());
    }

    @Test
    @DisplayName("createLog avec les champs optionnels a null ecrit NULL en base et 0 pour le prix")
    void createLog_nullOptionalFields_storesNullsAndZeroPrice() throws Exception {
        logs().createLog(LogType.SALE, this.itemA, this.seller, null, null, null, null, null, null);

        assertEquals(1, count(Tables.LOGS));
        assertNull(queryObject("SELECT target_unique_id FROM " + Tables.LOGS));
        assertNull(queryObject("SELECT itemstack FROM " + Tables.LOGS));
        assertNull(queryObject("SELECT economy_name FROM " + Tables.LOGS));
        assertNull(queryObject("SELECT additional_data FROM " + Tables.LOGS));
        assertNull(queryObject("SELECT readed_at FROM " + Tables.LOGS));
        assertEquals(0, queryLong("SELECT price FROM " + Tables.LOGS));

        LogDTO dto = logs().selectByPlayer(this.seller).get(0);
        assertEquals(LogType.SALE, dto.log_type());
        assertNull(dto.target_unique_id());
        assertNull(dto.itemstack());
        assertNull(dto.economy_name());
        assertNull(dto.additional_data());
        assertNull(dto.readed_at());
        assertFalse(dto.isRead());
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.price()));
        assertNotNull(dto.created_at());
    }

    @ParameterizedTest(name = "log_type {0}")
    @EnumSource(LogType.class)
    @DisplayName("createLog ecrit le nom de l'enum et le DTO le relit pour chaque LogType")
    void createLog_everyLogType_roundTripsEnum(LogType logType) throws Exception {
        logs().createLog(logType, this.itemA, this.seller, this.buyer, null, BigDecimal.ONE, "vault", null, null);

        assertEquals(logType.name(), queryString("SELECT log_type FROM " + Tables.LOGS));
        List<LogDTO> rows = logs().selectByPlayer(this.seller);
        assertEquals(1, rows.size());
        assertEquals(logType, rows.get(0).log_type());
    }

    // ------------------------------------------------------------------------------------------
    // selectByPlayer / selectByTarget
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("selectByPlayer ne rend que les logs ou le joueur est l'acteur")
    void selectByPlayer_returnsOnlyActorLogs() throws Exception {
        int first = log(LogType.SALE, this.itemA, this.seller, null);
        int second = log(LogType.REMOVE_LISTED, this.itemB, this.seller, null);
        log(LogType.PURCHASE, this.itemA, this.buyer, this.seller); // seller est CIBLE, pas acteur
        log(LogType.SALE, this.itemA, this.other, null);

        List<LogDTO> rows = logs().selectByPlayer(this.seller);

        assertEquals(2, rows.size());
        assertTrue(ids(rows).containsAll(List.of(first, second)));
        assertTrue(rows.stream().allMatch(row -> row.player_unique_id().equals(this.seller)));
    }

    @Test
    @DisplayName("selectByPlayer trie par created_at decroissant, independamment de l'ordre d'insertion")
    void selectByPlayer_ordersByCreatedAtDesc() throws Exception {
        int oldest = log(LogType.SALE, this.itemA, this.seller, null);
        int newest = log(LogType.SALE, this.itemA, this.seller, null);
        int middle = log(LogType.SALE, this.itemA, this.seller, null);
        age(oldest, OLDEST);
        age(newest, NEWEST);
        age(middle, MIDDLE);

        assertEquals(List.of(newest, middle, oldest), ids(logs().selectByPlayer(this.seller)));
    }

    @Test
    @DisplayName("selectByPlayer rend une liste vide pour un joueur sans log")
    void selectByPlayer_unknownPlayer_returnsEmpty() throws Exception {
        log(LogType.SALE, this.itemA, this.seller, null);

        assertTrue(logs().selectByPlayer(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("selectByTarget ne rend que les logs ou le joueur est la cible, tries par created_at decroissant")
    void selectByTarget_returnsOnlyTargetLogsOrderedDesc() throws Exception {
        int oldest = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int newest = log(LogType.PURCHASE, this.itemB, this.other, this.seller);
        log(LogType.SALE, this.itemA, this.seller, null);           // seller acteur, cible null
        log(LogType.PURCHASE, this.itemA, this.seller, this.buyer); // seller acteur, cible buyer
        age(oldest, OLDEST);
        age(newest, NEWEST);

        List<LogDTO> rows = logs().selectByTarget(this.seller);

        assertEquals(List.of(newest, oldest), ids(rows));
        assertTrue(rows.stream().allMatch(row -> this.seller.equals(row.target_unique_id())));
    }

    @Test
    @DisplayName("selectByTarget rend une liste vide pour un joueur jamais cible")
    void selectByTarget_unknownPlayer_returnsEmpty() throws Exception {
        log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);

        assertTrue(logs().selectByTarget(this.other).isEmpty());
        assertTrue(logs().selectByTarget(this.buyer).isEmpty(), "buyer est acteur, jamais cible");
    }

    // ------------------------------------------------------------------------------------------
    // selectByPlayerOrTarget
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("selectByPlayerOrTarget fusionne acteur et cible sans doublon : un log auto-cible compte une fois")
    void selectByPlayerOrTarget_mergesWithoutDuplicates() throws Exception {
        int selfTargeted = log(LogType.REMOVE_EXPIRED, this.itemA, this.seller, this.seller);
        int asActor = log(LogType.PURCHASE, this.itemA, this.seller, this.buyer);
        int asTarget = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);
        int unrelated = log(LogType.PURCHASE, this.itemB, this.buyer, this.other);

        List<LogDTO> rows = logs().selectByPlayerOrTarget(this.seller);

        assertEquals(3, rows.size(), "le log auto-cible ne doit apparaitre qu'une fois");
        List<Integer> ids = ids(rows);
        assertEquals(ids.stream().distinct().count(), ids.size());
        assertTrue(ids.containsAll(List.of(selfTargeted, asActor, asTarget)));
        assertFalse(ids.contains(unrelated));
    }

    @Test
    @DisplayName("selectByPlayerOrTarget trie le resultat fusionne par created_at decroissant")
    void selectByPlayerOrTarget_sortsMergedResultDesc() throws Exception {
        int oldestAsActor = log(LogType.SALE, this.itemA, this.seller, null);
        int middleAsActor = log(LogType.PURCHASE, this.itemA, this.seller, this.buyer);
        int newestAsTarget = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);
        age(oldestAsActor, OLDEST);
        age(middleAsActor, MIDDLE);
        age(newestAsTarget, NEWEST);

        // Le log le plus recent vient de la source "cible" : le tri doit traverser les deux listes.
        assertEquals(List.of(newestAsTarget, middleAsActor, oldestAsActor), ids(logs().selectByPlayerOrTarget(this.seller)));
    }

    @Test
    @DisplayName("selectByPlayerOrTarget rend une liste vide pour un joueur inconnu")
    void selectByPlayerOrTarget_unknownPlayer_returnsEmpty() throws Exception {
        log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);

        assertTrue(logs().selectByPlayerOrTarget(UUID.randomUUID()).isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // selectUnreadSales
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("selectUnreadSales ne rend que les PURCHASE non lus dont le joueur est la cible")
    void selectUnreadSales_filtersTypeTargetAndReadState() throws Exception {
        int unreadSale = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        log(LogType.PURCHASE, this.itemB, this.buyer, this.seller, new Date(READ_AT)); // deja lu
        log(LogType.SALE, this.itemA, this.buyer, this.seller);                        // mauvais type
        log(LogType.PURCHASE, this.itemA, this.buyer, this.other);                     // autre vendeur
        log(LogType.PURCHASE, this.itemA, this.seller, this.buyer);                    // seller acheteur, pas vendeur

        List<LogDTO> rows = logs().selectUnreadSales(this.seller);

        assertEquals(List.of(unreadSale), ids(rows));
        assertFalse(rows.get(0).isRead());
        assertEquals(LogType.PURCHASE, rows.get(0).log_type());
        assertEquals(this.seller, rows.get(0).target_unique_id());
    }

    @Test
    @DisplayName("selectUnreadSales trie par created_at decroissant")
    void selectUnreadSales_ordersByCreatedAtDesc() throws Exception {
        int oldest = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int newest = log(LogType.PURCHASE, this.itemB, this.other, this.seller);
        age(oldest, OLDEST);
        age(newest, NEWEST);

        assertEquals(List.of(newest, oldest), ids(logs().selectUnreadSales(this.seller)));
    }

    @Test
    @DisplayName("selectUnreadSales ne rend plus un log une fois marque lu")
    void selectUnreadSales_afterMarkAsRead_isEmpty() throws Exception {
        int sale = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        assertEquals(1, logs().selectUnreadSales(this.seller).size());

        logs().markAsRead(sale);

        assertTrue(logs().selectUnreadSales(this.seller).isEmpty());
    }

    @Test
    @DisplayName("selectUnreadSales rend une liste vide sans vente")
    void selectUnreadSales_noSale_returnsEmpty() {
        assertTrue(logs().selectUnreadSales(this.seller).isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // selectSalesHistory
    // ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "expireAfterMs = {0}")
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("selectSalesHistory sans limite temporelle : PURCHASE, cible, item_id > 0, lus comme non lus")
    void selectSalesHistory_noExpire_filtersTypeTargetAndItemId(long expireAfterMs) throws Exception {
        int unread = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int read = log(LogType.PURCHASE, this.itemB, this.other, this.seller, new Date(READ_AT));
        log(LogType.PURCHASE, 0, this.buyer, this.seller);          // item_id = 0 : exclu
        log(LogType.SALE, this.itemA, this.buyer, this.seller);      // mauvais type
        log(LogType.PURCHASE, this.itemA, this.buyer, this.other);   // autre vendeur
        log(LogType.PURCHASE, this.itemA, this.seller, this.buyer);  // seller acheteur

        List<Integer> ids = ids(logs().selectSalesHistory(this.seller, expireAfterMs));

        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(unread, read)));
    }

    @Test
    @DisplayName("selectSalesHistory trie par created_at decroissant")
    void selectSalesHistory_ordersByCreatedAtDesc() throws Exception {
        int oldest = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int newest = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);
        int middle = log(LogType.PURCHASE, this.itemA, this.other, this.seller);
        age(oldest, OLDEST);
        age(newest, NEWEST);
        age(middle, MIDDLE);

        assertEquals(List.of(newest, middle, oldest), ids(logs().selectSalesHistory(this.seller, 0)));
    }

    @Test
    @DisplayName("selectSalesHistory avec limite temporelle conserve les ventes recentes")
    void selectSalesHistory_positiveExpire_keepsRecentSales() throws Exception {
        int recent = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int aged = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);
        age(aged, OLDEST);

        List<Integer> ids = ids(logs().selectSalesHistory(this.seller, 7 * ONE_DAY_MS));

        assertTrue(ids.contains(recent), "une vente recente ne doit jamais disparaitre de l'historique");
    }

    @Test
    @Disabled("BUG: LogRepository.java:100 compare created_at (TEXT, rempli par DEFAULT CURRENT_TIMESTAMP) a un java.util.Date lie en INTEGER ; SQLite classe tout INTEGER avant tout TEXT donc 'created_at > ?' est TOUJOURS vrai : le filtre expireAfterMs est inerte et les ventes anciennes ne sortent jamais de l'historique")
    @DisplayName("selectSalesHistory avec limite temporelle exclut les ventes plus anciennes que la limite")
    void selectSalesHistory_positiveExpire_excludesAgedSales() throws Exception {
        int recent = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int aged = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);
        age(aged, OLDEST);

        List<Integer> ids = ids(logs().selectSalesHistory(this.seller, 7 * ONE_DAY_MS));

        assertEquals(List.of(recent), ids, "la vente de 2020 doit etre exclue d'un historique limite a 7 jours");
    }

    @Test
    @DisplayName("selectSalesHistory rend une liste vide pour un vendeur sans vente")
    void selectSalesHistory_noSale_returnsEmpty() throws Exception {
        log(LogType.PURCHASE, this.itemA, this.seller, this.buyer); // seller est acheteur ici

        assertTrue(logs().selectSalesHistory(this.seller, 0).isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // markAsRead
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("markAsRead(Collection) marque exactement les identifiants donnes, les autres restent non lus")
    void markAsRead_collection_marksOnlyGivenIds() throws Exception {
        long before = System.currentTimeMillis();
        int first = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int second = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int third = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);

        logs().markAsRead(List.of(first, third));

        assertNotNull(readedAtOf(first));
        assertNull(readedAtOf(second));
        assertNotNull(readedAtOf(third));
        assertTrue(queryLong("SELECT readed_at FROM " + Tables.LOGS + " WHERE id = ?", first) >= before);
        assertEquals(2, count(Tables.LOGS, "readed_at IS NOT NULL"));

        List<LogDTO> rows = logs().selectByTarget(this.seller);
        assertEquals(2, rows.stream().filter(LogDTO::isRead).count());
        assertTrue(rows.stream().filter(row -> row.id() == second).noneMatch(LogDTO::isRead));
    }

    @Test
    @DisplayName("markAsRead(Collection) avec une collection vide ou null ne fait rien et ne leve pas")
    void markAsRead_emptyOrNullCollection_isNoOp() throws Exception {
        int unread = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);

        assertDoesNotThrow(() -> logs().markAsRead(List.of()));
        assertDoesNotThrow(() -> logs().markAsRead((Collection<Integer>) null));

        assertNull(readedAtOf(unread));
        assertEquals(0, count(Tables.LOGS, "readed_at IS NOT NULL"));
    }

    @Test
    @DisplayName("markAsRead(Collection) avec un identifiant inconnu ne leve pas et ne modifie rien")
    void markAsRead_unknownId_isHarmless() throws Exception {
        int unread = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);

        assertDoesNotThrow(() -> logs().markAsRead(List.of(999_999)));

        assertNull(readedAtOf(unread));
    }

    @Test
    @DisplayName("markAsRead(int) marque un seul log")
    void markAsRead_single_marksOneLog() throws Exception {
        int target = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int untouched = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);

        logs().markAsRead(target);

        assertNotNull(readedAtOf(target));
        assertNull(readedAtOf(untouched));
    }

    @Test
    @DisplayName("markAsRead sur un log deja lu rafraichit readed_at")
    void markAsRead_alreadyRead_refreshesTimestamp() throws Exception {
        int alreadyRead = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller, new Date(READ_AT));

        logs().markAsRead(alreadyRead);

        assertTrue(queryLong("SELECT readed_at FROM " + Tables.LOGS + " WHERE id = ?", alreadyRead) > READ_AT);
    }

    @Test
    @DisplayName("markAsRead(Collection) au-dela de 500 identifiants marque toutes les lignes")
    void markAsRead_largeBatch_marksEveryRow() throws Exception {
        for (int index = 0; index < 520; index++) {
            logs().createLog(LogType.PURCHASE, this.itemA, this.buyer, this.seller, null, BigDecimal.ONE, "vault", null, null);
        }
        List<Integer> ids = new ArrayList<>();
        for (Object[] row : queryRows("SELECT id FROM " + Tables.LOGS)) {
            ids.add(((Number) row[0]).intValue());
        }
        assertEquals(520, ids.size());

        logs().markAsRead(ids);

        assertEquals(0, count(Tables.LOGS, "readed_at IS NULL"));
        assertEquals(520, count(Tables.LOGS, "readed_at IS NOT NULL"));
        assertTrue(logs().selectUnreadSales(this.seller).isEmpty());
    }

    // ------------------------------------------------------------------------------------------
    // markPurchaseLogsAsReadByItem
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("markPurchaseLogsAsReadByItem ne marque que les PURCHASE non lus de cet item et de ce vendeur")
    void markPurchaseLogsAsReadByItem_marksOnlyMatchingRows() throws Exception {
        int target = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int alreadyRead = log(LogType.PURCHASE, this.itemA, this.other, this.seller, new Date(READ_AT));
        int wrongType = log(LogType.SALE, this.itemA, this.buyer, this.seller);
        int otherItem = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);
        int otherSeller = log(LogType.PURCHASE, this.itemA, this.buyer, this.other);

        logs().markPurchaseLogsAsReadByItem(this.itemA, this.seller);

        assertNotNull(readedAtOf(target));
        assertEquals(READ_AT, queryLong("SELECT readed_at FROM " + Tables.LOGS + " WHERE id = ?", alreadyRead), "un log deja lu garde son horodatage");
        assertNull(readedAtOf(wrongType));
        assertNull(readedAtOf(otherItem));
        assertNull(readedAtOf(otherSeller));
    }

    @Test
    @DisplayName("markPurchaseLogsAsReadByItem sans ligne correspondante ne leve pas et ne modifie rien")
    void markPurchaseLogsAsReadByItem_noMatch_isHarmless() throws Exception {
        int unread = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);

        assertDoesNotThrow(() -> logs().markPurchaseLogsAsReadByItem(this.itemB, this.seller));
        assertDoesNotThrow(() -> logs().markPurchaseLogsAsReadByItem(this.itemA, this.other));

        assertNull(readedAtOf(unread));
    }

    // ------------------------------------------------------------------------------------------
    // deleteByPlayer
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("deleteByPlayer supprime les logs de l'acteur, rend le compte exact et epargne les autres")
    void deleteByPlayer_deletesOnlyActorLogsAndReturnsCount() throws Exception {
        log(LogType.SALE, this.itemA, this.seller, null);
        log(LogType.PURCHASE, this.itemA, this.seller, this.buyer);
        log(LogType.REMOVE_LISTED, this.itemB, this.seller, null);
        int asTargetOnly = log(LogType.PURCHASE, this.itemB, this.buyer, this.seller);
        int otherLog = log(LogType.SALE, this.itemB, this.other, null);

        long deleted = logs().deleteByPlayer(this.seller);

        assertEquals(3, deleted);
        assertEquals(2, count(Tables.LOGS));
        assertEquals(0, count(Tables.LOGS, "player_unique_id = ?", this.seller.toString()));
        assertEquals(1, count(Tables.LOGS, "id = ?", asTargetOnly), "un log ou le joueur est seulement cible est conserve");
        assertEquals(1, count(Tables.LOGS, "id = ?", otherLog));
    }

    @Test
    @DisplayName("deleteByPlayer rend 0 pour un joueur sans log et ne supprime rien")
    void deleteByPlayer_unknownPlayer_returnsZero() throws Exception {
        log(LogType.SALE, this.itemA, this.seller, null);

        assertEquals(0, logs().deleteByPlayer(UUID.randomUUID()));
        assertEquals(1, count(Tables.LOGS));
    }

    // ------------------------------------------------------------------------------------------
    // deleteOlderThan
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("deleteOlderThan conserve les logs recents et rend 0")
    void deleteOlderThan_recentRows_keepsThemAndReturnsZero() throws Exception {
        log(LogType.SALE, this.itemA, this.seller, null);
        log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);

        assertEquals(0, logs().deleteOlderThan(30 * ONE_DAY_MS));
        assertEquals(2, count(Tables.LOGS));
    }

    @Test
    @Disabled("BUG: LogRepository.java:173 compare created_at (TEXT, rempli par DEFAULT CURRENT_TIMESTAMP) a un java.util.Date lie en INTEGER ; SQLite classe tout INTEGER avant tout TEXT donc 'created_at < ?' n'est JAMAIS vrai : la purge des logs anciens ne supprime aucune ligne")
    @DisplayName("deleteOlderThan supprime les logs plus anciens que la limite et conserve les recents")
    void deleteOlderThan_agedRows_deletesOnlyThem() throws Exception {
        int aged = log(LogType.SALE, this.itemA, this.seller, null);
        int agedToo = log(LogType.PURCHASE, this.itemA, this.buyer, this.seller);
        int recent = log(LogType.SALE, this.itemB, this.seller, null);
        age(aged, OLDEST);
        age(agedToo, MIDDLE);

        long deleted = logs().deleteOlderThan(30 * ONE_DAY_MS);

        assertEquals(2, deleted);
        assertEquals(1, count(Tables.LOGS));
        assertEquals(1, count(Tables.LOGS, "id = ?", recent));
    }

    @Test
    @DisplayName("deleteOlderThan sur une table vide rend 0")
    void deleteOlderThan_emptyTable_returnsZero() throws Exception {
        assertEquals(0, logs().deleteOlderThan(ONE_DAY_MS));
        assertEquals(0, count(Tables.LOGS));
    }

    // ------------------------------------------------------------------------------------------
    // deleteMigrated
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("deleteMigrated ne supprime que les lignes marquees migrated_from_v3 et rend leur nombre")
    void deleteMigrated_deletesOnlyMarkedRows() throws Exception {
        logs().createLog(LogType.PURCHASE, this.itemA, this.buyer, this.seller, null, BigDecimal.ONE, "vault", LogRepository.MIGRATED_FROM_V3, null);
        logs().createLog(LogType.SALE, this.itemB, this.seller, null, null, BigDecimal.ONE, "vault", LogRepository.MIGRATED_FROM_V3, null);
        logs().createLog(LogType.SALE, this.itemA, this.seller, null, null, BigDecimal.ONE, "vault", "something-else", null);
        logs().createLog(LogType.SALE, this.itemA, this.seller, null, null, BigDecimal.ONE, "vault", null, null);
        // item_id = 0 SANS marqueur : l'ancien predicat "item_id = 0" l'aurait supprime a tort.
        logs().createLog(LogType.SALE, 0, this.seller, null, null, BigDecimal.ONE, "vault", null, null);

        long deleted = logs().deleteMigrated();

        assertEquals(2, deleted);
        assertEquals(3, count(Tables.LOGS));
        assertEquals(0, count(Tables.LOGS, "additional_data = ?", LogRepository.MIGRATED_FROM_V3));
        assertEquals(1, count(Tables.LOGS, "additional_data = ?", "something-else"));
        assertEquals(2, count(Tables.LOGS, "additional_data IS NULL"));
    }

    @Test
    @DisplayName("deleteMigrated sans ligne migree rend 0 et ne supprime rien")
    void deleteMigrated_noMarkedRow_returnsZero() throws Exception {
        log(LogType.SALE, this.itemA, this.seller, null);

        assertEquals(0, logs().deleteMigrated());
        assertEquals(1, count(Tables.LOGS));
    }

    @Test
    @DisplayName("le marqueur MIGRATED_FROM_V3 est celui ecrit par l'import V3")
    void migratedMarker_matchesV3ImportLiteral() {
        // V3MigrationService.createLogEntry ecrit le litteral "migrated_from_v3" : les deux
        // doivent rester identiques, sinon la purge /ah admin logs clear-migrated ne trouve rien.
        assertEquals("migrated_from_v3", LogRepository.MIGRATED_FROM_V3);
    }
}
