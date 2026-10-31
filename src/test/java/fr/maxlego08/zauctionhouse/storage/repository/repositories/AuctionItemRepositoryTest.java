package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.testutil.BulkItemRows;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.TestItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ecriture et relecture des CONTENUS d'annonce ({@link AuctionItemRepository}).
 * <p>
 * La garde de {@code create} est le point sensible : aucune ligne ne doit partir si le lot
 * n'est pas integralement serialisable (C-067), sinon l'annonce se vend au prix plein et
 * l'acheteur ne recoit rien.
 */
class AuctionItemRepositoryTest extends DatabaseTestBase {

    private static final long EXPIRES_AT = 1_900_000_000_000L;
    private static final BigDecimal PRICE = new BigDecimal("12.50");

    private UUID seller;

    @BeforeEach
    void setUpSeller() {
        this.seller = newPlayer("Seller");
    }

    /** Ligne parente LISTED, pour que la cle etrangere item_id pointe sur une vraie annonce. */
    private int parent() {
        return items().create(this.seller, ItemType.AUCTION, PRICE, EXPIRES_AT, this.economy);
    }

    /** Charges utiles d'une annonce, dans l'ordre d'insertion (ORDER BY id). */
    private List<String> payloadsOf(int itemId) throws SQLException {
        return queryRows("SELECT itemstack FROM " + Tables.AUCTION_ITEMS + " WHERE item_id = ? ORDER BY id", itemId)
                .stream().map(row -> String.valueOf(row[0])).toList();
    }

    private AuctionItem createValidated(int itemId, List<ItemStack> itemStacks, List<String> encoded) {
        return auctionItems().create(this.seller, "Seller", itemId, PRICE, EXPIRES_AT, itemStacks, encoded, this.economy);
    }

    // ==========================================================================================

    @Nested
    @DisplayName("create(...) avec charges utiles deja encodees")
    class CreateValidated {

        @Test
        @DisplayName("insere une ligne par charge utile, dans l'ordre")
        void create_insertsOneRowPerPayloadInOrder() throws Exception {
            int itemId = parent();
            List<ItemStack> stacks = List.of(TestItems.diamond(1), TestItems.diamond(2), TestItems.diamond(3));
            List<String> encoded = List.of("first", "second", "third");

            createValidated(itemId, stacks, encoded);

            assertEquals(3, count(Tables.AUCTION_ITEMS));
            assertEquals(3, count(Tables.AUCTION_ITEMS, "item_id = ?", itemId));
            assertEquals(encoded, payloadsOf(itemId));
        }

        @Test
        @DisplayName("rend un ZAuctionItem portant id, serveur, vendeur, prix, economie et ItemStacks")
        void create_returnsAuctionItemWithMetadata() {
            int itemId = parent();
            List<ItemStack> stacks = List.of(TestItems.diamond(4), TestItems.itemStack(Material.EMERALD, 2));

            AuctionItem created = createValidated(itemId, stacks, List.of("a", "b"));

            assertEquals(itemId, created.getId());
            assertEquals(MockPlugins.SERVER_NAME, created.getServerName());
            assertEquals(AuctionItemRepositoryTest.this.seller, created.getSellerUniqueId());
            assertEquals("Seller", created.getSellerName());
            assertEquals(0, PRICE.compareTo(created.getPrice()));
            assertSame(AuctionItemRepositoryTest.this.economy, created.getAuctionEconomy());
            assertEquals("vault", created.getEconomyName());
            assertSame(stacks, created.getItemStacks());
            assertEquals(EXPIRES_AT, created.getExpiredAt().getTime());
            assertNotNull(created.getCreatedAt());
            assertNull(created.getBuyerUniqueId());
        }

        @Test
        @DisplayName("une seule charge utile : une seule ligne")
        void create_singlePayload() throws Exception {
            int itemId = parent();

            createValidated(itemId, List.of(TestItems.diamond(1)), List.of("only"));

            assertEquals(List.of("only"), payloadsOf(itemId));
        }

