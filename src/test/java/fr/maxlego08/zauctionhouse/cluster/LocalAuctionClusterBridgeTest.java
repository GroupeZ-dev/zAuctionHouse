package fr.maxlego08.zauctionhouse.cluster;

import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class LocalAuctionClusterBridgeTest {

    private LocalAuctionClusterBridge bridge;
    private Item item;
    private Item otherItem;
    private final UUID buyer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        this.bridge = new LocalAuctionClusterBridge();
        this.item = item(1);
        this.otherItem = item(2);
    }

    private static Item item(int id) {
        Item item = mock(Item.class);
        lenient().when(item.getId()).thenReturn(id);
        return item;
    }

    @Test
    @DisplayName("un item jamais verrouille est disponible")
    void checkAvailability_unlocked_isTrue() {
        assertTrue(bridge.checkAvailability(item).join());
        assertTrue(bridge.checkAvailability(item, StorageType.LISTED).join());
    }

    @Test
    @DisplayName("lockItem rend un jeton acquis portant l'id de l'item et l'acheteur, et rend l'item indisponible")
    void lockItem_firstLock_acquires() {
        LockToken token = bridge.lockItem(item, buyer, StorageType.LISTED).join();

        assertTrue(token.isAcquired());
        assertTrue(token.value().startsWith("item:1:" + buyer + ":"));
        assertFalse(bridge.checkAvailability(item).join());
        assertTrue(bridge.checkAvailability(otherItem).join(), "le verrou est par item");
    }

    @Test
    @DisplayName("un second lockItem sur le meme item rend le jeton noop (contention), jamais un future en erreur")
    void lockItem_secondLock_returnsNoop() {
        LockToken first = bridge.lockItem(item, buyer, StorageType.LISTED).join();
        CompletableFuture<LockToken> secondFuture = bridge.lockItem(item, UUID.randomUUID(), StorageType.LISTED);

        assertFalse(secondFuture.isCompletedExceptionally());
        LockToken second = secondFuture.join();
        assertTrue(second.isNoop());
        assertFalse(second.isAcquired());
        assertTrue(first.isAcquired());
        assertFalse(bridge.checkAvailability(item).join(), "le premier verrou est intact");
    }

    @Test
    @DisplayName("deux acquisitions successives produisent des jetons differents")
    void lockItem_tokensAreUnique() {
        LockToken first = bridge.lockItem(item, buyer, StorageType.LISTED).join();
        bridge.unlockItem(item, first, StorageType.LISTED).join();
        LockToken second = bridge.lockItem(item, buyer, StorageType.LISTED).join();
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("unlockItem avec le bon jeton libere l'item")
    void unlockItem_rightToken_releases() {
        LockToken token = bridge.lockItem(item, buyer, StorageType.LISTED).join();
        assertNull(bridge.unlockItem(item, token, StorageType.LISTED).join());
        assertTrue(bridge.checkAvailability(item).join());
        assertTrue(bridge.lockItem(item, buyer, StorageType.LISTED).join().isAcquired());
    }

    @Test
    @DisplayName("unlockItem avec un jeton etranger ne casse pas le verrou vivant")
    void unlockItem_foreignToken_doesNotRelease() {
        bridge.lockItem(item, buyer, StorageType.LISTED).join();
        LockToken foreign = LockToken.issue(item, UUID.randomUUID());

        bridge.unlockItem(item, foreign, StorageType.LISTED).join();

        assertFalse(bridge.checkAvailability(item).join());
    }

    @Test
    @DisplayName("releaseLock rend true avec le bon jeton puis false une fois le verrou deja libere")
    void releaseLock_rightToken_trueThenFalse() {
        LockToken token = bridge.lockItem(item, buyer, StorageType.LISTED).join();

        assertTrue(bridge.releaseLock(item, token, StorageType.LISTED).join());
        assertFalse(bridge.releaseLock(item, token, StorageType.LISTED).join(), "deja libere");
        assertTrue(bridge.checkAvailability(item).join());
    }

    @Test
    @DisplayName("releaseLock rend false pour un mauvais jeton et laisse le verrou en place")
    void releaseLock_wrongToken_false() {
        bridge.lockItem(item, buyer, StorageType.LISTED).join();
        LockToken stale = LockToken.issue(item, buyer); // meme item, meme acheteur, autre nonce

        assertFalse(bridge.releaseLock(item, stale, StorageType.LISTED).join());
        assertFalse(bridge.checkAvailability(item).join());
    }

    @Test
    @DisplayName("releaseLock / unlockItem avec noop, unavailable ou null sont sans effet et rendent false")
    void releaseLock_sentinelTokens_areIgnored() {
        bridge.lockItem(item, buyer, StorageType.LISTED).join();

        assertFalse(bridge.releaseLock(item, LockToken.noop(), StorageType.LISTED).join());
        assertFalse(bridge.releaseLock(item, LockToken.unavailable(), StorageType.LISTED).join());
        assertFalse(bridge.releaseLock(item, null, StorageType.LISTED).join());
        bridge.unlockItem(item, null, StorageType.LISTED).join();
        assertFalse(bridge.checkAvailability(item).join());

        // Sur un item non verrouille, les sentinelles rendent aussi false.
        assertFalse(bridge.releaseLock(otherItem, LockToken.noop(), StorageType.LISTED).join());
    }

    @Test
    @DisplayName("checkAndLock (default) acquiert sur un item libre, et ne rend jamais un jeton acquis sur un item verrouille")
    void checkAndLock_default() {
        LockToken first = bridge.checkAndLock(item, buyer, StorageType.LISTED).join();
        assertTrue(first.isAcquired());

        LockToken second = bridge.checkAndLock(item, UUID.randomUUID(), StorageType.LISTED).join();
        assertFalse(second.isAcquired());
        // L'implementation par defaut traduit l'indisponibilite en sentinelle unavailable.
        assertTrue(second.isUnavailable());
    }

    @Test
    @DisplayName("les notifications et removeItem completent normalement sans toucher aux verrous")
    void notifications_completeWithoutSideEffects() {
        LockToken token = bridge.lockItem(item, buyer, StorageType.LISTED).join();
        Player player = mock(Player.class);

        assertTrue(bridge.notifyItemBought(player, item).isDone());
        assertNull(bridge.notifyItemBought(player, item).join());
        assertNull(bridge.notifyItemListed(item).join());
        assertNull(bridge.notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.PURCHASED).join());
        assertNull(bridge.removeItem(item, StorageType.LISTED).join());
        assertNull(bridge.removeItem(item, StorageType.LISTED, StorageType.DELETED).join());

        assertFalse(bridge.checkAvailability(item).join(), "removeItem ne libere pas le verrou");
        assertTrue(bridge.releaseLock(item, token, StorageType.LISTED).join());
    }

    @Test
    @DisplayName("les defaults mono-serveur : pas de bail, pas de prolongation, detention optimiste, non distribue")
    void defaults_singleServer() {
        LockToken token = bridge.lockItem(item, buyer, StorageType.LISTED).join();

        assertEquals(Duration.ZERO, bridge.lockLeaseDuration());
        assertFalse(bridge.renewLock(item, token, StorageType.LISTED).join());
        assertTrue(bridge.isHeldBy(item, token, StorageType.LISTED).join());
        assertFalse(bridge.isDistributed());
    }

    @Test
    @DisplayName("des items d'identifiants differents sont verrouilles independamment")
    void locks_areIndependentPerItemId() {
        LockToken a = bridge.lockItem(item, buyer, StorageType.LISTED).join();
        LockToken b = bridge.lockItem(otherItem, buyer, StorageType.PURCHASED).join();
        assertTrue(a.isAcquired());
        assertTrue(b.isAcquired());

        assertTrue(bridge.releaseLock(item, a, StorageType.LISTED).join());
        assertFalse(bridge.checkAvailability(otherItem).join());
        assertFalse(bridge.releaseLock(otherItem, a, StorageType.PURCHASED).join(), "le jeton de a ne libere pas b");
        assertTrue(bridge.releaseLock(otherItem, b, StorageType.PURCHASED).join());
    }
}
