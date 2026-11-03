package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionPreRemovePurchasedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveExpiredItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemoveListedItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.remove.AuctionRemovePurchasedItemEvent;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.RemoveResult;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RemoveService} : les quatre entrees publiques de retrait, avec un vrai
 * {@code ZAuctionManager} et une vraie base SQLite derriere. Chaque test verifie l'etat de la
 * ligne {@code items} ET ce qui a reellement ete remis (ou non) au joueur.
 */
class RemoveServiceTest extends ServiceTestBase {

    private RemoveService service;
    private UUID sellerId;
    private Player player;
    private OfflinePlayer seller;

    @BeforeEach
    void setUpRemove() {
        this.service = new RemoveService(this.plugin, this.manager);
        this.sellerId = newPlayer("Seller");
        this.player = player("Seller", true);
        this.seller = offlinePlayer(this.sellerId, "Seller", this.player);
    }

    private TestAuctionItem listed() {
        return listItem(this.sellerId, "Seller", this.seller);
    }

    private TestAuctionItem expired() throws Exception {
        TestAuctionItem item = listed();
        moveTo(item, StorageType.LISTED, StorageType.EXPIRED, ItemStatus.REMOVED, null);
        return item;
    }

    private TestAuctionItem purchased(UUID buyer) throws Exception {
        TestAuctionItem item = listed();
        moveTo(item, StorageType.LISTED, StorageType.PURCHASED, ItemStatus.PURCHASED, buyer);
        return item;
    }

    private static RemoveResult join(CompletableFuture<RemoveResult> future) {
        assertTrue(future.isDone(), "la chaine doit etre entierement synchrone dans les tests");
        return future.join();
    }

