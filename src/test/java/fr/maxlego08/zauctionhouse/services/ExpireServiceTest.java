package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionExpireEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExpireService} : transitions LISTED -> EXPIRED et EXPIRED/PURCHASED -> DELETED, garde
 * compare-and-set (une ligne deja bougee ailleurs n'est jamais ecrasee et son fantome memoire
 * est purge), filtrage des lots, et chemin cluster (verrou + relecture autoritaire).
 */
class ExpireServiceTest extends ServiceTestBase {

    private ExpireService service;
    private UUID sellerId;
    private OfflinePlayer offlineSeller;

    @BeforeEach
    void setUpExpire() {
        this.service = new ExpireService(this.plugin, this.manager);
        this.sellerId = newPlayer("Seller");
        this.offlineSeller = offlinePlayer(this.sellerId, "Seller", null);
    }

    private TestAuctionItem listed() {
        return listItem(this.sellerId, "Seller", this.offlineSeller);
    }

    private void assertExpiredSoon(int itemId, long before) throws Exception {
        long expiredAt = expiredAtOf(itemId);
        long min = before + DEFAULT_EXPIRATION_SECONDS * 1000L - 5_000L;
        long max = System.currentTimeMillis() + DEFAULT_EXPIRATION_SECONDS * 1000L + 5_000L;
        assertTrue(expiredAt >= min && expiredAt <= max, "expired_at doit valoir maintenant + expire-expiration, trouve " + expiredAt);
    }

    private AuctionExpireEvent capturedExpireEvent() {
        ArgumentCaptor<AuctionExpireEvent> captor = ArgumentCaptor.forClass(AuctionExpireEvent.class);
        verify(this.pluginManager).callEvent(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("processExpiredItem")
    class SingleItem {

        @Test
        @DisplayName("LISTED, vendeur hors ligne : la ligne passe EXPIRED avec une nouvelle expiration, l'annonce change de conteneur, l'evenement est tire")
        void processExpiredItem_listedOfflineSeller_movesToExpired() throws Exception {
            TestAuctionItem item = listed();
            long before = System.currentTimeMillis();

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertExpiredSoon(item.getId(), before);
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.REMOVED, item.getStatus());
            assertEquals(item.getExpiredAt().getTime(), expiredAtOf(item.getId()), "memoire et base doivent porter la meme expiration");

            AuctionExpireEvent event = capturedExpireEvent();
            assertEquals(StorageType.LISTED, event.getType());
            assertEquals(List.of(item), event.getItems());
            verify(manager).clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
        }

        @Test
        @DisplayName("LISTED, vendeur en ligne : son cache est vide et l'expiration est calculee sur le joueur")
        void processExpiredItem_listedOnlineSeller_clearsSellerCache() throws Exception {
            Player sellerPlayer = player("Seller", true);
            TestAuctionItem item = listed();
            item.setSeller(offlinePlayer(sellerId, "Seller", sellerPlayer));
            long before = System.currentTimeMillis();

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertExpiredSoon(item.getId(), before);
            verify(manager).clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
        }

        @Test
        @DisplayName("expire-expiration a 0 : l'annonce expiree n'expire jamais (expired_at = 0)")
        void processExpiredItem_zeroExpiration_neverExpires() throws Exception {
            configureExpireExpiration(0L);
            TestAuctionItem item = listed();

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertEquals(0L, expiredAtOf(item.getId()));
            assertEquals(0L, item.getExpiredAt().getTime());
        }

        @Test
        @DisplayName("garde LISTED : une ligne deja PURCHASED par un autre serveur n'est pas touchee et le fantome n'est pas remis au vendeur")
        void processExpiredItem_rowAlreadyPurchased_dropsGhost() throws Exception {
            UUID buyer = UUID.randomUUID();
            TestAuctionItem item = listed();
            setRow(item.getId(), StorageType.PURCHASED, buyer);

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(buyer.toString(), buyerOf(item.getId()));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()), "l'item vendu ne doit pas devenir reclamable par le vendeur");
            assertEquals(ItemStatus.DELETED, item.getStatus());
        }

