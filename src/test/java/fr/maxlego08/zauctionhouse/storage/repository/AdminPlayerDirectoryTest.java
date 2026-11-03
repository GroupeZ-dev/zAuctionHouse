package fr.maxlego08.zauctionhouse.storage.repository;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class AdminPlayerDirectoryTest extends DatabaseTestBase {
    private int listing(UUID seller, long expiry) {
        return items().create(seller, ItemType.AUCTION, BigDecimal.TEN, expiry, economy, StorageType.LISTED);
    }
    @Test void directoryIncludesActorsAndTargetsButExcludesPlayersWithoutActivity() {
        var seller = newPlayer("seller");
        var buyer = newPlayer("buyer");
        newPlayer("no_history");
        int id = listing(seller, System.currentTimeMillis() + 3600000);
        logs().createLog(LogType.PURCHASE, id, buyer, seller, null, BigDecimal.TEN, "vault", null, null);
        var result = players().selectAdminPlayers();
        assertEquals(2, result.size());
        var sale = result.stream().filter(p -> p.uniqueId().equals(seller)).findFirst().orElseThrow();
        var purchase = result.stream().filter(p -> p.uniqueId().equals(buyer)).findFirst().orElseThrow();
        assertEquals(1, sale.listed());
        assertEquals(1, sale.sold());
        assertEquals(0, sale.purchased());
        assertEquals(1, purchase.purchased());
        assertEquals(0, purchase.sold());
    }
    @Test void activeCountExcludesExpiredAndReservedListingsButIncludesPermanentListings() throws Exception {
        var seller = newPlayer("seller");
        listing(seller, System.currentTimeMillis() + 3600000);
        listing(seller, 0);
        listing(seller, System.currentTimeMillis() - 3600000);
        items().create(seller, ItemType.AUCTION, BigDecimal.TEN, System.currentTimeMillis() + 3600000, economy, StorageType.DELETED);
        var result = players().selectAdminPlayers();
        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().listed());
        assertEquals(0, result.getFirst().sold());
    }
    @Test void historySurvivesClaimedItemsAndDoesNotCountListingEventsAsSales() throws Exception {
        var seller = newPlayer("seller");
        var buyer = newPlayer("buyer");
        int id = listing(seller, System.currentTimeMillis() + 3600000);
        execute("UPDATE " + Tables.ITEMS + " SET storage_type = 'DELETED', buyer_unique_id = ? WHERE id = ?", buyer.toString(), id);
        logs().createLog(LogType.SALE, id, seller, null, null, BigDecimal.TEN, "vault", null, null);
        logs().createLog(LogType.PURCHASE, id, buyer, seller, null, BigDecimal.TEN, "vault", null, null);
        var result = players().selectAdminPlayers();
        assertEquals(2, result.size());
        assertEquals(1, result.stream().mapToLong(p -> p.sold()).sum());
        assertEquals(1, result.stream().mapToLong(p -> p.purchased()).sum());
        assertEquals(0, result.stream().mapToLong(p -> p.listed()).sum());
    }
}
