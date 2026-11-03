package fr.maxlego08.zauctionhouse.services;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class AdminRemovalTest extends ServiceTestBase {
    @ParameterizedTest
    @CsvSource({"false,false,false", "false,false,true", "false,true,false", "false,true,true",
                "true,false,false", "true,false,true", "true,true,false", "true,true,true"})
    void removalOptionsAreIndependentAndCannotDeliverTwice(boolean notify, boolean expire, boolean copy) throws Exception {
        var seller = newPlayer("Seller");
        newPlayer("Admin");
        var admin = player("Admin", true);
        var item = listItem(seller, "Seller", offlinePlayer(seller, "Seller", null));
        manager.adminRemoveItem(admin, seller, item, StorageType.LISTED, notify, expire, copy);
        assertNull(manager.getItem(StorageType.LISTED, item.getId()));
        assertEquals(expire ? "EXPIRED" : "DELETED", storageTypeOf(item.getId()));
        assertEquals(expire ? ItemStatus.REMOVED : ItemStatus.DELETED, item.getStatus());
        if (expire) {
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
            assertTrue(item.getExpiredAt().getTime() > System.currentTimeMillis());
        } else assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
        verify(admin.getInventory(), times(copy ? 1 : 0)).addItem(any(ItemStack.class));
        assertEquals(notify ? 1 : 0, logs().selectUnreadAdminRemovals(seller).size());
        manager.adminRemoveItem(admin, seller, item, StorageType.LISTED, notify, expire, copy);
        verify(admin.getInventory(), times(copy ? 1 : 0)).addItem(any(ItemStack.class));
        assertEquals(1, count(Tables.LOGS, "item_id = ? AND log_type = 'ADMIN_REMOVE'", item.getId()));
    }
    @Test void lockContentionDoesNotDeleteOrCopyTheListing() throws Exception {
        var seller = newPlayer("Seller");
        var admin = player("Admin", true);
        var item = listItem(seller, "Seller", offlinePlayer(seller, "Seller", null));
        var bridge = mockBridge(false, LockToken.noop());
        when(bridge.checkAvailability(item, StorageType.LISTED)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        useBridge(bridge);
        manager.adminRemoveItem(admin, seller, item, StorageType.LISTED, false, true, true);
        assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
        assertEquals("LISTED", storageTypeOf(item.getId()));
        verify(admin.getInventory(), never()).addItem(any(ItemStack.class));
    }
}
