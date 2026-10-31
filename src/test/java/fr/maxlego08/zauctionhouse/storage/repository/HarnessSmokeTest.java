package fr.maxlego08.zauctionhouse.storage.repository;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie que le socle de test fonctionne de bout en bout : migrations, insertion d'une annonce
 * et de son contenu avec des ItemStack mockes, relecture.
 */
class HarnessSmokeTest extends DatabaseTestBase {

    @Test
    @DisplayName("les migrations de production creent toutes les tables")
    void migrations_createEveryTable() throws Exception {
        for (String table : List.of(Tables.PLAYERS, Tables.ITEMS, Tables.AUCTION_ITEMS, Tables.TRANSACTIONS, Tables.LOGS, Tables.OPTIONS, Tables.MIGRATION_STATE)) {
            assertTrue(tableExists(table), "table manquante : " + table);
        }
        assertEquals(11, count(MIGRATION_TABLE), "chaque migration doit etre enregistree une fois");
    }

    @Test
    @DisplayName("une annonce et son contenu peuvent etre crees puis relus")
    void createListing_roundTrip() throws Exception {
        UUID seller = newPlayer("Seller");
        long expiredAt = System.currentTimeMillis() + 60_000L;

        int itemId = items().create(seller, ItemType.AUCTION, new BigDecimal("12.50"), expiredAt, this.economy);
        var auctionItem = auctionItems().create(seller, "Seller", itemId, new BigDecimal("12.50"), expiredAt,
                List.of(TestItems.diamond(3), TestItems.diamond(2)), List.of(TestItems.encodedPayload(1), TestItems.encodedPayload(2)), this.economy);

        assertTrue(itemId > 0);
        assertEquals(itemId, auctionItem.getId());
        assertEquals(2, auctionItem.getItemStacks().size());
        assertEquals(2, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId));

        var dto = items().select(itemId).orElseThrow();
        assertEquals(StorageType.LISTED, dto.storage_type());
        assertEquals(seller, dto.seller_unique_id());
        assertEquals(0, new BigDecimal("12.50").compareTo(dto.price()));
        assertEquals("vault", dto.economy_name());
        assertEquals(expiredAt, dto.expired_at().getTime());
    }
}
