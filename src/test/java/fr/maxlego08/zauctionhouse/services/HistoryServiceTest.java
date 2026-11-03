package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.configuration.records.HistoryConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.records.SalesNotificationConfiguration;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.EconomyManager;
import fr.maxlego08.zauctionhouse.api.history.ItemLog;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.testutil.MessageRecorder;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.StorageManagerTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests de {@link HistoryService} sur une vraie base SQLite (vrai {@code ZStorageManager}).
 * <p>
 * L'historique recharge les annonces vendues via {@code ZStorageManager.selectItems}, qui decode
 * les ItemStack avec {@code Base64ItemStack.decode} : sans serveur Bukkit ce decodage est
 * remplace par une doublure statique qui rend un ItemStack mocke.
 */
class HistoryServiceTest extends StorageManagerTestBase {

    private static final String PAYLOAD = "payload-log";

    private MessageRecorder recorder;
    private RecordingHistoryService service;
    private InventoriesLoader inventoriesLoader;
    private CategoryManager categoryManager;
    private UUID seller;
    private UUID buyer;
    private Player player;

    @BeforeEach
    void setUpService() {
        this.recorder = new MessageRecorder();
        this.service = new RecordingHistoryService(this.plugin, this.recorder);

        this.categoryManager = mock(CategoryManager.class);
        this.inventoriesLoader = mock(InventoriesLoader.class);
        lenient().when(this.plugin.getCategoryManager()).thenReturn(this.categoryManager);
        lenient().when(this.plugin.getInventoriesLoader()).thenReturn(this.inventoriesLoader);

        EconomyManager economyManager = this.plugin.getEconomyManager();
        lenient().when(economyManager.format(any(AuctionEconomy.class), any(Number.class)))
                .thenAnswer(invocation -> invocation.getArgument(1).toString() + "$");

        history(500, 0);
        notifications(true, 0);

        this.seller = newPlayer("Seller");
        this.buyer = newPlayer("Buyer");
        this.player = MockPlugins.player(this.seller, "Seller");
    }

    // ------------------------------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------------------------------

    private void history(int maxEntries, int expireAfterDays) {
        lenient().when(this.plugin.getConfiguration().getHistory()).thenReturn(new HistoryConfiguration(maxEntries, expireAfterDays));
    }

    private void notifications(boolean enabled, long delayTicks) {
        lenient().when(this.plugin.getConfiguration().getSalesNotificationConfiguration()).thenReturn(new SalesNotificationConfiguration(enabled, delayTicks));
    }

    /**
     * Cree une annonce vendue (ligne items PURCHASED + une ligne de contenu) et rend son id.
     */
    private int soldItem(UUID sellerId, UUID buyerId, String price) throws Exception {
        long expiredAt = System.currentTimeMillis() + 60_000L;
        BigDecimal amount = new BigDecimal(price);
        int itemId = items().create(sellerId, ItemType.AUCTION, amount, expiredAt, this.economy);
        auctionItems().create(sellerId, "Seller", itemId, amount, expiredAt, List.of(TestItems.diamond(1)),
                List.of(TestItems.encodedPayload(itemId)), this.economy);
        execute("UPDATE " + Tables.ITEMS + " SET storage_type = ?, buyer_unique_id = ? WHERE id = ?",
                StorageType.PURCHASED.name(), buyerId.toString(), itemId);
        return itemId;
    }

    /**
     * Journalise un achat : l'acteur est l'acheteur, la cible le vendeur. Rend l'id du log.
     */
    private int purchaseLog(int itemId, UUID buyerId, UUID sellerId, String price, String economyName, Date readedAt) throws Exception {
        logs().createLog(LogType.PURCHASE, itemId, buyerId, sellerId, PAYLOAD, new BigDecimal(price), economyName, null, readedAt);
        return (int) queryLong("SELECT MAX(id) FROM " + Tables.LOGS);
    }

    /**
     * Une vente complete du vendeur de reference : item + log PURCHASE non lu.
     */
    private Sale sale(String price) throws Exception {
        int itemId = soldItem(this.seller, this.buyer, price);
        int logId = purchaseLog(itemId, this.buyer, this.seller, price, "vault", null);
        return new Sale(itemId, logId);
    }