    private void assertNothingGiven() {
        verify(this.player.getInventory(), never()).addItem(any(ItemStack.class));
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("removeListedItem")
    class RemoveListed {

        @Test
        @DisplayName("give-item et inventaire libre : LISTED -> DELETED, lot remis, log et evenements, verrou relache")
        void removeListedItem_giveItem_deletesRowAndDelivers() throws Exception {
            configureActions(true, false, false, false);
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeListedItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertTrue(result.isItemGiven());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            verify(player.getInventory()).addItem(any(ItemStack.class));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());

            assertEquals(1, logCount(item.getId(), "REMOVE_LISTED"));
            assertEquals("removed_from_listed", logAdditionalData(item.getId(), "REMOVE_LISTED"));
            assertEquals(sellerId.toString(), queryString("SELECT player_unique_id FROM " + Tables.LOGS + " WHERE item_id = ?", item.getId()));

            InOrder events = inOrder(pluginManager);
            events.verify(pluginManager).callEvent(isA(AuctionPreRemoveListedItemEvent.class));
            events.verify(pluginManager).callEvent(isA(AuctionRemoveListedItemEvent.class));

            verify(componentMessage).sendMessage(eq(player), contains("removed"));
            verify(player).closeInventory();
            assertTrue(localBridge.checkAvailability(item).join());
        }

        @Test
        @DisplayName("give-item desactive : LISTED -> EXPIRED avec une nouvelle expiration, rien n'est remis, l'annonce rejoint le conteneur EXPIRED")
        void removeListedItem_noGiveItem_movesToExpired() throws Exception {
            configureActions(false, false, false, false);
            TestAuctionItem item = listed();
            long before = System.currentTimeMillis();

            RemoveResult result = join(service.removeListedItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertFalse(result.isItemGiven());
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            long expiredAt = expiredAtOf(item.getId());
            assertTrue(expiredAt >= before + DEFAULT_EXPIRATION_SECONDS * 1000L - 5_000L && expiredAt <= System.currentTimeMillis() + DEFAULT_EXPIRATION_SECONDS * 1000L + 5_000L,
                    "expired_at doit valoir maintenant + expire-expiration, trouve " + expiredAt);
            assertNothingGiven();
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.REMOVED, item.getStatus());
            assertEquals(1, logCount(item.getId(), "REMOVE_LISTED"));
        }

        @Test
        @DisplayName("give-item actif mais inventaire plein : la decision est prise avant le verrou, l'annonce part en EXPIRED")
        void removeListedItem_giveItemInventoryFull_movesToExpired() throws Exception {
            configureActions(true, false, false, false);
            player = player("Seller", false);
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeListedItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertFalse(result.isItemGiven());
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertNothingGiven();
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
        }

        @Test
        @DisplayName("joueur deconnecte pendant la remise : la ligne DELETED est restauree en EXPIRED (sans expiration) et le retrait reste un succes sans remise")
        void removeListedItem_playerOfflineDuringDelivery_restoresRowAsExpired() throws Exception {
            configureActions(true, false, false, false);
            AuctionClusterBridge bridge = mockBridge(false, null);
            useBridge(bridge);
            TestAuctionItem item = listed();
            when(player.isOnline()).thenReturn(false);

            RemoveResult result = join(service.removeListedItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertFalse(result.isItemGiven(), "C-053 : rien n'a ete remis, le resultat doit le dire");
            assertNothingGiven();
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertEquals(0L, expiredAtOf(item.getId()), "une compensation ne doit jamais produire un item deja perime");
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.REMOVED, item.getStatus());
            // C-083 : la destination DIFFUSEE est celle reellement ecrite, pas l'intention DELETED.
            verify(bridge).removeItem(item, StorageType.LISTED, StorageType.EXPIRED);
            verify(bridge, never()).removeItem(item, StorageType.LISTED, StorageType.DELETED);
        }

        @Test
        @DisplayName("IS_REMOVE_CONFIRM detenu par le joueur, depuis sa confirmation : le retrait aboutit")
        void removeListedItem_removeConfirmStatus_accepted() throws Exception {
            configureActions(true, false, false, false);
            TestAuctionItem item = listed();
            item.setStatus(ItemStatus.IS_REMOVE_CONFIRM);
            item.setReservationOwner(player.getUniqueId());

            RemoveResult result = join(service.removeListedItem(player, item, true));

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals("DELETED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("IS_REMOVE_CONFIRM hors confirmation (clic direct / API) : INVALID_ITEM_STATUS")
        void removeListedItem_removeConfirmStatus_directPath_refused() throws Exception {
            TestAuctionItem item = listed();
            item.setStatus(ItemStatus.IS_REMOVE_CONFIRM);
            item.setReservationOwner(player.getUniqueId());

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.INVALID_ITEM_STATUS, result.getFailReason());
            assertEquals("LISTED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("IS_REMOVE_CONFIRM detenu par un administrateur : le vendeur ne peut pas retirer")
        void removeListedItem_reservedByAdmin_refused() throws Exception {
            TestAuctionItem item = listed();
            item.setStatus(ItemStatus.IS_REMOVE_CONFIRM);
            item.setReservationOwner(java.util.UUID.randomUUID());

            RemoveResult result = join(service.removeListedItem(player, item, true));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.INVALID_ITEM_STATUS, result.getFailReason());
            assertEquals(ItemStatus.IS_REMOVE_CONFIRM, item.getStatus());
            assertEquals("LISTED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("reference perimee (l'instance n'est plus celle du store) : ITEM_NOT_AVAILABLE, inventaire rafraichi, rien ne bouge")
        void removeListedItem_staleReference_refused() throws Exception {
            TestAuctionItem item = listed();
            manager.removeItem(StorageType.LISTED, item); // sortie sans changement de statut

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            verify(manager).updateInventory(player);
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertNothingGiven();
            assertEquals(0, logCount(item.getId(), "REMOVE_LISTED"));
        }

        @Test
        @DisplayName("annonce expiree : ITEM_EXPIRED, caches vides et menu principal rouvert")
        void removeListedItem_expired_refused() throws Exception {
            TestAuctionItem item = listItem(sellerId, "Seller", System.currentTimeMillis() - 1_000L, seller);

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_EXPIRED, result.getFailReason());
            verify(manager).openMainAuction(player);
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertNothingGiven();
        }

        @Test
        @DisplayName("statut transitoire (IS_BEING_PURCHASED) : INVALID_ITEM_STATUS")
        void removeListedItem_invalidStatus_refused() throws Exception {
            TestAuctionItem item = listed();
            item.setStatus(ItemStatus.IS_BEING_PURCHASED);

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.INVALID_ITEM_STATUS, result.getFailReason());
            assertEquals(ItemStatus.IS_BEING_PURCHASED, item.getStatus());
            assertEquals("LISTED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("AuctionPreRemoveListedItemEvent annule : EVENT_CANCELLED, rien n'est ecrit")
        void removeListedItem_preEventCancelled_writesNothing() throws Exception {
            cancelEvents(AuctionPreRemoveListedItemEvent.class);
            configureActions(true, false, false, false);
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.EVENT_CANCELLED, result.getFailReason());
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            assertNothingGiven();
        }

        @Test
        @DisplayName("serveur en cours d'arret : INTERNAL_ERROR avec message, aucune ecriture")
        void removeListedItem_shuttingDown_refused() throws Exception {
            when(plugin.isShuttingDown()).thenReturn(true);
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.INTERNAL_ERROR, result.getFailReason());
            verify(componentMessage).sendMessage(eq(player), contains("shutting down"));
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertNothingGiven();
        }

        @Test
        @DisplayName("annonce verrouillee par un autre acteur : ITEM_NOT_AVAILABLE, statut intact, verrou etranger intact")
        void removeListedItem_lockedByAnother_refused() throws Exception {
            TestAuctionItem item = listed();
            LockToken foreign = localBridge.lockItem(item, UUID.randomUUID(), StorageType.LISTED).join();
            assertTrue(foreign.isAcquired());

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            assertFalse(localBridge.checkAvailability(item).join());
            verify(manager).updateInventory(player);
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertNothingGiven();
        }

        @Test
        @DisplayName("contention sur le verrou (LockToken.noop) : LOCK_FAILED, aucun statut diffuse")
        void removeListedItem_lockRefused_failsCleanly() throws Exception {
            AuctionClusterBridge bridge = mockBridge(false, LockToken.noop());
            useBridge(bridge);
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.LOCK_FAILED, result.getFailReason());
            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            verify(bridge, never()).notifyItemStatusChange(any(), any(), any());
            verify(bridge, never()).unlockItem(any(), any(), any());
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertNothingGiven();
        }

        @Test
        @DisplayName("compare-and-set perdu (ligne deja vendue par un autre serveur) : aucune remise, fantome purge, message au joueur, ligne intacte")
        void removeListedItem_casLost_noDuplication() throws Exception {
            configureActions(true, false, false, false);
            UUID otherBuyer = UUID.randomUUID();
            TestAuctionItem item = listed();
            setRow(item.getId(), StorageType.PURCHASED, otherBuyer);

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNothingGiven();
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(otherBuyer.toString(), buyerOf(item.getId()));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()), "le fantome doit quitter le store");
            assertEquals(0, logCount(item.getId(), "REMOVE_LISTED"));
            verify(componentMessage).sendMessage(eq(player), contains("no longer available"));
            assertTrue(localBridge.checkAvailability(item).join(), "le verrou doit etre relache");
        }

