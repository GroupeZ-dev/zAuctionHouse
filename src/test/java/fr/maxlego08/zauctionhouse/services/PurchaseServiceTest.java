package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.AuctionClusterBridge;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.purchase.AuctionPrePurchaseItemEvent;
import fr.maxlego08.zauctionhouse.api.event.events.stats.AuctionItemPurchasedEvent;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.PurchaseResult;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.tax.TaxConfiguration;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PurchaseService#purchaseItem} de bout en bout : gardes synchrones, verrou cluster,
 * relecture autoritaire sous verrou, debit, ecritures base (items / transactions / logs) et
 * convergence de statut sur les chemins d'echec.
 */
class PurchaseServiceTest extends ServiceTestBase {

    private PurchaseService service;
    private UUID sellerId;
    private UUID buyerId;
    private Player buyer;
    private OfflinePlayer seller;

    @BeforeEach
    void setUpPurchase() {
        this.service = new PurchaseService(this.plugin);
        this.sellerId = newPlayer("Seller");
        this.buyerId = newPlayer("Buyer");
        this.buyer = player("Buyer", true);
        this.seller = offlinePlayer(this.sellerId, "Seller", null);
    }

    /**
     * Une annonce LISTED en base et en memoire, dans l'etat attendu par le service
     * (confirmation d'achat ouverte).
     */
    private TestAuctionItem confirmedItem() {
        TestAuctionItem item = listItem(this.sellerId, "Seller", this.seller);
        item.setStatus(ItemStatus.IS_PURCHASE_CONFIRM);
        return item;
    }

    private PurchaseResult purchase(TestAuctionItem item) {
        CompletableFuture<PurchaseResult> future = this.service.purchaseItem(this.buyer, item);
        assertTrue(future.isDone(), "la chaine doit etre entierement synchrone dans les tests");
        return future.join();
    }

    private void assertRowUntouched(TestAuctionItem item) throws Exception {
        assertEquals("LISTED", storageTypeOf(item.getId()));
        assertNull(buyerOf(item.getId()));
        assertEquals(0, transactionCount(item.getId()));
        assertEquals(0, logCount(item.getId(), "PURCHASE"));
    }

