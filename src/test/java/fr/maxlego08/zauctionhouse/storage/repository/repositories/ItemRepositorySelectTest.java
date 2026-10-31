package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lectures de {@link ItemRepository} ({@code select()}, {@code select(int)}, {@code select(List)}),
 * mapping du record {@link ItemDTO}, et {@code restoreFromDeleted}.
 */
class ItemRepositorySelectTest extends DatabaseTestBase {

    private static final long EXPIRES_IN_ONE_HOUR = System.currentTimeMillis() + 3_600_000L;

    private int create(UUID seller, StorageType storageType) {
        return items().create(seller, ItemType.AUCTION, BigDecimal.TEN, EXPIRES_IN_ONE_HOUR, this.economy, storageType);
    }

    private void setStorageType(int itemId, StorageType storageType) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET storage_type = ? WHERE id = ?", storageType.name(), itemId));
    }

    private void setBuyer(int itemId, UUID buyer) throws SQLException {
        assertEquals(1, execute("UPDATE " + Tables.ITEMS + " SET buyer_unique_id = ? WHERE id = ?", buyer.toString(), itemId));
    }

    private String storageTypeOf(int itemId) throws SQLException {
        return queryString("SELECT storage_type FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    private long expiredAtOf(int itemId) throws SQLException {
        return queryLong("SELECT expired_at FROM " + Tables.ITEMS + " WHERE id = ?", itemId);
    }

    /** Doublure minimale d'un {@link Item} : seuls l'identifiant et l'expiration sont lus par le repository. */
    private static Item item(int id, long expiredAt) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getExpiredAt()).thenReturn(new Date(expiredAt));
        return item;
    }

    // ------------------------------------------------------------------------------------------
    // select()
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("select()")
    class SelectAll {

        @Test
        @DisplayName("table vide : liste vide")
        void select_emptyTable_returnsEmptyList() {
            assertTrue(items().select().isEmpty());
        }

        @Test
        @DisplayName("rend LISTED, EXPIRED et PURCHASED, exclut DELETED")
        void select_excludesDeleted() throws Exception {
            UUID seller = newPlayer("Seller");
            int listed = create(seller, StorageType.LISTED);
            int expired = create(seller, StorageType.EXPIRED);
            int purchased = create(seller, StorageType.PURCHASED);
            int deleted = create(seller, StorageType.LISTED);
            setStorageType(deleted, StorageType.DELETED);
            int reserved = create(seller, StorageType.DELETED);
            assertEquals(5, count(Tables.ITEMS), "pre-condition : cinq lignes en base");

            List<ItemDTO> rows = items().select();

            Set<Integer> ids = rows.stream().map(ItemDTO::id).collect(Collectors.toSet());
            assertEquals(Set.of(listed, expired, purchased), ids);
            assertFalse(ids.contains(deleted));
            assertFalse(ids.contains(reserved));
            assertTrue(rows.stream().noneMatch(dto -> dto.storage_type() == StorageType.DELETED));
        }

        @Test
        @DisplayName("ne rend rien quand toutes les lignes sont DELETED")
        void select_onlyDeletedRows_returnsEmptyList() throws Exception {
            UUID seller = newPlayer("Seller");
            create(seller, StorageType.DELETED);
            create(seller, StorageType.DELETED);

            assertTrue(items().select().isEmpty());
        }
    }

    // ------------------------------------------------------------------------------------------
    // select(int)
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("select(int)")
    class SelectById {

        @Test
        @DisplayName("id inconnu : Optional vide")
        void select_unknownId_returnsEmpty() {
            assertTrue(items().select(123_456).isEmpty());
        }

        @Test
        @DisplayName("rend la ligne LISTED demandee et aucune autre")
        void select_listed_returnsRow() throws Exception {
            UUID seller = newPlayer("Seller");
            int first = create(seller, StorageType.LISTED);
            int second = create(seller, StorageType.LISTED);

            Optional<ItemDTO> dto = items().select(second);

            assertTrue(dto.isPresent());
            assertEquals(second, dto.get().id());
            assertEquals(first, items().select(first).orElseThrow().id());
        }

        @Test
        @DisplayName("rend aussi EXPIRED et PURCHASED")
        void select_expiredAndPurchased_areVisible() throws Exception {
            UUID seller = newPlayer("Seller");
            int expired = create(seller, StorageType.EXPIRED);
            int purchased = create(seller, StorageType.PURCHASED);

            assertEquals(StorageType.EXPIRED, items().select(expired).orElseThrow().storage_type());
            assertEquals(StorageType.PURCHASED, items().select(purchased).orElseThrow().storage_type());
        }

        @Test
        @DisplayName("exclut DELETED, y compris une reservation")
        void select_deleted_returnsEmpty() throws Exception {
            UUID seller = newPlayer("Seller");
            int deleted = create(seller, StorageType.LISTED);
            setStorageType(deleted, StorageType.DELETED);
            int reserved = create(seller, StorageType.DELETED);

            assertTrue(items().select(deleted).isEmpty());
            assertTrue(items().select(reserved).isEmpty());
            assertEquals(2, count(Tables.ITEMS), "les lignes existent bel et bien, elles sont juste filtrees");
        }
    }

    // ------------------------------------------------------------------------------------------
    // select(List<String>)
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("select(List<String> ids)")
    class SelectIn {

        @Test
        @DisplayName("liste vide : liste vide, sans toucher la base")
        void selectIn_emptyList_returnsEmpty() {
            assertTrue(items().select(List.of()).isEmpty());
        }

        @Test
        @DisplayName("rend exactement les lignes demandees, ignore les identifiants inconnus")
        void selectIn_returnsRequestedRowsOnly() throws Exception {
            UUID seller = newPlayer("Seller");
            int a = create(seller, StorageType.LISTED);
            int b = create(seller, StorageType.LISTED);
            create(seller, StorageType.LISTED); // non demande

            List<ItemDTO> rows = items().select(List.of(String.valueOf(a), String.valueOf(b), "999999"));

            assertEquals(Set.of(a, b), rows.stream().map(ItemDTO::id).collect(Collectors.toSet()));
            assertEquals(2, rows.size());
        }

        @Test
        @DisplayName("ne filtre PAS DELETED : la relecture de lot (findStale) doit voir l'etat reel")
        void selectIn_includesDeletedRows() throws Exception {
            UUID seller = newPlayer("Seller");
            int deleted = create(seller, StorageType.LISTED);
            setStorageType(deleted, StorageType.DELETED);

            List<ItemDTO> rows = items().select(List.of(String.valueOf(deleted)));

            assertEquals(1, rows.size());
            assertEquals(StorageType.DELETED, rows.get(0).storage_type());
        }

        @Test
        @DisplayName("un identifiant en double ne rend qu'une ligne")
        void selectIn_duplicateIds_returnsOneRow() throws Exception {
            UUID seller = newPlayer("Seller");
            int id = create(seller, StorageType.LISTED);

            List<ItemDTO> rows = items().select(List.of(String.valueOf(id), String.valueOf(id)));

            assertEquals(1, rows.size());
        }

        @ParameterizedTest(name = "{0} identifiants")
        @ValueSource(ints = {500, 501})
        @DisplayName("pagine la clause IN autour de la borne de 500 sans perdre aucune ligne")
        void selectIn_chunkBoundary_losesNothing(int amount) {
            UUID seller = newPlayer("Seller");
            List<String> ids = new ArrayList<>(amount + 1);
            for (int index = 0; index < amount; index++) {
                ids.add(String.valueOf(create(seller, StorageType.LISTED)));
            }
            ids.add("999999"); // inconnu : ne doit rien casser

            List<ItemDTO> rows = items().select(ids);

            assertEquals(amount, rows.size());
            assertEquals(amount, rows.stream().map(ItemDTO::id).distinct().count());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Mapping ItemDTO
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("mapping ItemDTO")
    class DtoMapping {

        @Test
        @DisplayName("item_type et storage_type sont mappes en enums, seller en UUID, buyer null, created_at non null")
        void dto_mapsEveryColumn() throws Exception {
            UUID seller = newPlayer("Seller");
            long expiredAt = 1_900_000_000_000L;
            int itemId = items().create(seller, ItemType.BID, new BigDecimal("99.99"), expiredAt, economy, StorageType.EXPIRED);

            ItemDTO dto = items().select(itemId).orElseThrow();

            assertEquals(itemId, dto.id());
            assertEquals(ItemType.BID, dto.item_type());
            assertEquals(StorageType.EXPIRED, dto.storage_type());
            assertEquals(seller, dto.seller_unique_id());
            assertNull(dto.buyer_unique_id());
            assertEquals(0, new BigDecimal("99.99").compareTo(dto.price()));
            assertEquals("vault", dto.economy_name());
            assertEquals(MockPlugins.SERVER_NAME, dto.server_name());
            assertNotNull(dto.created_at(), "created_at est rempli par defaut et doit etre lu");
            assertNotNull(dto.expired_at());
            assertEquals(expiredAt, dto.expired_at().getTime());
        }

        @Test
        @DisplayName("buyer_unique_id est mappe en UUID quand il est renseigne")
        void dto_mapsBuyer() throws Exception {
            UUID seller = newPlayer("Seller");
            UUID buyer = newPlayer("Buyer");
            int itemId = create(seller, StorageType.PURCHASED);
            setBuyer(itemId, buyer);

            ItemDTO dto = items().select(itemId).orElseThrow();

            assertEquals(buyer, dto.buyer_unique_id());
            assertEquals(seller, dto.seller_unique_id());
        }

        @Test
        @DisplayName("chaque ItemType est relu a l'identique")
        void dto_mapsEveryItemType() {
            UUID seller = newPlayer("Seller");
            for (ItemType itemType : ItemType.values()) {
                int itemId = items().create(seller, itemType, BigDecimal.ONE, EXPIRES_IN_ONE_HOUR, economy);
                assertEquals(itemType, items().select(itemId).orElseThrow().item_type());
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // restoreFromDeleted
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("restoreFromDeleted")
    class RestoreFromDeleted {

        @Test
        @DisplayName("DELETED -> EXPIRED : rend 1, reecrit expired_at depuis l'item, redevient visible")
        void restore_deletedToExpired_returnsOne() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = create(seller, StorageType.LISTED);
            setStorageType(itemId, StorageType.DELETED);
            long newExpiredAt = 1_950_000_000_000L;

            int affected = items().restoreFromDeleted(item(itemId, newExpiredAt), StorageType.EXPIRED);

            assertEquals(1, affected);
            assertEquals("EXPIRED", storageTypeOf(itemId));
            assertEquals(newExpiredAt, expiredAtOf(itemId), "expired_at doit etre reecrit depuis l'item");
            assertEquals(StorageType.EXPIRED, items().select(itemId).orElseThrow().storage_type());
        }

        @Test
        @DisplayName("DELETED -> PURCHASED : rend 1 et conserve l'acheteur")
        void restore_deletedToPurchased_keepsBuyer() throws Exception {
            UUID seller = newPlayer("Seller");
            UUID buyer = newPlayer("Buyer");
            int itemId = create(seller, StorageType.PURCHASED);
            setBuyer(itemId, buyer);
            setStorageType(itemId, StorageType.DELETED);

            int affected = items().restoreFromDeleted(item(itemId, EXPIRES_IN_ONE_HOUR), StorageType.PURCHASED);

            assertEquals(1, affected);
            assertEquals("PURCHASED", storageTypeOf(itemId));
            assertEquals(buyer.toString(), queryString("SELECT buyer_unique_id FROM " + Tables.ITEMS + " WHERE id = ?", itemId));
            assertEquals(buyer, items().select(itemId).orElseThrow().buyer_unique_id());
        }

        @Test
        @DisplayName("idempotence : un second appel rend 0 et ne reecrit pas expired_at")
        void restore_twice_secondReturnsZero() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = create(seller, StorageType.LISTED);
            setStorageType(itemId, StorageType.DELETED);
            assertEquals(1, items().restoreFromDeleted(item(itemId, 1_950_000_000_000L), StorageType.EXPIRED));

            int affected = items().restoreFromDeleted(item(itemId, 1_960_000_000_000L), StorageType.EXPIRED);

            assertEquals(0, affected);
            assertEquals("EXPIRED", storageTypeOf(itemId));
            assertEquals(1_950_000_000_000L, expiredAtOf(itemId));
        }

        @Test
        @DisplayName("ligne non DELETED (LISTED) : rend 0 et ne touche rien")
        void restore_listedRow_returnsZero() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = create(seller, StorageType.LISTED);
            long expiredAt = expiredAtOf(itemId);

            int affected = items().restoreFromDeleted(item(itemId, 1_950_000_000_000L), StorageType.EXPIRED);

            assertEquals(0, affected);
            assertEquals("LISTED", storageTypeOf(itemId));
            assertEquals(expiredAt, expiredAtOf(itemId));
        }

        @Test
        @DisplayName("ligne deja bougee ailleurs (PURCHASED) : rend 0, la course est perdue proprement")
        void restore_rowMovedElsewhere_returnsZero() throws Exception {
            UUID seller = newPlayer("Seller");
            int itemId = create(seller, StorageType.PURCHASED);

            assertEquals(0, items().restoreFromDeleted(item(itemId, EXPIRES_IN_ONE_HOUR), StorageType.EXPIRED));
            assertEquals("PURCHASED", storageTypeOf(itemId));
        }

        @Test
        @DisplayName("id inconnu : rend 0")
        void restore_unknownId_returnsZero() {
            assertEquals(0, items().restoreFromDeleted(item(123_456, EXPIRES_IN_ONE_HOUR), StorageType.EXPIRED));
        }

        @Test
        @DisplayName("ne touche que la ligne ciblee")
        void restore_onlyTargetRow() throws Exception {
            UUID seller = newPlayer("Seller");
            int target = create(seller, StorageType.LISTED);
            int other = create(seller, StorageType.LISTED);
            setStorageType(target, StorageType.DELETED);
            setStorageType(other, StorageType.DELETED);

            assertEquals(1, items().restoreFromDeleted(item(target, EXPIRES_IN_ONE_HOUR), StorageType.EXPIRED));

            assertEquals("EXPIRED", storageTypeOf(target));
            assertEquals("DELETED", storageTypeOf(other));
        }
    }
}