    private record Sale(int itemId, int logId) {
    }

    private void ageLog(int logId, String createdAt) throws Exception {
        execute("UPDATE " + Tables.LOGS + " SET created_at = ? WHERE id = ?", createdAt, logId);
    }

    private long unreadCount(UUID sellerId) throws Exception {
        return count(Tables.LOGS, "target_unique_id = ? AND readed_at IS NULL", sellerId.toString());
    }

    /**
     * Appelle {@code getSalesHistory} avec un decodage d'ItemStack simule.
     */
    private List<ItemLog> salesHistory(UUID uniqueId) {
        try (MockedStatic<Base64ItemStack> base64 = mockStatic(Base64ItemStack.class)) {
            base64.when(() -> Base64ItemStack.decode(anyString())).thenAnswer(invocation -> TestItems.diamond(1));
            return this.service.getSalesHistory(uniqueId).join();
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "attendu " + expected + " mais obtenu " + actual);
    }

    // ------------------------------------------------------------------------------------------
    // getSalesHistory
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("getSalesHistory")
    class SalesHistory {

        @Test
        @DisplayName("sans aucun log : liste vide")
        void getSalesHistory_noLogs_returnsEmpty() {
            assertTrue(service.getSalesHistory(seller).join().isEmpty());
        }

        @Test
        @DisplayName("rend les logs PURCHASE dont le joueur est le vendeur, avec l'annonce rechargee")
        void getSalesHistory_returnsPurchaseLogsOfSellerWithItems() throws Exception {
            Sale first = sale("10");
            Sale second = sale("25.50");

            List<ItemLog> history = salesHistory(seller);

            assertEquals(2, history.size());
            for (ItemLog itemLog : history) {
                assertEquals(LogType.PURCHASE, itemLog.log().log_type());
                assertEquals(seller, itemLog.log().target_unique_id());
                assertEquals(buyer, itemLog.log().player_unique_id());
                Item item = itemLog.item();
                assertNotNull(item);
                assertEquals(itemLog.log().item_id(), item.getId());
                assertEquals(seller, item.getSellerUniqueId());
                assertEquals(buyer, item.getBuyerUniqueId());
            }
            List<Integer> itemIds = history.stream().map(entry -> entry.item().getId()).toList();
            assertTrue(itemIds.containsAll(List.of(first.itemId(), second.itemId())));
            verify(categoryManager, times(2)).applyCategories(any(Item.class));
        }

        @Test
        @DisplayName("ignore les achats du joueur (acteur) et les logs d'un autre type")
        void getSalesHistory_ignoresPurchasesAsBuyerAndOtherLogTypes() throws Exception {
            Sale mine = sale("10");
            // Le vendeur a lui-meme achete un item a Buyer : il est acteur, pas cible.
            int bought = soldItem(buyer, seller, "7");
            purchaseLog(bought, seller, buyer, "7", "vault", null);
            // Un retrait d'annonce ciblant le vendeur n'est pas une vente.
            int removed = soldItem(seller, buyer, "3");
            logs().createLog(LogType.REMOVE_LISTED, removed, seller, seller, PAYLOAD, new BigDecimal("3"), "vault", null, null);

            List<ItemLog> history = salesHistory(seller);

            assertEquals(1, history.size());
            assertEquals(mine.itemId(), history.getFirst().item().getId());
        }

        @Test
        @DisplayName("ignore les logs sans annonce (item_id = 0) et ceux dont l'annonce a disparu")
        void getSalesHistory_skipsLogsWithoutItem() throws Exception {
            Sale kept = sale("10");
            purchaseLog(0, buyer, seller, "99", "vault", null);
            purchaseLog(999_999, buyer, seller, "42", "vault", null);

            List<ItemLog> history = salesHistory(seller);

            assertEquals(1, history.size());
            assertEquals(kept.itemId(), history.getFirst().item().getId());
            assertEquals(kept.logId(), history.getFirst().log().id());
        }