        @Test
        @DisplayName("statut DELETED (deja traite ailleurs) : l'instance quitte le store, aucune ecriture ni evenement")
        void processExpiredItem_alreadyDeleted_onlyRemovesFromStore() throws Exception {
            TestAuctionItem item = listed();
            item.setStatus(ItemStatus.DELETED);

            service.processExpiredItem(item, StorageType.LISTED);

            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals("LISTED", storageTypeOf(item.getId()));
            verify(pluginManager, never()).callEvent(any());
        }

        @Test
        @DisplayName("EXPIRED trop vieux : la ligne passe DELETED, l'annonce quitte le store et porte le statut DELETED")
        void processExpiredItem_expired_deletesRow() throws Exception {
            TestAuctionItem item = listed();
            moveTo(item, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);

            service.processExpiredItem(item, StorageType.EXPIRED);

            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
            assertEquals(StorageType.EXPIRED, capturedExpireEvent().getType());
        }

        @Test
        @DisplayName("PURCHASED non reclame trop vieux : la ligne passe DELETED en conservant l'acheteur")
        void processExpiredItem_purchased_deletesRowKeepingBuyer() throws Exception {
            UUID buyer = newPlayer("Buyer");
            TestAuctionItem item = listed();
            moveTo(item, StorageType.LISTED, StorageType.PURCHASED, ItemStatus.PURCHASED, buyer);

            service.processExpiredItem(item, StorageType.PURCHASED);

            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertEquals(buyer.toString(), buyerOf(item.getId()));
            assertNull(manager.getItem(StorageType.PURCHASED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
        }

        @Test
        @DisplayName("EXPIRED -> DELETED alors que la ligne est deja DELETED ailleurs : aucune erreur, store vide")
        void processExpiredItem_expiredRowAlreadyDeleted_noError() throws Exception {
            TestAuctionItem item = listed();
            moveTo(item, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);
            setRow(item.getId(), StorageType.DELETED, null);

            service.processExpiredItem(item, StorageType.EXPIRED);

            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("processExpiredItems (lots)")
    class Batch {

        @Test
        @DisplayName("liste vide : rien ne se passe")
        void processExpiredItems_empty_noop() {
            service.processExpiredItems(List.of(), StorageType.LISTED);

            verify(pluginManager, never()).callEvent(any());
            verify(manager, never()).clearPlayersCache(any(PlayerCacheKey[].class));
        }

        @Test
        @DisplayName("le lot est refiltre : null, DELETED, fantome et statut transitoire sont ecartes, les autres passent EXPIRED en un seul evenement")
        void processExpiredItems_filtersBatch() throws Exception {
            Player onlineSeller = player("Seller", true);
            TestAuctionItem offlineA = listed();
            TestAuctionItem offlineB = listed();
            TestAuctionItem online = listed();
            online.setSeller(offlinePlayer(sellerId, "Seller", onlineSeller));
            TestAuctionItem deleted = listed();
            deleted.setStatus(ItemStatus.DELETED);
            TestAuctionItem ghost = listed();
            manager.removeItem(StorageType.LISTED, ghost);
            TestAuctionItem transientItem = listed();
            transientItem.setStatus(ItemStatus.IS_BEING_PURCHASED);

            List<Item> batch = new ArrayList<>();
            batch.add(offlineA);
            batch.add(null);
            batch.add(deleted);
            batch.add(ghost);
            batch.add(transientItem);
            batch.add(offlineB);
            batch.add(online);
            long before = System.currentTimeMillis();

            service.processExpiredItems(batch, StorageType.LISTED);

            for (TestAuctionItem moved : List.of(offlineA, offlineB, online)) {
                assertEquals("EXPIRED", storageTypeOf(moved.getId()), "item " + moved.getId());
                assertExpiredSoon(moved.getId(), before);
                assertSame(moved, manager.getItem(StorageType.EXPIRED, moved.getId()));
                assertEquals(ItemStatus.REMOVED, moved.getStatus());
            }
            assertNull(manager.getItem(StorageType.LISTED, deleted.getId()), "l'instance DELETED quitte le store");
            assertEquals("LISTED", storageTypeOf(deleted.getId()));
            assertEquals("LISTED", storageTypeOf(ghost.getId()), "le fantome n'est pas traite");
            assertSame(transientItem, manager.getItem(StorageType.LISTED, transientItem.getId()));
            assertEquals(ItemStatus.IS_BEING_PURCHASED, transientItem.getStatus());
            assertEquals("LISTED", storageTypeOf(transientItem.getId()));

            AuctionExpireEvent event = capturedExpireEvent();
            assertEquals(StorageType.LISTED, event.getType());
            assertEquals(3, event.getItems().size());
            assertTrue(event.getItems().containsAll(List.of(offlineA, offlineB, online)));
            verify(manager).clearPlayerCache(onlineSeller, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
        }

        @Test
        @DisplayName("lot avec un perdant : les gagnants passent EXPIRED, le perdant (deja vendu) est purge et sa ligne reste intacte")
        void processExpiredItems_mixedWinnersAndLoser_dropsOnlyLoser() throws Exception {
            UUID buyer = UUID.randomUUID();
            TestAuctionItem winnerA = listed();
            TestAuctionItem loser = listed();
            TestAuctionItem winnerB = listed();
            setRow(loser.getId(), StorageType.PURCHASED, buyer);

            service.processExpiredItems(List.of(winnerA, loser, winnerB), StorageType.LISTED);

            assertEquals("EXPIRED", storageTypeOf(winnerA.getId()));
            assertEquals("EXPIRED", storageTypeOf(winnerB.getId()));
            assertSame(winnerA, manager.getItem(StorageType.EXPIRED, winnerA.getId()));
            assertSame(winnerB, manager.getItem(StorageType.EXPIRED, winnerB.getId()));

            assertEquals("PURCHASED", storageTypeOf(loser.getId()));
            assertEquals(buyer.toString(), buyerOf(loser.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, loser.getId()), "le perdant ne doit pas rester reclamable");
            assertNull(manager.getItem(StorageType.LISTED, loser.getId()));
            assertEquals(ItemStatus.DELETED, loser.getStatus());
        }

        @Test
        @DisplayName("lot d'items EXPIRED trop vieux : toutes les lignes passent DELETED et quittent le store")
        void processExpiredItems_expiredBatch_deletesRows() throws Exception {
            TestAuctionItem first = listed();
            TestAuctionItem second = listed();
            moveTo(first, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);
            moveTo(second, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);

            service.processExpiredItems(List.of(first, second), StorageType.EXPIRED);

            assertEquals("DELETED", storageTypeOf(first.getId()));
            assertEquals("DELETED", storageTypeOf(second.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, first.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, second.getId()));
            assertEquals(ItemStatus.DELETED, first.getStatus());
            assertEquals(ItemStatus.DELETED, second.getStatus());
            assertEquals(StorageType.EXPIRED, capturedExpireEvent().getType());
        }

        @Test
        @DisplayName("lot EXPIRED -> DELETED avec une ligne deja bougee ailleurs : les autres passent, aucune erreur")
        void processExpiredItems_expiredBatchWithLoser_othersStillDeleted() throws Exception {
            TestAuctionItem kept = listed();
            TestAuctionItem lost = listed();
            moveTo(kept, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);
            moveTo(lost, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);
            setRow(lost.getId(), StorageType.LISTED, null); // remis en vente par un autre noeud

            service.processExpiredItems(List.of(kept, lost), StorageType.EXPIRED);

            assertEquals("DELETED", storageTypeOf(kept.getId()));
            assertEquals("LISTED", storageTypeOf(lost.getId()), "la ligne qui n'est plus EXPIRED ne doit pas etre ecrasee");
            assertNull(manager.getItem(StorageType.EXPIRED, kept.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, lost.getId()));
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("mode cluster (bridge distribue)")
    class Distributed {

        private AuctionClusterBridge bridge;

        @BeforeEach
        void setUpBridge() {
            this.bridge = mockBridge(true, null);
            useBridge(this.bridge);
        }

        @Test
        @DisplayName("succes : verrou pris au nom du vendeur, ligne encore LISTED, bascule EXPIRED puis diffusion LISTED -> EXPIRED et liberation")
        void processExpiredItem_distributed_success() throws Exception {
            TestAuctionItem item = listed();
            long before = System.currentTimeMillis();

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertExpiredSoon(item.getId(), before);
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(ItemStatus.REMOVED, item.getStatus());
            verify(bridge).lockItem(item, sellerId, StorageType.LISTED);
            verify(bridge).removeItem(item, StorageType.LISTED, StorageType.EXPIRED);
            verify(bridge).releaseLock(eq(item), any(), eq(StorageType.LISTED));
            assertEquals(StorageType.LISTED, capturedExpireEvent().getType());
        }

        @Test
        @DisplayName("vendu sur un autre serveur : fantome purge, ligne intacte, rien diffuse, verrou relache")
        void processExpiredItem_distributed_soldElsewhere_dropsGhost() throws Exception {
            UUID buyer = UUID.randomUUID();
            TestAuctionItem item = listed();
            setRow(item.getId(), StorageType.PURCHASED, buyer);

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
            verify(bridge, never()).removeItem(any(), any(), any());
            verify(bridge).releaseLock(eq(item), any(), eq(StorageType.LISTED));
            verify(pluginManager, never()).callEvent(any());
        }

        @Test
        @DisplayName("deja EXPIRED par un autre noeud : la ligne n'est pas re-ecrite et le fantome LISTED est purge")
        void processExpiredItem_distributed_alreadyExpiredElsewhere_dropsGhost() throws Exception {
            TestAuctionItem item = listed();
            setRow(item.getId(), StorageType.EXPIRED, null);
            long foreignExpiredAt = expiredAtOf(item.getId());

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertEquals(foreignExpiredAt, expiredAtOf(item.getId()), "l'expiration posee par l'autre noeud ne doit pas etre ecrasee");
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            verify(bridge, never()).removeItem(any(), any(), any());
        }

        @Test
        @DisplayName("ligne illisible (economie inconnue) : expiration reportee, annonce intacte en memoire et en base")
        void processExpiredItem_distributed_unreadableRow_postponed() throws Exception {
            TestAuctionItem item = listed();
            execute("UPDATE " + Tables.ITEMS + " SET economy_name = 'unknown-economy' WHERE id = ?", item.getId());

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            verify(bridge, never()).removeItem(any(), any(), any());
            verify(bridge).releaseLock(eq(item), any(), eq(StorageType.LISTED));
        }

        @Test
        @DisplayName("verrou refuse (contention) : rien ne bouge et aucune liberation n'est tentee")
        void processExpiredItem_distributed_lockRefused_noop() throws Exception {
            this.bridge = mockBridge(true, LockToken.noop());
            useBridge(this.bridge);
            TestAuctionItem item = listed();

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            verify(bridge, never()).removeItem(any(), any(), any());
            verify(bridge, never()).releaseLock(any(), any(), any());
        }

        @Test
        @DisplayName("item indisponible sur le cluster (en cours d'achat) : aucun verrou tente, rien ne bouge")
        void processExpiredItem_distributed_unavailable_noop() throws Exception {
            when(bridge.checkAvailability(any(Item.class))).thenReturn(CompletableFuture.completedFuture(false));
            TestAuctionItem item = listed();

            service.processExpiredItem(item, StorageType.LISTED);

            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
            verify(bridge, never()).lockItem(any(), any(), any());
            verify(bridge, never()).removeItem(any(), any(), any());
        }

        @Test
        @DisplayName("lot LISTED en cluster : chaque item passe par le chemin verrouille et est diffuse individuellement")
        void processExpiredItems_distributed_routesEachItem() throws Exception {
            TestAuctionItem first = listed();
            TestAuctionItem second = listed();

            service.processExpiredItems(List.of(first, second), StorageType.LISTED);

            assertEquals("EXPIRED", storageTypeOf(first.getId()));
            assertEquals("EXPIRED", storageTypeOf(second.getId()));
            assertSame(first, manager.getItem(StorageType.EXPIRED, first.getId()));
            assertSame(second, manager.getItem(StorageType.EXPIRED, second.getId()));
            verify(bridge).removeItem(first, StorageType.LISTED, StorageType.EXPIRED);
            verify(bridge).removeItem(second, StorageType.LISTED, StorageType.EXPIRED);
            verify(bridge, times(2)).releaseLock(any(), any(), eq(StorageType.LISTED));
        }

        @Test
        @DisplayName("les conteneurs EXPIRED/PURCHASED ne passent pas par le cluster : DELETED direct")
        void processExpiredItem_distributed_expiredBucket_usesFastPath() throws Exception {
            TestAuctionItem item = listed();
            moveTo(item, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);

            service.processExpiredItem(item, StorageType.EXPIRED);

            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            verify(bridge, never()).lockItem(any(), any(), any());
        }
    }
}