        @Test
        @DisplayName("echec de transport APRES le commit (diffusion cluster) : le retrait reste un succes et le verrou est relache")
        void removeListedItem_postCommitTransportFailure_stillSuccess() throws Exception {
            configureActions(true, false, false, false);
            AuctionClusterBridge bridge = mockBridge(false, null);
            when(bridge.removeItem(any(), any(), any())).thenReturn(failed(new RuntimeException("redis down")));
            useBridge(bridge);
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeListedItem(player, item));

            assertTrue(result.isSuccess(), "une fois la ligne ecrite et le lot remis, l'operation est commise");
            assertTrue(result.isItemGiven());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            verify(player.getInventory()).addItem(any(ItemStack.class));
            verify(bridge).unlockItem(eq(item), any(), eq(StorageType.LISTED));
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("removeListedItem en cluster (relecture autoritaire sous verrou)")
    class RemoveListedDistributed {

        @Test
        @DisplayName("succes : l'expiration memoire est resynchronisee sur la base, la destination reelle est diffusee et le verrou relache")
        void removeListedItem_distributed_success() throws Exception {
            configureActions(true, false, false, false);
            AuctionClusterBridge bridge = mockBridge(true, null);
            useBridge(bridge);
            TestAuctionItem item = listed();
            long prolonged = farFuture() + 3_600_000L;
            execute("UPDATE " + Tables.ITEMS + " SET expired_at = ? WHERE id = ?", prolonged, item.getId());

            RemoveResult result = join(service.removeListedItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(prolonged, item.getExpiredAt().getTime());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            verify(bridge).lockItem(item, player.getUniqueId(), StorageType.LISTED);
            verify(bridge).notifyItemStatusChange(item, ItemStatus.AVAILABLE, ItemStatus.IS_BEING_REMOVED);
            verify(bridge).removeItem(item, StorageType.LISTED, StorageType.DELETED);
            verify(bridge).releaseLock(eq(item), any(), eq(StorageType.LISTED));
        }

        @Test
        @DisplayName("ligne LISTED mais acheteur deja inscrit (commit partiel ailleurs) : fantome purge, AVAILABLE jamais rediffuse, rien remis")
        void removeListedItem_distributed_rowSoldElsewhere_purgesGhost() throws Exception {
            configureActions(true, false, false, false);
            AuctionClusterBridge bridge = mockBridge(true, null);
            useBridge(bridge);
            UUID otherBuyer = UUID.randomUUID();
            TestAuctionItem item = listed();
            setRow(item.getId(), StorageType.LISTED, otherBuyer);

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNothingGiven();
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(otherBuyer.toString(), buyerOf(item.getId()));
            assertEquals("LISTED", storageTypeOf(item.getId()));
            // C-009 : on ne remet jamais en vente sur le cluster un item qu'on constate vendu.
            verify(bridge, never()).notifyItemStatusChange(any(), eq(ItemStatus.IS_BEING_REMOVED), eq(ItemStatus.AVAILABLE));
            verify(bridge).unlockItem(eq(item), any(), eq(StorageType.LISTED));
            verify(componentMessage).sendMessage(eq(player), contains("no longer available"));
        }

        @Test
        @DisplayName("ligne dans un autre conteneur (EXPIRED) : refus SANS purge, statut local restaure, rien diffuse")
        void removeListedItem_distributed_bucketMismatch_refusedWithoutPurge() throws Exception {
            AuctionClusterBridge bridge = mockBridge(true, null);
            useBridge(bridge);
            TestAuctionItem item = listed();
            setRow(item.getId(), StorageType.EXPIRED, null);

            RemoveResult result = join(service.removeListedItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()), "un simple decalage de conteneur ne purge pas");
            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            verify(bridge, never()).notifyItemStatusChange(any(), eq(ItemStatus.IS_BEING_REMOVED), eq(ItemStatus.AVAILABLE));
            verify(bridge, never()).removeItem(any(), any(), any());
            assertNothingGiven();
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("removeSellingItem")
    class RemoveSelling {

        @Test
        @DisplayName("succes avec updatePlayer : LISTED -> DELETED, lot remis, message, log 'removed_selling_item'")
        void removeSellingItem_success_deletesAndDelivers() throws Exception {
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeSellingItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertTrue(result.isItemGiven());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            verify(player.getInventory()).addItem(any(ItemStack.class));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
            assertEquals("removed_selling_item", logAdditionalData(item.getId(), "REMOVE_SELLING"));
            verify(componentMessage).sendMessage(eq(player), contains("removed"));
            verify(pluginManager).callEvent(isA(AuctionRemoveListedItemEvent.class));
        }

        @Test
        @DisplayName("updatePlayer false (retrait en masse) : aucun message ni rafraichissement, log 'removed_selling_item_bulk'")
        void removeSellingItem_bulk_noPlayerFeedback() throws Exception {
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeSellingItem(player, item, false));

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            verify(player.getInventory()).addItem(any(ItemStack.class));
            assertEquals("removed_selling_item_bulk", logAdditionalData(item.getId(), "REMOVE_SELLING"));
            verify(componentMessage, never()).sendMessage(eq(player), anyString());
            verify(manager, never()).updateInventory(player);
        }

        @Test
        @DisplayName("free-space exige et inventaire plein : INSUFFICIENT_SPACE, message seulement si updatePlayer")
        void removeSellingItem_noSpace_refused() throws Exception {
            configureActions(false, false, false, true);
            player = player("Seller", false);
            TestAuctionItem item = listed();

            RemoveResult silent = join(service.removeSellingItem(player, item, false));
            assertEquals(RemoveFailReason.INSUFFICIENT_SPACE, silent.getFailReason());
            verify(componentMessage, never()).sendMessage(eq(player), anyString());

            RemoveResult verbose = join(service.removeSellingItem(player, item, true));
            assertEquals(RemoveFailReason.INSUFFICIENT_SPACE, verbose.getFailReason());
            verify(componentMessage).sendMessage(eq(player), contains("space"));

            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
        }

        @Test
        @DisplayName("statut different de AVAILABLE : INVALID_ITEM_STATUS, inventaire rafraichi seulement si updatePlayer")
        void removeSellingItem_invalidStatus_refused() throws Exception {
            TestAuctionItem item = listed();
            item.setStatus(ItemStatus.IS_REMOVE_CONFIRM);

            RemoveResult silent = join(service.removeSellingItem(player, item, false));
            assertEquals(RemoveFailReason.INVALID_ITEM_STATUS, silent.getFailReason());
            verify(manager, never()).updateInventory(player);

            RemoveResult verbose = join(service.removeSellingItem(player, item, true));
            assertEquals(RemoveFailReason.INVALID_ITEM_STATUS, verbose.getFailReason());
            verify(manager).updateInventory(player);
            assertEquals("LISTED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("annonce expiree : ITEM_EXPIRED")
        void removeSellingItem_expired_refused() throws Exception {
            TestAuctionItem item = listItem(sellerId, "Seller", System.currentTimeMillis() - 1_000L, seller);

            RemoveResult result = join(service.removeSellingItem(player, item));

            assertEquals(RemoveFailReason.ITEM_EXPIRED, result.getFailReason());
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertNothingGiven();
        }

        @Test
        @DisplayName("compare-and-set perdu : aucune remise, fantome purge, ligne intacte")
        void removeSellingItem_casLost_noDuplication() throws Exception {
            TestAuctionItem item = listed();
            setRow(item.getId(), StorageType.DELETED, null);

            RemoveResult result = join(service.removeSellingItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNothingGiven();
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertEquals(0, logCount(item.getId(), "REMOVE_SELLING"));
        }

        @Test
        @Disabled("BUG: RemoveService ne verifie jamais que le joueur est le proprietaire de l'annonce (RemoveFailReason.NO_PERMISSION n'est jamais rendu) ; un appel API direct par un autre joueur retire l'annonce et lui remet le lot. RemoveService.java:87")
        @DisplayName("un joueur qui n'est pas le vendeur ne peut pas retirer l'annonce")
        void removeSellingItem_wrongOwner_refused() throws Exception {
            newPlayer("Intruder");
            Player intruder = player("Intruder", true);
            TestAuctionItem item = listed();

            RemoveResult result = join(service.removeSellingItem(intruder, item));

            assertFalse(result.isSuccess());
            assertEquals("LISTED", storageTypeOf(item.getId()));
            verify(intruder.getInventory(), never()).addItem(any(ItemStack.class));
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("removeExpiredItem")
    class RemoveExpired {

        @Test
        @DisplayName("succes : EXPIRED -> DELETED, lot remis, log REMOVE_EXPIRED et evenements")
        void removeExpiredItem_success_deletesAndDelivers() throws Exception {
            TestAuctionItem item = expired();

            RemoveResult result = join(service.removeExpiredItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertTrue(result.isItemGiven());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            verify(player.getInventory()).addItem(any(ItemStack.class));
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
            assertEquals("removed_expired_item", logAdditionalData(item.getId(), "REMOVE_EXPIRED"));

            InOrder events = inOrder(pluginManager);
            events.verify(pluginManager).callEvent(isA(AuctionPreRemoveExpiredItemEvent.class));
            events.verify(pluginManager).callEvent(isA(AuctionRemoveExpiredItemEvent.class));
            verify(componentMessage).sendMessage(eq(player), contains("removed"));
        }

        @Test
        @DisplayName("statut different de REMOVED : INVALID_ITEM_STATUS")
        void removeExpiredItem_invalidStatus_refused() throws Exception {
            TestAuctionItem item = expired();
            item.setStatus(ItemStatus.AVAILABLE);

            RemoveResult result = join(service.removeExpiredItem(player, item));

            assertEquals(RemoveFailReason.INVALID_ITEM_STATUS, result.getFailReason());
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertNothingGiven();
        }

        @Test
        @DisplayName("compare-and-set perdu (ligne deja DELETED ailleurs) : aucune remise, fantome purge")
        void removeExpiredItem_casLost_noDuplication() throws Exception {
            TestAuctionItem item = expired();
            setRow(item.getId(), StorageType.DELETED, null);

            RemoveResult result = join(service.removeExpiredItem(player, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNothingGiven();
            assertNull(manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(0, logCount(item.getId(), "REMOVE_EXPIRED"));
            verify(componentMessage).sendMessage(eq(player), contains("no longer available"));
        }

        @Test
        @DisplayName("joueur deconnecte pendant la remise : la ligne revient en EXPIRED (sans expiration) et l'annonce reste reclamable")
        void removeExpiredItem_playerOffline_restoresRow() throws Exception {
            AuctionClusterBridge bridge = mockBridge(false, null);
            useBridge(bridge);
            TestAuctionItem item = expired();
            when(player.isOnline()).thenReturn(false);

            RemoveResult result = join(service.removeExpiredItem(player, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertFalse(result.isItemGiven());
            assertNothingGiven();
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertEquals(0L, expiredAtOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
            assertEquals(ItemStatus.REMOVED, item.getStatus());
            verify(bridge).removeItem(item, StorageType.EXPIRED, StorageType.EXPIRED);
        }

        @Test
        @DisplayName("AuctionPreRemoveExpiredItemEvent annule : EVENT_CANCELLED")
        void removeExpiredItem_preEventCancelled_writesNothing() throws Exception {
            cancelEvents(AuctionPreRemoveExpiredItemEvent.class);
            TestAuctionItem item = expired();

            RemoveResult result = join(service.removeExpiredItem(player, item));

            assertEquals(RemoveFailReason.EVENT_CANCELLED, result.getFailReason());
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.EXPIRED, item.getId()));
            assertNothingGiven();
        }

        @Test
        @DisplayName("free-space exige et inventaire plein : INSUFFICIENT_SPACE sans message en mode silencieux")
        void removeExpiredItem_noSpace_refused() throws Exception {
            configureActions(false, false, false, true);
            player = player("Seller", false);
            TestAuctionItem item = expired();

            RemoveResult result = join(service.removeExpiredItem(player, item, false));

            assertEquals(RemoveFailReason.INSUFFICIENT_SPACE, result.getFailReason());
            verify(componentMessage, never()).sendMessage(eq(player), anyString());
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("removePurchasedItem")
    class RemovePurchased {

        private UUID buyerId;
        private Player buyer;

        @BeforeEach
        void setUpBuyer() {
            this.buyerId = newPlayer("Buyer");
            this.buyer = player("Buyer", true);
        }

        @Test
        @DisplayName("succes : PURCHASED -> DELETED, lot remis a l'acheteur, log REMOVE_PURCHASED cible le vendeur")
        void removePurchasedItem_success_deletesAndDelivers() throws Exception {
            TestAuctionItem item = purchased(this.buyerId);

            RemoveResult result = join(service.removePurchasedItem(this.buyer, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertTrue(result.isItemGiven());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertEquals(this.buyerId.toString(), buyerOf(item.getId()), "l'acheteur reste inscrit sur la ligne DELETED");
            verify(this.buyer.getInventory()).addItem(any(ItemStack.class));
            assertNull(manager.getItem(StorageType.PURCHASED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
            assertEquals("removed_purchased_item", logAdditionalData(item.getId(), "REMOVE_PURCHASED"));
            assertEquals(sellerId.toString(), queryString("SELECT target_unique_id FROM " + Tables.LOGS + " WHERE item_id = ? AND log_type = 'REMOVE_PURCHASED'", item.getId()));

            InOrder events = inOrder(pluginManager);
            events.verify(pluginManager).callEvent(isA(AuctionPreRemovePurchasedItemEvent.class));
            events.verify(pluginManager).callEvent(isA(AuctionRemovePurchasedItemEvent.class));
        }

        @Test
        @DisplayName("statut different de PURCHASED : INVALID_ITEM_STATUS")
        void removePurchasedItem_invalidStatus_refused() throws Exception {
            TestAuctionItem item = purchased(this.buyerId);
            item.setStatus(ItemStatus.REMOVED);

            RemoveResult result = join(service.removePurchasedItem(this.buyer, item));

            assertEquals(RemoveFailReason.INVALID_ITEM_STATUS, result.getFailReason());
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            verify(this.buyer.getInventory(), never()).addItem(any(ItemStack.class));
        }

        @Test
        @DisplayName("compare-and-set perdu (deja reclame sur un autre serveur) : aucune remise, fantome purge")
        void removePurchasedItem_casLost_noDuplication() throws Exception {
            TestAuctionItem item = purchased(this.buyerId);
            setRow(item.getId(), StorageType.DELETED, this.buyerId);

            RemoveResult result = join(service.removePurchasedItem(this.buyer, item));

            assertFalse(result.isSuccess());
            assertEquals(RemoveFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            verify(this.buyer.getInventory(), never()).addItem(any(ItemStack.class));
            assertNull(manager.getItem(StorageType.PURCHASED, item.getId()));
            assertEquals(0, logCount(item.getId(), "REMOVE_PURCHASED"));
        }

        @Test
        @DisplayName("acheteur deconnecte pendant la remise : la ligne revient en PURCHASED et l'achat reste reclamable")
        void removePurchasedItem_playerOffline_restoresRow() throws Exception {
            AuctionClusterBridge bridge = mockBridge(false, null);
            useBridge(bridge);
            TestAuctionItem item = purchased(this.buyerId);
            when(this.buyer.isOnline()).thenReturn(false);

            RemoveResult result = join(service.removePurchasedItem(this.buyer, item));

            assertTrue(result.isSuccess(), result.getMessage());
            assertFalse(result.isItemGiven());
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(this.buyerId.toString(), buyerOf(item.getId()));
            assertEquals(0L, expiredAtOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.PURCHASED, item.getId()));
            assertEquals(ItemStatus.PURCHASED, item.getStatus());
            verify(bridge).removeItem(item, StorageType.PURCHASED, StorageType.PURCHASED);
        }

        @Test
        @DisplayName("AuctionPreRemovePurchasedItemEvent annule : EVENT_CANCELLED")
        void removePurchasedItem_preEventCancelled_writesNothing() throws Exception {
            cancelEvents(AuctionPreRemovePurchasedItemEvent.class);
            TestAuctionItem item = purchased(this.buyerId);

            RemoveResult result = join(service.removePurchasedItem(this.buyer, item));

            assertEquals(RemoveFailReason.EVENT_CANCELLED, result.getFailReason());
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            verify(this.buyer.getInventory(), never()).addItem(any(ItemStack.class));
        }

        @Test
        @DisplayName("free-space exige et inventaire plein : INSUFFICIENT_SPACE")
        void removePurchasedItem_noSpace_refused() throws Exception {
            configureActions(false, false, true, false);
            Player fullBuyer = player("Buyer", false);
            TestAuctionItem item = purchased(this.buyerId);

            RemoveResult result = join(service.removePurchasedItem(fullBuyer, item));

            assertEquals(RemoveFailReason.INSUFFICIENT_SPACE, result.getFailReason());
            verify(componentMessage).sendMessage(eq(fullBuyer), contains("space"));
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            verifyNoInteractions(inventoryManager);
        }
    }
}