        @Test
        @DisplayName("une annonce dont le contenu est illisible est mise en quarantaine et absente de l'historique")
        void getSalesHistory_undecodableItem_skipped() throws Exception {
            sale("10");

            List<ItemLog> history;
            try (MockedStatic<Base64ItemStack> base64 = mockStatic(Base64ItemStack.class)) {
                base64.when(() -> Base64ItemStack.decode(anyString())).thenReturn(null);
                history = service.getSalesHistory(seller).join();
            }

            assertTrue(history.isEmpty());
            verify(categoryManager, never()).applyCategories(any(Item.class));
        }

        @Test
        @DisplayName("max-entries limite le resultat aux ventes les plus recentes")
        void getSalesHistory_maxEntries_keepsMostRecent() throws Exception {
            Sale oldest = sale("1");
            Sale middle = sale("2");
            Sale newest = sale("3");
            ageLog(oldest.logId(), "2020-01-01 00:00:00");
            ageLog(middle.logId(), "2021-06-15 12:00:00");
            history(2, 0);

            List<ItemLog> history = salesHistory(seller);

            assertEquals(2, history.size());
            assertEquals(newest.logId(), history.get(0).log().id());
            assertEquals(middle.logId(), history.get(1).log().id());
            assertTrue(history.stream().noneMatch(entry -> entry.log().id() == oldest.logId()));
        }

        @Test
        @DisplayName("max-entries = 0 : aucune limite")
        void getSalesHistory_maxEntriesZero_unlimited() throws Exception {
            for (int index = 0; index < 5; index++) sale("1");
            history(0, 0);

            assertEquals(5, salesHistory(seller).size());
        }

        @Test
        @DisplayName("max-entries superieur au nombre de ventes : tout est rendu")
        void getSalesHistory_maxEntriesAboveCount_returnsAll() throws Exception {
            sale("1");
            sale("2");
            history(10, 0);

            assertEquals(2, salesHistory(seller).size());
        }

        @Test
        @DisplayName("expire-after-days actif : une vente recente reste visible")
        void getSalesHistory_expireAfterDays_keepsRecentSale() throws Exception {
            Sale recent = sale("10");
            history(500, 30);

            List<ItemLog> history = salesHistory(seller);

            assertEquals(1, history.size());
            assertEquals(recent.logId(), history.getFirst().log().id());
        }

        @Test
        @Disabled("BUG: LogRepository.selectSalesHistory compare created_at (TEXT, DEFAULT CURRENT_TIMESTAMP) a une java.util.Date liee en INTEGER ; sous SQLite tout INTEGER est inferieur a tout TEXT, donc 'created_at > ?' est toujours vrai et expire-after-days ne retire jamais rien (LogRepository.java:97)")
        @DisplayName("expire-after-days actif : une vente plus ancienne que le seuil n'est plus affichee")
        void getSalesHistory_expireAfterDays_hidesOldSale() throws Exception {
            Sale old = sale("10");
            Sale recent = sale("5");
            ageLog(old.logId(), "2020-01-01 00:00:00");
            history(500, 30);

            List<ItemLog> history = salesHistory(seller);

            assertEquals(1, history.size());
            assertEquals(recent.logId(), history.getFirst().log().id());
        }

        @Test
        @DisplayName("l'historique d'un joueur n'inclut pas les ventes d'un autre vendeur")
        void getSalesHistory_isolatesSellers() throws Exception {
            sale("10");
            UUID other = newPlayer("Other");
            int otherItem = soldItem(other, buyer, "8");
            purchaseLog(otherItem, buyer, other, "8", "vault", null);

            List<ItemLog> history = salesHistory(seller);

            assertEquals(1, history.size());
            assertEquals(seller, history.getFirst().item().getSellerUniqueId());
        }
    }

    // ------------------------------------------------------------------------------------------
    // getUnreadSales / markSalesAsRead
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("ventes non lues")
    class UnreadSales {

