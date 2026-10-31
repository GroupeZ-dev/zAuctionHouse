package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.testutil.BulkItemRows;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transitions d'etat de {@link ItemRepository} : compare-and-set unitaire, chemins deprecies,
 * lots (avec et sans garde) et relecture par lot.
 * <p>
 * Chaque verdict est confirme par une lecture SQL BRUTE de la ligne, jamais seulement par la
 * valeur de retour.
 */
class ItemRepositoryUpdateTest extends DatabaseTestBase {

    /** Expiration fixe et lointaine (2030) : la relecture par Sarah la rend a la milliseconde. */
    private static final long EXPIRES_AT = 1_900_000_000_000L;

    private UUID seller;
    private UUID buyer;

    @BeforeEach
    void setUpPlayers() {
        this.seller = newPlayer("Seller");
        this.buyer = newPlayer("Buyer");
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------

    /** Cree une annonce LISTED en base et rend l'objet metier qui la represente. */
    private ZAuctionItem listed() {
        int id = items().create(this.seller, ItemType.AUCTION, BigDecimal.TEN, EXPIRES_AT, this.economy);
        return TestItems.auctionItem(this.plugin, id, this.seller, this.economy, EXPIRES_AT);
    }

    /** Cree une annonce LISTED puis la force dans l'etat voulu, comme l'aurait fait un autre noeud. */
    private ZAuctionItem inState(StorageType storageType) throws SQLException {
        ZAuctionItem item = listed();
        forceState(item.getId(), storageType);
        return item;
    }

    private ZAuctionItem withBuyer(ZAuctionItem item) {
        item.setBuyer(this.buyer, "Buyer");
        return item;
    }

    private List<Integer> bulkListed(int count) throws SQLException {
        try (Connection jdbc = jdbc()) {
            return BulkItemRows.insertItems(jdbc, TABLE_PREFIX, this.seller, count, StorageType.LISTED, EXPIRES_AT);
        }
    }

    private List<Item> itemsFor(List<Integer> ids) {
        List<Item> result = new ArrayList<>(ids.size());
        for (int id : ids) result.add(TestItems.auctionItem(this.plugin, id, this.seller, this.economy, EXPIRES_AT));
        return result;
    }

    private static List<String> idsOf(List<Integer> ids) {
        return ids.stream().map(String::valueOf).toList();
    }

    // ------------------------------------------------------------------------------------------
    // Lecture / ecriture SQL brute
    // ------------------------------------------------------------------------------------------

    private String storageTypeOf(int id) throws SQLException {
        return queryString("SELECT storage_type FROM " + Tables.ITEMS + " WHERE id = ?", id);
    }

    private String buyerOf(int id) throws SQLException {
        return queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", id);
    }

    private long expiredAtOf(int id) throws SQLException {
        return queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", id);
    }

    private long countInState(StorageType storageType) throws SQLException {
        return count(Tables.ITEMS, "storage_type = ?", storageType.name());
    }

    private void forceState(int id, StorageType storageType) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET storage_type = ? WHERE id = ?", storageType.name(), id));
    }