    private void assertNoMoneyMoved() {
        verify(this.economy, never()).withdrawChecked(any(), any(), any());
        verify(this.economy, never()).withdraw(any(), any(), any());
        verify(this.economy, never()).depositChecked(any(), any(), any());
        verify(this.economy, never()).deposit(any(), any(), any());
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("succes")
    class Success {

        @Test
        @DisplayName("vendeur hors ligne : la ligne passe LISTED -> PURCHASED avec l'acheteur, le vendeur recoit une transaction PENDING, un log PURCHASE est ecrit")
        void purchaseItem_sellerOffline_movesRowAndCreatesPendingTransaction() throws Exception {
            TestAuctionItem item = confirmedItem();
            long before = System.currentTimeMillis();

            PurchaseResult result = purchase(item);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(PurchaseFailReason.NONE, result.getFailReason());

            // Base : la ligne a bouge avec le bon acheteur et une expiration d'achat recalculee.
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(buyerId.toString(), buyerOf(item.getId()));
            long expiredAt = expiredAtOf(item.getId());
            assertTrue(expiredAt >= before + DEFAULT_EXPIRATION_SECONDS * 1000L - 5_000L && expiredAt <= System.currentTimeMillis() + DEFAULT_EXPIRATION_SECONDS * 1000L + 5_000L,
                    "expired_at doit valoir maintenant + purchase-expiration, trouve " + expiredAt);

            // Comptabilite : debit RETRIEVED pour l'acheteur, dette PENDING pour le vendeur.
            assertEquals(2, transactionCount(item.getId()));
            assertEquals("RETRIEVED", transactionStatus(item.getId(), buyerId));
            assertEquals(0, PRICE.negate().compareTo(transactionValue(item.getId(), buyerId)));
            assertEquals("PENDING", transactionStatus(item.getId(), sellerId));
            assertEquals(0, PRICE.compareTo(transactionValue(item.getId(), sellerId)));
            assertEquals("vault", queryString("SELECT economy_name FROM " + Tables.TRANSACTIONS + " WHERE item_id = ? AND player_unique_id = ?", item.getId(), sellerId.toString()));

            // Journal : un log PURCHASE non lu (vendeur absent), cible = vendeur.
            assertEquals(1, logCount(item.getId(), "PURCHASE"));
            assertEquals(sellerId.toString(), queryString("SELECT target_unique_id FROM " + Tables.LOGS + " WHERE item_id = ? AND log_type = 'PURCHASE'", item.getId()));
            assertEquals(buyerId.toString(), queryString("SELECT player_unique_id FROM " + Tables.LOGS + " WHERE item_id = ? AND log_type = 'PURCHASE'", item.getId()));
            assertNull(queryObject("SELECT readed_at FROM " + Tables.LOGS + " WHERE item_id = ? AND log_type = 'PURCHASE'", item.getId()));

            // Memoire : l'annonce a change de conteneur et porte le statut terminal.
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertSame(item, manager.getItem(StorageType.PURCHASED, item.getId()));
            assertEquals(ItemStatus.PURCHASED, item.getStatus());
            assertEquals(buyerId, item.getBuyerUniqueId());

            // Argent : un seul debit du montant exact, aucun depot (vendeur hors ligne => PENDING).
            verify(economy).withdrawChecked(eq(buyerId), eq(PRICE), anyString());
            verify(economy, never()).depositChecked(any(), any(), any());

            // Evenements : Pre (annulable) AVANT l'achat, puis l'evenement de statistiques.
            InOrder events = inOrder(pluginManager);
            events.verify(pluginManager).callEvent(isA(AuctionPrePurchaseItemEvent.class));
            events.verify(pluginManager).callEvent(isA(AuctionItemPurchasedEvent.class));

            // Verrou local relache.
            assertTrue(localBridge.checkAvailability(item).join());

            verify(componentMessage).sendMessage(eq(buyer), contains("bought"));
        }

        @Test
        @DisplayName("vendeur en ligne et auto-claim : le vendeur est credite immediatement et sa transaction est RETRIEVED, le log est marque lu")
        void purchaseItem_sellerOnlineAutoClaim_depositsImmediately() throws Exception {
            Player sellerPlayer = player("Seller", true);
            TestAuctionItem item = confirmedItem();
            item.setSeller(offlinePlayer(sellerId, "Seller", sellerPlayer));
            when(economy.isAutoClaim()).thenReturn(true);

            PurchaseResult result = purchase(item);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            verify(economy).withdrawChecked(eq(buyerId), eq(PRICE), anyString());
            verify(economy).depositChecked(eq(sellerId), eq(PRICE), anyString());
            assertEquals("RETRIEVED", transactionStatus(item.getId(), sellerId));
            assertNotNull(queryObject("SELECT readed_at FROM " + Tables.LOGS + " WHERE item_id = ? AND log_type = 'PURCHASE'", item.getId()));
            verify(componentMessage).sendMessage(eq(sellerPlayer), contains("bought"));
        }

        @Test
        @DisplayName("taxe d'achat : l'acheteur paie le prix plein, le vendeur touche le prix net dans sa transaction")
        void purchaseItem_withPurchaseTax_sellerReceivesNetAmount() throws Exception {
            TestAuctionItem item = confirmedItem();
            TaxConfiguration taxConfiguration = mock(TaxConfiguration.class);
            when(taxConfiguration.isEnabled()).thenReturn(true);
            when(economy.getTaxConfiguration()).thenReturn(taxConfiguration);
            // 10% PURCHASE : acheteur 10, vendeur 9.
            TaxResult taxResult = new TaxResult(BigDecimal.ONE, 10.0, PRICE, new BigDecimal("9"), false, false, 0, TaxType.PURCHASE);
            when(economy.calculatePurchaseTax(any(Player.class), any(BigDecimal.class), any())).thenReturn(taxResult);

            PurchaseResult result = purchase(item);

            assertTrue(result.isSuccess(), result.getMessage());
            verify(economy).withdrawChecked(eq(buyerId), eq(PRICE), anyString());
            assertEquals(0, new BigDecimal("9").compareTo(transactionValue(item.getId(), sellerId)));
            assertEquals(0, PRICE.negate().compareTo(transactionValue(item.getId(), buyerId)));
            verify(componentMessage).sendMessage(eq(buyer), contains("tax"));
        }

        @Test
        @DisplayName("give-item actif et inventaire libre : la ligne part en DELETED avec l'acheteur et le lot est remis en main propre")
        void purchaseItem_giveItem_deliversAndDeletesRow() throws Exception {
            configureActions(false, true, false, false);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals("DELETED", storageTypeOf(item.getId()));
            assertEquals(buyerId.toString(), buyerOf(item.getId()));
            verify(buyer.getInventory()).addItem(any(org.bukkit.inventory.ItemStack.class));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertNull(manager.getItem(StorageType.PURCHASED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
        }

        @Test
        @DisplayName("give-item actif mais inventaire plein : la ligne est restauree en PURCHASED (reclamable, sans expiration) au lieu d'etre perdue")
        void purchaseItem_giveItemInventoryFull_restoresRowAsPurchased() throws Exception {
            configureActions(false, true, false, false);
            Player fullBuyer = player("Buyer", false);
            buyer = fullBuyer;
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertTrue(result.isSuccess(), result.getMessage());
            verify(fullBuyer.getInventory(), never()).addItem(any(org.bukkit.inventory.ItemStack.class));
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(buyerId.toString(), buyerOf(item.getId()));
            assertEquals(0L, expiredAtOf(item.getId()), "une compensation ne doit jamais produire un item deja perime");
            assertSame(item, manager.getItem(StorageType.PURCHASED, item.getId()));
            assertEquals(ItemStatus.PURCHASED, item.getStatus());
            verify(componentMessage).sendMessage(eq(fullBuyer), contains("inventory is full"));
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("gardes synchrones (avant tout verrou)")
    class SynchronousGuards {

        @Test
        @DisplayName("serveur en cours d'arret : refus INTERNAL_ERROR avec message, aucun evenement ni ecriture")
        void purchaseItem_shuttingDown_refusesBeforeAnything() throws Exception {
            when(plugin.isShuttingDown()).thenReturn(true);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.INTERNAL_ERROR, result.getFailReason());
            verify(componentMessage).sendMessage(eq(buyer), contains("shutting down"));
            verifyNoInteractions(pluginManager);
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("AuctionPrePurchaseItemEvent annule : EVENT_CANCELLED, rien n'est ecrit ni debite")
        void purchaseItem_preEventCancelled_writesNothing() throws Exception {
            cancelEvents(AuctionPrePurchaseItemEvent.class);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.EVENT_CANCELLED, result.getFailReason());
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
            assertRowUntouched(item);
            assertNoMoneyMoved();
            verify(economy, never()).has(any(UUID.class), any(BigDecimal.class));
        }

        @Test
        @DisplayName("configuration de taxe qui cree de la monnaie : INTERNAL_ERROR, achat refuse avant tout mouvement")
        void purchaseItem_taxMintsMoney_refused() throws Exception {
            TaxConfiguration taxConfiguration = mock(TaxConfiguration.class);
            when(taxConfiguration.isEnabled()).thenReturn(true);
            when(economy.getTaxConfiguration()).thenReturn(taxConfiguration);
            // CAPITALISM : acheteur paie finalPrice (8) et vendeur touche originalPrice (10).
            TaxResult minting = new TaxResult(new BigDecimal("2"), 20.0, PRICE, new BigDecimal("8"), false, false, 0, TaxType.CAPITALISM);
            when(economy.calculatePurchaseTax(any(Player.class), any(BigDecimal.class), any())).thenReturn(minting);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.INTERNAL_ERROR, result.getFailReason());
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("give-item + free-space exiges et inventaire plein : INSUFFICIENT_SPACE avec message")
        void purchaseItem_noInventorySpace_refused() throws Exception {
            configureActions(false, true, true, false);
            buyer = player("Buyer", false);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.INSUFFICIENT_SPACE, result.getFailReason());
            verify(componentMessage).sendMessage(eq(buyer), contains("space"));
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("annonce expiree : ITEM_EXPIRED, le menu principal est rouvert et rien n'est ecrit")
        void purchaseItem_expiredItem_refused() throws Exception {
            TestAuctionItem item = listItem(sellerId, "Seller", System.currentTimeMillis() - 1_000L, seller);
            item.setStatus(ItemStatus.IS_PURCHASE_CONFIRM);

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.ITEM_EXPIRED, result.getFailReason());
            verify(manager).openMainAuction(buyer);
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("annonce qui n'est pas en confirmation d'achat : ITEM_NOT_IN_PURCHASE_STATE")
        void purchaseItem_notInPurchaseConfirm_refused() throws Exception {
            TestAuctionItem item = listItem(sellerId, "Seller", seller); // AVAILABLE

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.ITEM_NOT_IN_PURCHASE_STATE, result.getFailReason());
            assertEquals(ItemStatus.AVAILABLE, item.getStatus());
            verify(manager).openMainAuction(buyer);
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @Disabled("BUG: PurchaseService.purchaseItem ne refuse pas l'achat de sa propre annonce (PurchaseFailReason.CANNOT_BUY_OWN_ITEM n'est jamais rendu) ; seule l'UI (ListedItemsButton.java:123) protege, un appel API direct debite le vendeur-acheteur. PurchaseService.java:36")
        @DisplayName("l'acheteur est le vendeur : CANNOT_BUY_OWN_ITEM, rien n'est ecrit")
        void purchaseItem_buyerIsSeller_refused() throws Exception {
            buyer = player("Seller", true); // meme UUID que le vendeur
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.CANNOT_BUY_OWN_ITEM, result.getFailReason());
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("verrou cluster")
    class ClusterLock {

        @Test
        @DisplayName("annonce deja verrouillee par un autre acteur (bridge local) : ITEM_NOT_AVAILABLE, inventaire rafraichi, verrou etranger intact")
        void purchaseItem_itemLockedByAnother_refused() throws Exception {
            TestAuctionItem item = confirmedItem();
            LockToken foreign = localBridge.lockItem(item, UUID.randomUUID(), StorageType.LISTED).join();
            assertTrue(foreign.isAcquired());

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            verify(inventoryManager).updateInventory(buyer);
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus(), "le statut appartient a la chaine gagnante, il ne doit pas etre touche");
            assertFalse(localBridge.checkAvailability(item).join(), "le verrou de l'autre acteur ne doit pas etre detruit");
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("acquisition refusee par contention (LockToken.noop) : LOCK_FAILED, aucun changement de statut diffuse")
        void purchaseItem_lockRefused_failsWithoutStatusChange() throws Exception {
            AuctionClusterBridge bridge = mockBridge(true, LockToken.noop());
            useBridge(bridge);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.LOCK_FAILED, result.getFailReason());
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
            verify(bridge, never()).notifyItemStatusChange(any(), any(), any());
            verify(bridge, never()).unlockItem(any(), any(), any());
            verify(inventoryManager).updateInventory(buyer);
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("bail perdu avant le debit (isHeldBy false) : LOCK_FAILED, statut restaure, rien n'est debite")
        void purchaseItem_leaseLostBeforeCommit_abortsForFree() throws Exception {
            AuctionClusterBridge bridge = mockBridge(true, null);
            when(bridge.isHeldBy(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(false));
            useBridge(bridge);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.LOCK_FAILED, result.getFailReason());
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
            verify(bridge).notifyItemStatusChange(item, ItemStatus.IS_BEING_PURCHASED, ItemStatus.IS_PURCHASE_CONFIRM);
            verify(bridge).unlockItem(eq(item), any(), eq(StorageType.LISTED));
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("course perdue entre la relecture et le compare-and-set : la ligne de l'autre serveur n'est pas ecrasee et l'achat est rapporte en echec")
        void purchaseItem_casLostAfterRevalidation_doesNotOverwriteRow() throws Exception {
            UUID otherBuyer = UUID.randomUUID();
            AuctionClusterBridge bridge = mockBridge(true, null);
            // Un autre noeud commet l'achat juste apres notre relecture autoritaire.
            when(bridge.isHeldBy(any(), any(), any())).thenAnswer(invocation -> {
                TestAuctionItem locked = invocation.getArgument(0);
                setRow(locked.getId(), StorageType.PURCHASED, otherBuyer);
                return CompletableFuture.completedFuture(true);
            });
            useBridge(bridge);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(otherBuyer.toString(), buyerOf(item.getId()), "la ligne deja vendue ailleurs ne doit jamais etre ecrasee");
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus(), "aucun verdict base n'a ete lu sous verrou : le statut de confirmation est restaure");
            verify(bridge).unlockItem(eq(item), any(), eq(StorageType.LISTED));
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("relecture autoritaire sous verrou")
    class RevalidationUnderLock {

        @Test
        @DisplayName("ligne deja PURCHASED par un autre serveur : ITEM_NOT_AVAILABLE, aucun debit, fantome purge du store et du cache ITEM_SHOW, message au joueur")
        void purchaseItem_rowAlreadyPurchased_noWithdrawAndPurge() throws Exception {
            UUID otherBuyer = UUID.randomUUID();
            TestAuctionItem item = confirmedItem();
            manager.getCache(buyer).set(PlayerCacheKey.ITEM_SHOW, item);
            setRow(item.getId(), StorageType.PURCHASED, otherBuyer);

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNoMoneyMoved();
            assertEquals(0, transactionCount(item.getId()));
            assertEquals("PURCHASED", storageTypeOf(item.getId()));
            assertEquals(otherBuyer.toString(), buyerOf(item.getId()));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()), "l'exemplaire local est un fantome : il doit quitter le store");
            assertFalse(manager.getCache(buyer).has(PlayerCacheKey.ITEM_SHOW));
            assertEquals(ItemStatus.DELETED, item.getStatus(), "jamais un statut du cycle LISTED apres un verdict terminal de la base");
            verify(componentMessage).sendMessage(eq(buyer), contains("no longer available"));
            verify(inventoryManager).updateInventory(buyer);
            assertTrue(localBridge.checkAvailability(item).join(), "le verrou doit etre relache");
        }

        @Test
        @DisplayName("ligne disparue (DELETED) : ITEM_NOT_AVAILABLE, purge, statut terminal DELETED")
        void purchaseItem_rowGone_purgesLocalCopy() throws Exception {
            TestAuctionItem item = confirmedItem();
            setRow(item.getId(), StorageType.DELETED, null);

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNoMoneyMoved();
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(ItemStatus.DELETED, item.getStatus());
            assertEquals("DELETED", storageTypeOf(item.getId()));
        }

        @Test
        @DisplayName("ligne passee EXPIRED par un autre noeud (retrait du vendeur) : refus meme sans acheteur inscrit, purge")
        void purchaseItem_rowExpiredElsewhere_refused() throws Exception {
            TestAuctionItem item = confirmedItem();
            setRow(item.getId(), StorageType.EXPIRED, null);

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNoMoneyMoved();
            assertEquals("EXPIRED", storageTypeOf(item.getId()));
            assertNull(manager.getItem(StorageType.LISTED, item.getId()));
        }

        @Test
        @DisplayName("ligne illisible (economie inconnue) : refus SANS purge, statut de confirmation restaure, ligne intacte")
        void purchaseItem_rowUnreadable_refusedWithoutPurge() throws Exception {
            TestAuctionItem item = confirmedItem();
            execute("UPDATE " + Tables.ITEMS + " SET economy_name = 'unknown-economy' WHERE id = ?", item.getId());

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.ITEM_NOT_AVAILABLE, result.getFailReason());
            assertNoMoneyMoved();
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()), "une ligne simplement illisible ne doit pas faire disparaitre l'annonce");
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertTrue(localBridge.checkAvailability(item).join());
        }

        @Test
        @DisplayName("l'expiration memoire est resynchronisee sur la base avant la facturation")
        void purchaseItem_resyncsExpirationFromDatabase() throws Exception {
            configureActions(false, true, false, false); // DELETED : expired_at memoire conserve tel quel
            TestAuctionItem item = confirmedItem();
            long prolonged = farFuture() + 3_600_000L;
            execute("UPDATE " + Tables.ITEMS + " SET expired_at = ? WHERE id = ?", prolonged, item.getId());

            PurchaseResult result = purchase(item);

            assertTrue(result.isSuccess(), result.getMessage());
            assertEquals(prolonged, item.getExpiredAt().getTime());
        }
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("fonds insuffisants")
    class InsufficientFunds {

        @Test
        @DisplayName("has() false : INSUFFICIENT_FUNDS, message, statut restaure, verrou relache, aucune ecriture")
        void purchaseItem_notEnoughMoney_restoresStatusAndUnlocks() throws Exception {
            when(economy.has(any(UUID.class), any(BigDecimal.class))).thenReturn(CompletableFuture.completedFuture(false));
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            assertEquals(PurchaseFailReason.INSUFFICIENT_FUNDS, result.getFailReason());
            verify(economy).has(buyerId, PRICE);
            verify(componentMessage).sendMessage(eq(buyer), contains("enough money"));
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
            assertTrue(localBridge.checkAvailability(item).join());
            assertRowUntouched(item);
            assertNoMoneyMoved();
        }

        @Test
        @DisplayName("le provider refuse le debit (withdrawChecked false) : echec, aucun credit vendeur, ligne intacte")
        void purchaseItem_withdrawRefused_abortsBeforeAnyMovement() throws Exception {
            when(economy.withdrawChecked(any(UUID.class), any(BigDecimal.class), any())).thenReturn(false);
            TestAuctionItem item = confirmedItem();

            PurchaseResult result = purchase(item);

            assertFalse(result.isSuccess());
            verify(economy, never()).depositChecked(any(), any(), any());
            assertEquals(0, transactionCount(item.getId()));
            assertEquals("LISTED", storageTypeOf(item.getId()));
            assertNull(buyerOf(item.getId()));
            assertSame(item, manager.getItem(StorageType.LISTED, item.getId()));
            assertEquals(ItemStatus.IS_PURCHASE_CONFIRM, item.getStatus());
            verify(componentMessage).sendMessage(eq(buyer), contains("enough money"));
            assertTrue(localBridge.checkAvailability(item).join());
        }
    }
}