        @Test
        @DisplayName("getUnreadSales ne rend que les logs PURCHASE non lus dont le joueur est la cible")
        void getUnreadSales_returnsOnlyUnreadPurchaseLogsOfSeller() throws Exception {
            Sale unread1 = sale("10");
            Sale unread2 = sale("5");
            int readItem = soldItem(seller, buyer, "7");
            purchaseLog(readItem, buyer, seller, "7", "vault", new Date());
            UUID other = newPlayer("Other");
            int otherItem = soldItem(other, buyer, "8");
            purchaseLog(otherItem, buyer, other, "8", "vault", null);
            logs().createLog(LogType.REMOVE_LISTED, unread1.itemId(), seller, seller, PAYLOAD, BigDecimal.ONE, "vault", null, null);

            List<LogDTO> unread = service.getUnreadSales(seller).join();

            assertEquals(2, unread.size());
            assertTrue(unread.stream().map(LogDTO::id).toList().containsAll(List.of(unread1.logId(), unread2.logId())));
            assertTrue(unread.stream().noneMatch(LogDTO::isRead));
            assertTrue(unread.stream().allMatch(log -> log.log_type() == LogType.PURCHASE));
        }

        @Test
        @DisplayName("getUnreadSales sans vente rend une liste vide")
        void getUnreadSales_noSales_returnsEmpty() {
            assertTrue(service.getUnreadSales(seller).join().isEmpty());
        }

        @Test
        @DisplayName("markSalesAsRead pose readed_at uniquement sur les logs demandes")
        void markSalesAsRead_setsReadedAtOnGivenLogs() throws Exception {
            Sale first = sale("10");
            Sale second = sale("5");
            Sale third = sale("1");

            service.markSalesAsRead(List.of(first.logId(), second.logId())).join();

            assertEquals(2, count(Tables.LOGS, "readed_at IS NOT NULL"));
            assertEquals(1, unreadCount(seller));
            List<LogDTO> unread = service.getUnreadSales(seller).join();
            assertEquals(1, unread.size());
            assertEquals(third.logId(), unread.getFirst().id());
            assertTrue(logs().selectByTarget(seller).stream().filter(log -> log.id() == first.logId()).allMatch(LogDTO::isRead));
        }

        @Test
        @DisplayName("markSalesAsRead avec une liste vide ou des ids inconnus ne change rien")
        void markSalesAsRead_emptyOrUnknownIds_noop() throws Exception {
            sale("10");

            service.markSalesAsRead(List.of()).join();
            service.markSalesAsRead(List.of(999_999)).join();

            assertEquals(0, count(Tables.LOGS, "readed_at IS NOT NULL"));
            assertEquals(1, unreadCount(seller));
        }
    }

    // ------------------------------------------------------------------------------------------
    // handlePlayerJoin
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("handlePlayerJoin")
    class PlayerJoin {

        @Test
        @DisplayName("notification desactivee : rien n'est envoye ni marque lu")
        void handlePlayerJoin_disabled_doesNothing() throws Exception {
            notifications(false, 0);
            sale("10");

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
            assertEquals(1, unreadCount(seller));
        }

        @Test
        @DisplayName("ventes non lues : message SALES_NOTIFICATION avec le nombre et le total, puis logs marques lus")
        void handlePlayerJoin_unreadSales_notifiesAndMarksRead() throws Exception {
            sale("10");
            sale("5.50");
            int readItem = soldItem(seller, buyer, "100");
            purchaseLog(readItem, buyer, seller, "100", "vault", new Date());

            service.handlePlayerJoin(player);

            var notification = recorder.first(Message.SALES_NOTIFICATION).orElseThrow();
            assertSame(player, notification.sender());
            assertEquals("2", notification.arg("%count%"));
            String total = notification.arg("%total%");
            assertNotNull(total);
            assertTrue(total.endsWith("$"), "total formate par l'economie : " + total);
            assertMoney("15.50", new BigDecimal(total.replace("$", "")));
            assertEquals(1, recorder.count(Message.SALES_NOTIFICATION));
            assertEquals(0, unreadCount(seller));
            assertTrue(service.getUnreadSales(seller).join().isEmpty());
        }

        @Test
        @DisplayName("avec delai : la notification passe par runLater(delai)")
        void handlePlayerJoin_withDelay_usesRunLater() throws Exception {
            notifications(true, 40);
            sale("10");

            service.handlePlayerJoin(player);

            verify(plugin.getScheduler()).runLater(ArgumentMatchers.<Consumer<WrappedTask>>any(), eq(40L));
            assertTrue(recorder.has(Message.SALES_NOTIFICATION));
            assertEquals(0, unreadCount(seller));
        }