    private void forceBuyer(int id, UUID buyerUniqueId) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET buyer_unique_id = ? WHERE id = ?", buyerUniqueId.toString(), id));
    }

    // ==========================================================================================

    @Nested
    @DisplayName("updateItem(item, from, to) : compare-and-set")
    class CompareAndSet {

        @Test
        @DisplayName("rend 1 et deplace la ligne quand elle porte encore l'etat source")
        void updateItem_fromMatches_returnsOneAndMovesRow() throws Exception {
            ZAuctionItem item = listed();

            int affected = items().updateItem(item, StorageType.LISTED, StorageType.EXPIRED);

            assertEquals(1, affected);
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("rend 0 et ne touche pas la ligne quand elle a deja change d'etat")
        void updateItem_fromMismatch_returnsZeroAndLeavesRow() throws Exception {
            ZAuctionItem item = inState(StorageType.PURCHASED);

            int affected = items().updateItem(item, StorageType.LISTED, StorageType.EXPIRED);

            assertEquals(0, affected);
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(EXPIRES_AT, expiredAtOf(item.getId()));
        }

        @Test
        @DisplayName("rend 0 quand l'etat source attendu n'est pas celui de la ligne, meme si la destination est deja atteinte")
        void updateItem_alreadyAtDestination_returnsZero() throws Exception {
            ZAuctionItem item = inState(StorageType.EXPIRED);

            assertEquals(0, items().updateItem(item, StorageType.LISTED, StorageType.EXPIRED));
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
        }

        @ParameterizedTest(name = "LISTED -> {0}")
        @EnumSource(value = StorageType.class, names = {"PURCHASED", "EXPIRED", "DELETED"})
        @DisplayName("une seconde ecriture avec la meme source perd la course")
        void updateItem_secondWriteWithSameSource_losesRace(StorageType destination) throws Exception {
            ZAuctionItem item = withBuyer(listed());

            assertEquals(1, items().updateItem(item, StorageType.LISTED, destination));
            assertEquals(0, items().updateItem(item, StorageType.LISTED, destination));
            assertEquals(destination.name(), storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("vers PURCHASED : une ligne qui porte deja un acheteur ne se vend pas une seconde fois")
        void updateItem_toPurchased_rowAlreadyHasBuyer_returnsZero() throws Exception {
            UUID firstBuyer = newPlayer("FirstBuyer");
            ZAuctionItem item = withBuyer(listed());
            forceBuyer(item.getId(), firstBuyer);

            int affected = items().updateItem(item, StorageType.LISTED, StorageType.PURCHASED);

            assertEquals(0, affected);
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertEquals(firstBuyer.toString(), buyerOf(item.getId()), "l'acheteur d'origine doit etre conserve");
        }

        @Test
        @DisplayName("vers PURCHASED : ecrit buyer_unique_id quand l'item porte un acheteur")
        void updateItem_toPurchased_writesBuyer() throws Exception {
            ZAuctionItem item = withBuyer(listed());

            assertEquals(1, items().updateItem(item, StorageType.LISTED, StorageType.PURCHASED));

            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(ItemRepositoryUpdateTest.this.buyer.toString(), buyerOf(item.getId()));
        }

        @Test
        @DisplayName("vers PURCHASED : sans acheteur sur l'item, buyer_unique_id reste NULL")
        void updateItem_toPurchased_withoutBuyer_leavesBuyerNull() throws Exception {
            ZAuctionItem item = listed();

            assertEquals(1, items().updateItem(item, StorageType.LISTED, StorageType.PURCHASED));

            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertNull(buyerOf(item.getId()));
        }

        @Test
        @DisplayName("vers DELETED : n'ecrit PAS expired_at")
        void updateItem_toDeleted_doesNotWriteExpiredAt() throws Exception {
            ZAuctionItem item = listed();
            item.setExpiredAt(new Date(123_456L));

            assertEquals(1, items().updateItem(item, StorageType.LISTED, StorageType.DELETED));

            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertEquals(EXPIRES_AT, expiredAtOf(item.getId()), "expired_at doit garder sa valeur d'origine");
        }

        @Test
        @DisplayName("vers DELETED : ecrit buyer_unique_id quand l'item porte un acheteur")
        void updateItem_toDeleted_withBuyer_writesBuyer() throws Exception {
            ZAuctionItem item = withBuyer(listed());

            assertEquals(1, items().updateItem(item, StorageType.LISTED, StorageType.DELETED));

            assertEquals(ItemRepositoryUpdateTest.this.buyer.toString(), buyerOf(item.getId()));
        }

        @Test
        @DisplayName("vers DELETED : une ligne deja achetee peut etre supprimee (pas de garde whereNull)")
        void updateItem_toDeleted_rowWithBuyer_isAllowed() throws Exception {
            ZAuctionItem item = withBuyer(listed());
            forceBuyer(item.getId(), ItemRepositoryUpdateTest.this.buyer);
            forceState(item.getId(), StorageType.PURCHASED);

            assertEquals(1, items().updateItem(item, StorageType.PURCHASED, StorageType.DELETED));
            assertEquals("DELETED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("vers EXPIRED : ecrit expired_at = item.getExpiredAt()")
        void updateItem_toExpired_writesExpiredAt() throws Exception {
            ZAuctionItem item = listed();
            item.setExpiredAt(new Date(0));

            assertEquals(1, items().updateItem(item, StorageType.LISTED, StorageType.EXPIRED));

            assertEquals(0L, expiredAtOf(item.getId()));
        }

        @Test
        @DisplayName("vers PURCHASED : ecrit expired_at = item.getExpiredAt()")
        void updateItem_toPurchased_writesExpiredAt() throws Exception {
            ZAuctionItem item = listed();
            item.setExpiredAt(new Date(777_000L));

            assertEquals(1, items().updateItem(item, StorageType.LISTED, StorageType.PURCHASED));

            assertEquals(777_000L, expiredAtOf(item.getId()));
        }

        @Test
        @DisplayName("EXPIRED -> LISTED (remise en vente) ecrit la nouvelle expiration")
        void updateItem_expiredToListed_writesExpiredAt() throws Exception {
            ZAuctionItem item = inState(StorageType.EXPIRED);
            item.setExpiredAt(new Date(555_000L));

            assertEquals(1, items().updateItem(item, StorageType.EXPIRED, StorageType.LISTED));

            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertEquals(555_000L, expiredAtOf(item.getId()));
        }

        @Test
        @DisplayName("ne touche jamais une autre ligne")
        void updateItem_doesNotTouchOtherRows() throws Exception {
            ZAuctionItem moved = listed();
            ZAuctionItem untouched = listed();

            items().updateItem(moved, StorageType.LISTED, StorageType.EXPIRED);

            assertEquals("LISTED", storageTypeOf(untouched.getId()));
            assertEquals(1, countInState(StorageType.EXPIRED));
            assertEquals(1, countInState(StorageType.LISTED));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("updateItem(item, to) deprecie : ecrase sans garde, sauf vers EXPIRED")
    @SuppressWarnings("deprecation")
    class LegacyUpdateItem {

        @Test
        @DisplayName("LISTED -> EXPIRED fonctionne")
        void legacyUpdateItem_listedToExpired_moves() throws Exception {
            ZAuctionItem item = listed();

            items().updateItem(item, StorageType.EXPIRED);

            assertEquals("EXPIRED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("vers EXPIRED exige LISTED : une ligne PURCHASED ne redevient pas EXPIRED")
        void legacyUpdateItem_purchasedToExpired_isBlocked() throws Exception {
            ZAuctionItem item = inState(StorageType.PURCHASED);

            items().updateItem(item, StorageType.EXPIRED);

            assertEquals("PURCHASED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("vers EXPIRED exige LISTED : une ligne DELETED ne ressuscite pas")
        void legacyUpdateItem_deletedToExpired_isBlocked() throws Exception {
            ZAuctionItem item = inState(StorageType.DELETED);

            items().updateItem(item, StorageType.EXPIRED);

            assertEquals("DELETED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("vers DELETED ecrase une ligne PURCHASED sans garde")
        void legacyUpdateItem_toDeleted_overwritesPurchasedRow() throws Exception {
            ZAuctionItem item = inState(StorageType.PURCHASED);

            items().updateItem(item, StorageType.DELETED);

            assertEquals("DELETED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("vers DELETED ne touche pas expired_at")
        void legacyUpdateItem_toDeleted_keepsExpiredAt() throws Exception {
            ZAuctionItem item = listed();
            item.setExpiredAt(new Date(1L));

            items().updateItem(item, StorageType.DELETED);

            assertEquals(EXPIRES_AT, expiredAtOf(item.getId()));
        }

        @Test
        @DisplayName("vers PURCHASED ecrase un acheteur deja present (aucune garde whereNull)")
        void legacyUpdateItem_toPurchased_overwritesExistingBuyer() throws Exception {
            UUID firstBuyer = newPlayer("FirstBuyer");
            ZAuctionItem item = withBuyer(listed());
            forceBuyer(item.getId(), firstBuyer);

            items().updateItem(item, StorageType.PURCHASED);

            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(ItemRepositoryUpdateTest.this.buyer.toString(), buyerOf(item.getId()));
        }

        @Test
        @DisplayName("vers PURCHASED ecrit expired_at et laisse buyer_unique_id NULL sans acheteur")
        void legacyUpdateItem_toPurchased_withoutBuyer() throws Exception {
            ZAuctionItem item = listed();
            item.setExpiredAt(new Date(42_000L));

            items().updateItem(item, StorageType.PURCHASED);

            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(42_000L, expiredAtOf(item.getId()));
            assertNull(buyerOf(item.getId()));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("updateItems(Map) historique : sans garde de source")
    class LegacyUpdateItems {

        @Test
        @DisplayName("deplace chaque item de chaque destination")
        void legacyUpdateItems_movesEveryItemOfEveryBucket() throws Exception {
            ZAuctionItem a = listed();
            ZAuctionItem b = listed();
            ZAuctionItem c = withBuyer(listed());
            ZAuctionItem d = listed();
            Map<StorageType, List<Item>> byDestination = new LinkedHashMap<>();
            byDestination.put(StorageType.EXPIRED, List.of(a, b));
            byDestination.put(StorageType.PURCHASED, List.of(c));
            byDestination.put(StorageType.DELETED, List.of(d));

            items().updateItems(byDestination);

            assertEquals("EXPIRED", storageTypeOf(a.getId()));
            assertEquals("EXPIRED", storageTypeOf(b.getId()));
            assertEquals("PURCHASED", storageTypeOf(c.getId()));
            assertEquals(ItemRepositoryUpdateTest.this.buyer.toString(), buyerOf(c.getId()));
            assertEquals("DELETED", storageTypeOf(d.getId()));
        }

        @Test
        @DisplayName("une liste vide est ignoree sans erreur")
        void legacyUpdateItems_emptyList_isIgnored() throws Exception {
            ZAuctionItem untouched = listed();

            items().updateItems(Map.<StorageType, List<Item>>of(StorageType.EXPIRED, List.of()));

            assertEquals("LISTED", storageTypeOf(untouched.getId()));
        }

        @Test
        @DisplayName("une Map vide ne fait rien")
        void legacyUpdateItems_emptyMap_doesNothing() throws Exception {
            ZAuctionItem untouched = listed();

            items().updateItems(Map.<StorageType, List<Item>>of());

            assertEquals("LISTED", storageTypeOf(untouched.getId()));
        }

        @Test
        @DisplayName("la garde historique vers EXPIRED laisse une ligne PURCHASED intacte")
        void legacyUpdateItems_expiredGuard_skipsPurchasedRow() throws Exception {
            ZAuctionItem listedRow = listed();
            ZAuctionItem purchasedRow = inState(StorageType.PURCHASED);

            items().updateItems(Map.<StorageType, List<Item>>of(StorageType.EXPIRED, List.of(listedRow, purchasedRow)));

            assertEquals("EXPIRED", storageTypeOf(listedRow.getId()));
            assertEquals("PURCHASED", storageTypeOf(purchasedRow.getId()));
        }

        @Test
        @DisplayName("vers DELETED ecrase quel que soit l'etat courant")
        void legacyUpdateItems_toDeleted_overwritesAnyState() throws Exception {
            ZAuctionItem purchasedRow = inState(StorageType.PURCHASED);
            ZAuctionItem expiredRow = inState(StorageType.EXPIRED);

            items().updateItems(Map.<StorageType, List<Item>>of(StorageType.DELETED, List.of(purchasedRow, expiredRow)));

            assertEquals("DELETED", storageTypeOf(purchasedRow.getId()));
            assertEquals("DELETED", storageTypeOf(expiredRow.getId()));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("updateItems(Map, from) : lot compare-and-set")
    class BatchCompareAndSet {

        @Test
        @DisplayName("liste vide en retour quand toutes les lignes bougent")
        void updateItems_allMove_returnsEmpty() throws Exception {
            ZAuctionItem a = listed();
            ZAuctionItem b = listed();
            ZAuctionItem c = listed();

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.EXPIRED, List.of(a, b, c)), StorageType.LISTED);

            assertTrue(stale.isEmpty());
            assertEquals(3, countInState(StorageType.EXPIRED));
            assertEquals(0, countInState(StorageType.LISTED));
        }

        @Test
        @DisplayName("rend exactement les ids qui ont perdu la course et laisse leurs lignes intactes")
        void updateItems_losers_returnedAndLeftUntouched() throws Exception {
            ZAuctionItem a = listed();
            ZAuctionItem loser1 = inState(StorageType.PURCHASED);
            ZAuctionItem b = listed();
            ZAuctionItem loser2 = inState(StorageType.DELETED);
            ZAuctionItem c = listed();

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.EXPIRED, List.of(a, loser1, b, loser2, c)), StorageType.LISTED);

            assertEquals(Set.of(loser1.getId(), loser2.getId()), new HashSet<>(stale));
            assertEquals(2, stale.size(), "pas de doublon");
            assertEquals("EXPIRED", storageTypeOf(a.getId()));
            assertEquals("EXPIRED", storageTypeOf(b.getId()));
            assertEquals("EXPIRED", storageTypeOf(c.getId()));
            assertEquals("PURCHASED", storageTypeOf(loser1.getId()));
            assertEquals("DELETED", storageTypeOf(loser2.getId()));
        }

        @Test
        @DisplayName("Map avec une liste vide : rien ne bouge, liste vide en retour")
        void updateItems_emptyList_returnsEmptyAndTouchesNothing() throws Exception {
            ZAuctionItem untouched = listed();

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.PURCHASED, List.of()), StorageType.LISTED);

            assertTrue(stale.isEmpty());
            assertEquals("LISTED", storageTypeOf(untouched.getId()));
        }

        @Test
        @DisplayName("Map vide : liste vide en retour")
        void updateItems_emptyMap_returnsEmpty() {
            assertTrue(items().updateItems(Map.<StorageType, List<Item>>of(), StorageType.LISTED).isEmpty());
        }

        @Test
        @DisplayName("vers PURCHASED : formes mixtes (avec et sans acheteur) dans le meme lot")
        void updateItems_mixedShapes_toPurchased() throws Exception {
            ZAuctionItem withBuyerA = withBuyer(listed());
            ZAuctionItem noBuyer = listed();
            ZAuctionItem withBuyerB = withBuyer(listed());

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.PURCHASED, List.of(withBuyerA, noBuyer, withBuyerB)), StorageType.LISTED);

            assertTrue(stale.isEmpty());
            assertEquals(3, countInState(StorageType.PURCHASED));
            assertEquals(ItemRepositoryUpdateTest.this.buyer.toString(), buyerOf(withBuyerA.getId()));
            assertEquals(ItemRepositoryUpdateTest.this.buyer.toString(), buyerOf(withBuyerB.getId()));
            assertNull(buyerOf(noBuyer.getId()));
        }

        @Test
        @DisplayName("vers DELETED : formes mixtes, expired_at jamais reecrit")
        void updateItems_mixedShapes_toDeleted() throws Exception {
            ZAuctionItem withBuyerA = withBuyer(listed());
            ZAuctionItem noBuyer = listed();
            withBuyerA.setExpiredAt(new Date(1L));
            noBuyer.setExpiredAt(new Date(2L));

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.DELETED, List.of(withBuyerA, noBuyer)), StorageType.LISTED);

            assertTrue(stale.isEmpty());
            assertEquals("DELETED", storageTypeOf(withBuyerA.getId()));
            assertEquals("DELETED", storageTypeOf(noBuyer.getId()));
            assertEquals(ItemRepositoryUpdateTest.this.buyer.toString(), buyerOf(withBuyerA.getId()));
            assertNull(buyerOf(noBuyer.getId()));
            assertEquals(EXPIRES_AT, expiredAtOf(withBuyerA.getId()));
            assertEquals(EXPIRES_AT, expiredAtOf(noBuyer.getId()));
        }

        @Test
        @DisplayName("vers EXPIRED : expired_at est reecrit depuis l'item, l'acheteur de l'item est ignore")
        void updateItems_toExpired_writesExpiredAtAndIgnoresBuyer() throws Exception {
            ZAuctionItem item = withBuyer(listed());
            item.setExpiredAt(new Date(0));

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.EXPIRED, List.of(item)), StorageType.LISTED);

            assertTrue(stale.isEmpty());
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertEquals(0L, expiredAtOf(item.getId()));
            assertNull(buyerOf(item.getId()), "EXPIRED n'ecrit jamais buyer_unique_id");
        }

        @Test
        @DisplayName("vers PURCHASED : une ligne qui porte deja un acheteur est un perdant")
        void updateItems_toPurchased_rowWithBuyer_isLoser() throws Exception {
            UUID firstBuyer = newPlayer("FirstBuyer");
            ZAuctionItem alreadySold = withBuyer(listed());
            forceBuyer(alreadySold.getId(), firstBuyer);
            ZAuctionItem fresh = withBuyer(listed());

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.PURCHASED, List.of(alreadySold, fresh)), StorageType.LISTED);

            assertEquals(List.of(alreadySold.getId()), stale);
            assertEquals("LISTED", storageTypeOf(alreadySold.getId()));
            assertEquals(firstBuyer.toString(), buyerOf(alreadySold.getId()));
            assertEquals("PURCHASED", storageTypeOf(fresh.getId()));
        }

        @Test
        @DisplayName("plusieurs destinations dans le meme appel")
        void updateItems_multipleDestinations_inOneCall() throws Exception {
            ZAuctionItem toExpire = listed();
            ZAuctionItem toPurchase = withBuyer(listed());
            ZAuctionItem toDelete = listed();
            ZAuctionItem loser = inState(StorageType.EXPIRED);
            Map<StorageType, List<Item>> byDestination = new LinkedHashMap<>();
            byDestination.put(StorageType.EXPIRED, List.of(toExpire));
            byDestination.put(StorageType.PURCHASED, List.of(toPurchase, loser));
            byDestination.put(StorageType.DELETED, List.of(toDelete));

            List<Integer> stale = items().updateItems(byDestination, StorageType.LISTED);

            assertEquals(List.of(loser.getId()), stale);
            assertEquals("EXPIRED", storageTypeOf(toExpire.getId()));
            assertEquals("PURCHASED", storageTypeOf(toPurchase.getId()));
            assertEquals("DELETED", storageTypeOf(toDelete.getId()));
            assertEquals("EXPIRED", storageTypeOf(loser.getId()));
        }

        @Test
        @DisplayName("la source attendue peut etre autre que LISTED (EXPIRED -> DELETED)")
        void updateItems_fromExpired_toDeleted() throws Exception {
            ZAuctionItem expired = inState(StorageType.EXPIRED);
            ZAuctionItem stillListed = listed();

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.DELETED, List.of(expired, stillListed)), StorageType.EXPIRED);

            assertEquals(List.of(stillListed.getId()), stale);
            assertEquals("DELETED", storageTypeOf(expired.getId()));
            assertEquals("LISTED", storageTypeOf(stillListed.getId()));
        }

        @Test
        @DisplayName("lot de 1001 items (3 tranches de 500) avec des perdants au debut, au milieu et a la fin")
        void updateItems_largeBatch_chunksAndReportsLosersAcrossChunks() throws Exception {
            List<Integer> ids = bulkListed(1001);
            assertEquals(1001, ids.size());
            // Frontieres de tranche : [0..499] [500..999] [1000]
            List<Integer> loserIndexes = List.of(0, 250, 499, 500, 777, 1000);
            Set<Integer> loserIds = new HashSet<>();
            for (int index : loserIndexes) {
                forceState(ids.get(index), StorageType.PURCHASED);
                loserIds.add(ids.get(index));
            }

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.EXPIRED, itemsFor(ids)), StorageType.LISTED);

            assertEquals(loserIds, new HashSet<>(stale));
            assertEquals(loserIds.size(), stale.size(), "chaque perdant est rendu une seule fois");
            assertEquals(1001 - loserIds.size(), countInState(StorageType.EXPIRED));
            assertEquals(loserIds.size(), countInState(StorageType.PURCHASED));
            assertEquals(0, countInState(StorageType.LISTED));
        }

        @Test
        @DisplayName("lot de 501 items sans aucun perdant : liste vide, tout est passe")
        void updateItems_largeBatch_noLosers() throws Exception {
            List<Integer> ids = bulkListed(501);

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.DELETED, itemsFor(ids)), StorageType.LISTED);

            assertTrue(stale.isEmpty());
            assertEquals(501, countInState(StorageType.DELETED));
        }

        @Test
        @DisplayName("lot > 500 vers PURCHASED avec formes mixtes reparties dans les tranches")
        void updateItems_largeBatch_mixedShapesToPurchased() throws Exception {
            List<Integer> ids = bulkListed(600);
            List<Item> batch = itemsFor(ids);
            // un item sur trois porte un acheteur
            int withBuyer = 0;
            for (int index = 0; index < batch.size(); index += 3) {
                batch.get(index).setBuyer(ItemRepositoryUpdateTest.this.buyer, "Buyer");
                withBuyer++;
            }

            List<Integer> stale = items().updateItems(Map.<StorageType, List<Item>>of(StorageType.PURCHASED, batch), StorageType.LISTED);

            assertTrue(stale.isEmpty());
            assertEquals(600, countInState(StorageType.PURCHASED));
            assertEquals(withBuyer, count(Tables.ITEMS, "buyer_unique_id = ?", ItemRepositoryUpdateTest.this.buyer.toString()));
            assertEquals(600 - withBuyer, count(Tables.ITEMS, "buyer_unique_id IS NULL"));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("select(List<String> ids) : relecture par lot")
    class SelectByIds {

        @Test
        @DisplayName("liste vide : resultat vide")
        void selectIn_emptyList_returnsEmpty() {
            assertTrue(items().select(List.of()).isEmpty());
        }

        @Test
        @DisplayName("un seul id : une seule ligne, complete")
        void selectIn_singleId_returnsRow() {
            ZAuctionItem item = listed();
            listed(); // bruit

            List<ItemDTO> rows = items().select(List.of(String.valueOf(item.getId())));

            assertEquals(1, rows.size());
            ItemDTO dto = rows.getFirst();
            assertEquals(item.getId(), dto.id());
            assertEquals(StorageType.LISTED, dto.storage_type());
            assertEquals(ItemRepositoryUpdateTest.this.seller, dto.seller_unique_id());
            assertEquals(ItemType.AUCTION, dto.item_type());
            assertEquals("vault", dto.economy_name());
            assertEquals(EXPIRES_AT, dto.expired_at().getTime());
            assertNull(dto.buyer_unique_id());
        }

        @ParameterizedTest(name = "{0} identifiants")
        @ValueSource(ints = {500, 501, 1001})
        @DisplayName("pagine la clause IN par 500 sans perdre ni dupliquer de ligne")
        void selectIn_paginates(int size) throws Exception {
            List<Integer> ids = bulkListed(size);

            List<ItemDTO> rows = items().select(idsOf(ids));

            assertEquals(size, rows.size());
            assertEquals(new HashSet<>(ids), rows.stream().map(ItemDTO::id).collect(java.util.stream.Collectors.toSet()));
        }

        @Test
        @DisplayName("les identifiants inconnus sont ignores")
        void selectIn_unknownIds_ignored() {
            ZAuctionItem item = listed();

            List<ItemDTO> rows = items().select(List.of("999999", String.valueOf(item.getId()), "888888"));

            assertEquals(1, rows.size());
            assertEquals(item.getId(), rows.getFirst().id());
        }

        @Test
        @DisplayName("un identifiant repete ne rend la ligne qu'une fois")
        void selectIn_duplicateIds_returnsRowOnce() {
            ZAuctionItem item = listed();
            String id = String.valueOf(item.getId());

            assertEquals(1, items().select(List.of(id, id, id)).size());
        }

        @Test
        @DisplayName("inclut les lignes DELETED, contrairement a select() et select(int)")
        void selectIn_includesDeletedRows_unlikeSelect() {
            int reserved = items().create(ItemRepositoryUpdateTest.this.seller, ItemType.AUCTION, BigDecimal.ONE, EXPIRES_AT, ItemRepositoryUpdateTest.this.economy, StorageType.DELETED);
            ZAuctionItem visible = listed();

            List<ItemDTO> byIds = items().select(List.of(String.valueOf(reserved), String.valueOf(visible.getId())));

            assertEquals(2, byIds.size());
            assertTrue(byIds.stream().anyMatch(dto -> dto.id() == reserved && dto.storage_type() == StorageType.DELETED));
            assertTrue(items().select(reserved).isEmpty(), "select(int) filtre DELETED");
            assertFalse(items().select().stream().anyMatch(dto -> dto.id() == reserved), "select() filtre DELETED");
            assertTrue(items().select().stream().anyMatch(dto -> dto.id() == visible.getId()));
        }

        @Test
        @DisplayName("relit l'etat REEL apres une transition : c'est ce qui tranche une course perdue")
        void selectIn_reflectsCurrentState() throws Exception {
            ZAuctionItem item = withBuyer(listed());
            items().updateItem(item, StorageType.LISTED, StorageType.PURCHASED);

            ItemDTO dto = items().select(List.of(String.valueOf(item.getId()))).getFirst();

            assertEquals(StorageType.PURCHASED, dto.storage_type());
            assertEquals(ItemRepositoryUpdateTest.this.buyer, dto.buyer_unique_id());
        }
    }
}