        @Test
        @DisplayName("listes vides : aucune ligne, mais l'objet est rendu")
        void create_emptyLists_insertsNothingAndReturnsItem() throws Exception {
            int itemId = parent();

            AuctionItem created = createValidated(itemId, List.of(), List.of());

            assertEquals(itemId, created.getId());
            assertTrue(created.getItemStacks().isEmpty());
            assertEquals(0, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("encodedItemStacks null : IllegalStateException et AUCUNE ligne")
        void create_nullEncodedList_throwsAndInsertsNothing() throws Exception {
            int itemId = parent();

            assertThrows(IllegalStateException.class, () -> createValidated(itemId, List.of(TestItems.diamond(1)), null));

            assertEquals(0, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("moins de charges utiles que d'ItemStacks : IllegalStateException et AUCUNE ligne")
        void create_fewerPayloadsThanStacks_throwsAndInsertsNothing() throws Exception {
            int itemId = parent();

            assertThrows(IllegalStateException.class, () -> createValidated(itemId, List.of(TestItems.diamond(1), TestItems.diamond(2)), List.of("only-one")));

            assertEquals(0, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("plus de charges utiles que d'ItemStacks : IllegalStateException et AUCUNE ligne")
        void create_morePayloadsThanStacks_throwsAndInsertsNothing() throws Exception {
            int itemId = parent();

            assertThrows(IllegalStateException.class, () -> createValidated(itemId, List.of(TestItems.diamond(1)), List.of("a", "b")));

            assertEquals(0, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("un element null au milieu du lot : IllegalStateException et AUCUNE ligne (meme pas les precedents)")
        void create_nullPayload_throwsAndInsertsNothing() throws Exception {
            int itemId = parent();
            List<ItemStack> stacks = List.of(TestItems.diamond(1), TestItems.diamond(2), TestItems.diamond(3));
            List<String> encoded = Arrays.asList("ok-1", null, "ok-3");

            assertThrows(IllegalStateException.class, () -> createValidated(itemId, stacks, encoded));

            assertEquals(0, count(Tables.AUCTION_ITEMS), "la validation doit preceder tout INSERT");
        }

        @Test
        @DisplayName("un element vide en fin de lot : IllegalStateException et AUCUNE ligne")
        void create_emptyPayload_throwsAndInsertsNothing() throws Exception {
            int itemId = parent();
            List<ItemStack> stacks = List.of(TestItems.diamond(1), TestItems.diamond(2));

            assertThrows(IllegalStateException.class, () -> createValidated(itemId, stacks, List.of("ok", "")));

            assertEquals(0, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("deux annonces n'entremelent pas leurs contenus")
        void create_twoListings_keepContentsSeparate() throws Exception {
            int first = parent();
            int second = parent();

            createValidated(first, List.of(TestItems.diamond(1)), List.of("f-1"));
            createValidated(second, List.of(TestItems.diamond(1), TestItems.diamond(2)), List.of("s-1", "s-2"));

            assertEquals(List.of("f-1"), payloadsOf(first));
            assertEquals(List.of("s-1", "s-2"), payloadsOf(second));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("surcharges qui encodent elles-memes (Base64ItemStack mocke)")
    @SuppressWarnings("deprecation")
    class CreateEncoding {

        @Test
        @DisplayName("la surcharge deprecie encode chaque ItemStack et ecrit le resultat dans l'ordre")
        void deprecatedCreate_encodesEachStack() throws Exception {
            int itemId = parent();
            ItemStack first = TestItems.diamond(1);
            ItemStack second = TestItems.itemStack(Material.EMERALD, 5);

            try (MockedStatic<Base64ItemStack> base64 = Mockito.mockStatic(Base64ItemStack.class)) {
                base64.when(() -> Base64ItemStack.encode(first)).thenReturn("enc-first");
                base64.when(() -> Base64ItemStack.encode(second)).thenReturn("enc-second");

                AuctionItem created = auctionItems().create(AuctionItemRepositoryTest.this.seller, "Seller", itemId, PRICE, EXPIRES_AT, List.of(first, second), AuctionItemRepositoryTest.this.economy);

                assertEquals(itemId, created.getId());
                assertEquals(List.of(first, second), created.getItemStacks());
                base64.verify(() -> Base64ItemStack.encode(first));
                base64.verify(() -> Base64ItemStack.encode(second));
            }

            assertEquals(List.of("enc-first", "enc-second"), payloadsOf(itemId));
        }

        @Test
        @DisplayName("un encodage qui rend null : IllegalStateException et AUCUNE ligne")
        void deprecatedCreate_encodeReturnsNull_throwsAndInsertsNothing() throws Exception {
            int itemId = parent();
            ItemStack good = TestItems.diamond(1);
            ItemStack broken = TestItems.itemStack(Material.BARRIER, 1);

            try (MockedStatic<Base64ItemStack> base64 = Mockito.mockStatic(Base64ItemStack.class)) {
                base64.when(() -> Base64ItemStack.encode(good)).thenReturn("enc-good");
                base64.when(() -> Base64ItemStack.encode(broken)).thenReturn(null);

                assertThrows(IllegalStateException.class, () -> auctionItems().create(AuctionItemRepositoryTest.this.seller, "Seller", itemId, PRICE, EXPIRES_AT, List.of(good, broken), AuctionItemRepositoryTest.this.economy));
            }

            assertEquals(0, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("un encodage qui rend une chaine vide : IllegalStateException et AUCUNE ligne")
        void deprecatedCreate_encodeReturnsEmpty_throwsAndInsertsNothing() throws Exception {
            int itemId = parent();
            ItemStack stack = TestItems.diamond(1);

            try (MockedStatic<Base64ItemStack> base64 = Mockito.mockStatic(Base64ItemStack.class)) {
                base64.when(() -> Base64ItemStack.encode(stack)).thenReturn("");

                assertThrows(IllegalStateException.class, () -> auctionItems().create(AuctionItemRepositoryTest.this.seller, "Seller", itemId, PRICE, EXPIRES_AT, List.of(stack), AuctionItemRepositoryTest.this.economy));
            }

            assertEquals(0, count(Tables.AUCTION_ITEMS));
        }

        @Test
        @DisplayName("la surcharge Player lit l'UUID et le nom du joueur")
        void create_playerOverload_usesPlayerIdentity() throws Exception {
            UUID carlId = newPlayer("Carl");
            Player carl = MockPlugins.player(carlId, "Carl");
            int itemId = items().create(carlId, ItemType.AUCTION, PRICE, EXPIRES_AT, AuctionItemRepositoryTest.this.economy);
            ItemStack stack = TestItems.diamond(7);

            AuctionItem created;
            try (MockedStatic<Base64ItemStack> base64 = Mockito.mockStatic(Base64ItemStack.class)) {
                base64.when(() -> Base64ItemStack.encode(stack)).thenReturn("enc-carl");
                created = auctionItems().create(carl, itemId, PRICE, EXPIRES_AT, List.of(stack), AuctionItemRepositoryTest.this.economy);
            }

            assertEquals(carlId, created.getSellerUniqueId());
            assertEquals("Carl", created.getSellerName());
            assertEquals(itemId, created.getId());
            assertEquals(List.of("enc-carl"), payloadsOf(itemId));
        }
    }

    // ==========================================================================================

    @Nested
    @DisplayName("select(List<String> ids)")
    class SelectByIds {

        private List<Integer> bulkParents(int count) throws SQLException {
            try (Connection jdbc = jdbc()) {
                List<Integer> ids = BulkItemRows.insertItems(jdbc, TABLE_PREFIX, AuctionItemRepositoryTest.this.seller, count, StorageType.LISTED, EXPIRES_AT);
                BulkItemRows.insertPayloads(jdbc, TABLE_PREFIX, ids, 2);
                return ids;
            }
        }

        @Test
        @DisplayName("liste vide : resultat vide")
        void select_emptyIds_returnsEmpty() {
            parent();
            assertTrue(auctionItems().select(List.of()).isEmpty());
        }

        @Test
        @DisplayName("un seul id : ses charges utiles, avec item_id et itemstack renseignes")
        void select_singleId_returnsItsPayloads() throws Exception {
            int itemId = parent();
            int other = parent();
            createValidated(itemId, List.of(TestItems.diamond(1), TestItems.diamond(2)), List.of("p-1", "p-2"));
            createValidated(other, List.of(TestItems.diamond(1)), List.of("other"));

            List<AuctionItemDTO> rows = auctionItems().select(List.of(String.valueOf(itemId)));

            assertEquals(2, rows.size());
            for (AuctionItemDTO dto : rows) {
                assertEquals(itemId, dto.item_id());
                assertTrue(dto.id() > 0);
                assertNotNull(dto.created_at());
            }
            assertEquals(Set.of("p-1", "p-2"), rows.stream().map(AuctionItemDTO::itemstack).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("les identifiants inconnus sont ignores")
        void select_unknownIds_ignored() {
            int itemId = parent();
            createValidated(itemId, List.of(TestItems.diamond(1)), List.of("p"));

            List<AuctionItemDTO> rows = auctionItems().select(List.of("424242", String.valueOf(itemId)));

            assertEquals(1, rows.size());
            assertEquals(itemId, rows.getFirst().item_id());
        }

        @Test
        @DisplayName("une annonce sans contenu ne rend rien")
        void select_listingWithoutContent_returnsNothing() {
            int itemId = parent();

            assertTrue(auctionItems().select(List.of(String.valueOf(itemId))).isEmpty());
        }

        @ParameterizedTest(name = "{0} annonces x 2 contenus")
        @ValueSource(ints = {500, 501, 1001})
        @DisplayName("pagine la clause IN par 500 sans perdre aucun contenu")
        void select_paginates(int size) throws Exception {
            List<Integer> ids = bulkParents(size);

            List<AuctionItemDTO> rows = auctionItems().select(ids.stream().map(String::valueOf).toList());

            assertEquals(size * 2, rows.size());
            assertEquals(new HashSet<>(ids), rows.stream().map(AuctionItemDTO::item_id).collect(Collectors.toSet()));
            Set<String> expectedPayloads = new HashSet<>();
            for (int id : ids) {
                expectedPayloads.add(BulkItemRows.payload(id, 0));
                expectedPayloads.add(BulkItemRows.payload(id, 1));
            }
            assertEquals(expectedPayloads, rows.stream().map(AuctionItemDTO::itemstack).collect(Collectors.toSet()));
        }

        @Test
        @DisplayName("un sous-ensemble d'identifiants ne rend que ses contenus")
        void select_subset_returnsOnlyRequested() throws Exception {
            List<Integer> ids = bulkParents(20);
            List<String> wanted = new ArrayList<>();
            for (int index = 0; index < 20; index += 4) wanted.add(String.valueOf(ids.get(index)));

            List<AuctionItemDTO> rows = auctionItems().select(wanted);

            assertEquals(wanted.size() * 2, rows.size());
            Set<String> returned = rows.stream().map(dto -> String.valueOf(dto.item_id())).collect(Collectors.toSet());
            assertEquals(new HashSet<>(wanted), returned);
        }
    }
}