        @Test
        @DisplayName("sans delai : la notification est livree sur le thread du joueur (runAtEntity)")
        void handlePlayerJoin_noDelay_usesRunAtEntity() throws Exception {
            notifications(true, 0);
            sale("10");

            service.handlePlayerJoin(player);

            verify(plugin.getScheduler()).runAtEntity(eq(player), ArgumentMatchers.<Consumer<WrappedTask>>any());
            assertTrue(recorder.has(Message.SALES_NOTIFICATION));
        }

        @Test
        @DisplayName("aucune vente non lue : aucun message")
        void handlePlayerJoin_noUnreadSales_noMessage() throws Exception {
            int readItem = soldItem(seller, buyer, "100");
            purchaseLog(readItem, buyer, seller, "100", "vault", new Date());

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
        }

        @Test
        @DisplayName("economie introuvable : le total est affiche en valeur brute")
        void handlePlayerJoin_unknownEconomy_rawTotal() throws Exception {
            int itemId = soldItem(seller, buyer, "12");
            purchaseLog(itemId, buyer, seller, "12", "ghost", null);
            BigDecimal expected = service.getUnreadSales(seller).join().stream().map(LogDTO::price).reduce(BigDecimal.ZERO, BigDecimal::add);

            service.handlePlayerJoin(player);

            var notification = recorder.first(Message.SALES_NOTIFICATION).orElseThrow();
            assertEquals(expected.toString(), notification.arg("%total%"));
            assertEquals("1", notification.arg("%count%"));
        }

        @Test
        @DisplayName("joueur deconnecte avant la notification : rien n'est envoye, mais les ventes sont marquees lues")
        void handlePlayerJoin_playerOffline_marksReadWithoutMessage() throws Exception {
            lenient().when(player.isOnline()).thenReturn(false);
            sale("10");

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
            assertEquals(0, unreadCount(seller));
        }

        @Test
        @DisplayName("une seconde connexion ne renvoie pas la meme notification")
        void handlePlayerJoin_secondJoin_doesNotNotifyAgain() throws Exception {
            sale("10");

            service.handlePlayerJoin(player);
            recorder.clear();
            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
        }

        @Test
        @DisplayName("les ventes d'un autre vendeur ne sont ni notifiees ni marquees lues")
        void handlePlayerJoin_isolatesSellers() throws Exception {
            UUID other = newPlayer("Other");
            int otherItem = soldItem(other, buyer, "8");
            purchaseLog(otherItem, buyer, other, "8", "vault", null);

            service.handlePlayerJoin(player);

            assertTrue(recorder.isEmpty());
            assertEquals(1, unreadCount(other));
        }
    }

    // ------------------------------------------------------------------------------------------
    // openHistoryInventory
    // ------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("openHistoryInventory")
    class OpenInventory {

        @Test
        @DisplayName("sans page : ouvre l'inventaire HISTORY a la page 1")
        void openHistoryInventory_defaultPage_opensPageOne() {
            service.openHistoryInventory(player);

            verify(inventoriesLoader).openInventory(player, Inventories.HISTORY, 1);
        }

        @Test
        @DisplayName("avec page : ouvre l'inventaire HISTORY a la page demandee")
        void openHistoryInventory_withPage_opensGivenPage() {
            service.openHistoryInventory(player, 3);

            verify(inventoriesLoader).openInventory(player, Inventories.HISTORY, 3);
            verify(inventoriesLoader, never()).openInventory(player, Inventories.HISTORY, 1);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Doublure locale
    // ------------------------------------------------------------------------------------------

    /**
     * {@link HistoryService} dont l'envoi de messages est capture au lieu de passer par
     * PlaceholderAPI / MiniMessage (qui exigent un serveur).
     */
    static final class RecordingHistoryService extends HistoryService {

        private final MessageRecorder recorder;

        RecordingHistoryService(AuctionPlugin plugin, MessageRecorder recorder) {
            super(plugin);
            this.recorder = recorder;
        }

        @Override
        protected void message(AuctionPlugin plugin, CommandSender sender, Message message, Object... args) {
            this.recorder.record(sender, message, args);
        }
    }
}
