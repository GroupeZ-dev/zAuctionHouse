package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.OptionDTO;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SQL des options par joueur : cle composite (player_unique_id, option_name).
 */
class OptionRepositoryTest extends DatabaseTestBase {

    private String valueOf(UUID player, String optionName) throws Exception {
        return queryString("SELECT option_value FROM " + Tables.OPTIONS + " WHERE player_unique_id = ? AND option_name = ?", player.toString(), optionName);
    }

    @Test
    @DisplayName("upsertOption insere une ligne avec toutes les colonnes")
    void upsertOption_insertsRow() throws Exception {
        UUID alice = newPlayer("Alice");

        options().upsertOption(alice, "sort", "PRICE_ASC");

        assertEquals(1, count(Tables.OPTIONS));
        List<Object[]> rows = queryRows("SELECT player_unique_id, option_name, option_value FROM " + Tables.OPTIONS);
        assertEquals(alice.toString(), rows.get(0)[0]);
        assertEquals("sort", rows.get(0)[1]);
        assertEquals("PRICE_ASC", rows.get(0)[2]);
    }

    @Test
    @DisplayName("upsertOption sur la meme cle composite met a jour la valeur sans dupliquer la ligne")
    void upsertOption_sameKey_updatesInPlace() throws Exception {
        UUID alice = newPlayer("Alice");

        options().upsertOption(alice, "sort", "PRICE_ASC");
        options().upsertOption(alice, "sort", "PRICE_DESC");

        assertEquals(1, count(Tables.OPTIONS));
        assertEquals("PRICE_DESC", valueOf(alice, "sort"));
        List<OptionDTO> all = options().selectAll(alice);
        assertEquals(1, all.size());
        assertEquals("PRICE_DESC", all.get(0).option_value());
    }

    @Test
    @DisplayName("deux options differentes du meme joueur sont deux lignes distinctes")
    void upsertOption_differentNames_twoRows() throws Exception {
        UUID alice = newPlayer("Alice");

        options().upsertOption(alice, "sort", "PRICE_ASC");
        options().upsertOption(alice, "category", "weapons");

        assertEquals(2, count(Tables.OPTIONS, "player_unique_id = ?", alice.toString()));
        assertEquals("PRICE_ASC", valueOf(alice, "sort"));
        assertEquals("weapons", valueOf(alice, "category"));
    }

    @Test
    @DisplayName("la meme option chez deux joueurs est isolee (cle composite)")
    void upsertOption_twoPlayers_isolated() throws Exception {
        UUID alice = newPlayer("Alice");
        UUID bob = newPlayer("Bob");

        options().upsertOption(alice, "sort", "PRICE_ASC");
        options().upsertOption(bob, "sort", "PRICE_DESC");
        options().upsertOption(alice, "sort", "NAME");

        assertEquals(2, count(Tables.OPTIONS));
        assertEquals("NAME", valueOf(alice, "sort"));
        assertEquals("PRICE_DESC", valueOf(bob, "sort"), "la mise a jour d'Alice ne doit pas toucher Bob");
    }

    @Test
    @DisplayName("upsertOption accepte une valeur longue (option_value est un LONGTEXT)")
    void upsertOption_longValue_roundTrip() throws Exception {
        UUID alice = newPlayer("Alice");
        String longValue = "x".repeat(5_000);

        options().upsertOption(alice, "filters", longValue);

        assertEquals(longValue, valueOf(alice, "filters"));
        assertEquals(longValue, options().selectAll(alice).get(0).option_value());
    }

    @Test
    @DisplayName("selectAll rend toutes les options du joueur avec un mapping complet et exclut les autres joueurs")
    void selectAll_returnsOnlyPlayerOptions_andMapsDto() throws Exception {
        UUID alice = newPlayer("Alice");
        UUID bob = newPlayer("Bob");
        options().upsertOption(alice, "sort", "PRICE_ASC");
        options().upsertOption(alice, "category", "weapons");
        options().upsertOption(bob, "sort", "PRICE_DESC");

        List<OptionDTO> all = options().selectAll(alice);

        assertEquals(2, all.size());
        assertTrue(all.stream().allMatch(dto -> alice.equals(dto.player_unique_id())));
        Set<String> names = all.stream().map(OptionDTO::option_name).collect(Collectors.toSet());
        assertEquals(Set.of("sort", "category"), names);
        OptionDTO sort = all.stream().filter(dto -> dto.option_name().equals("sort")).findFirst().orElseThrow();
        assertEquals("PRICE_ASC", sort.option_value());
    }

    @Test
    @DisplayName("selectAll rend une liste vide pour un joueur sans option")
    void selectAll_unknownPlayer_returnsEmpty() throws Exception {
        options().upsertOption(newPlayer("Alice"), "sort", "PRICE_ASC");

        assertTrue(options().selectAll(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("deleteOption supprime uniquement l'option ciblee du joueur cible")
    void deleteOption_removesOnlyTargetedRow() throws Exception {
        UUID alice = newPlayer("Alice");
        UUID bob = newPlayer("Bob");
        options().upsertOption(alice, "sort", "PRICE_ASC");
        options().upsertOption(alice, "category", "weapons");
        options().upsertOption(bob, "sort", "PRICE_DESC");

        options().deleteOption(alice, "sort");

        assertEquals(2, count(Tables.OPTIONS));
        assertNull(valueOf(alice, "sort"));
        assertEquals("weapons", valueOf(alice, "category"));
        assertEquals("PRICE_DESC", valueOf(bob, "sort"));
    }

    @Test
    @DisplayName("deleteOption sur une option inexistante ne touche rien")
    void deleteOption_unknownOption_noEffect() throws Exception {
        UUID alice = newPlayer("Alice");
        options().upsertOption(alice, "sort", "PRICE_ASC");

        options().deleteOption(alice, "unknown");
        options().deleteOption(UUID.randomUUID(), "sort");

        assertEquals(1, count(Tables.OPTIONS));
        assertEquals("PRICE_ASC", valueOf(alice, "sort"));
    }

    @Test
    @DisplayName("deleteAll supprime toutes les options du joueur et ne touche pas l'autre joueur")
    void deleteAll_removesPlayerRowsOnly() throws Exception {
        UUID alice = newPlayer("Alice");
        UUID bob = newPlayer("Bob");
        options().upsertOption(alice, "sort", "PRICE_ASC");
        options().upsertOption(alice, "category", "weapons");
        options().upsertOption(bob, "sort", "PRICE_DESC");
        options().upsertOption(bob, "category", "tools");

        options().deleteAll(alice);

        assertEquals(0, count(Tables.OPTIONS, "player_unique_id = ?", alice.toString()));
        assertEquals(2, count(Tables.OPTIONS, "player_unique_id = ?", bob.toString()));
        assertTrue(options().selectAll(alice).isEmpty());
        assertEquals(2, options().selectAll(bob).size());
    }

    @Test
    @DisplayName("deleteAll sur un joueur sans option ne touche rien")
    void deleteAll_unknownPlayer_noEffect() throws Exception {
        options().upsertOption(newPlayer("Alice"), "sort", "PRICE_ASC");

        options().deleteAll(UUID.randomUUID());

        assertEquals(1, count(Tables.OPTIONS));
    }

    @Test
    @DisplayName("apres deleteOption, upsertOption recree la ligne")
    void upsertOption_afterDelete_recreatesRow() throws Exception {
        UUID alice = newPlayer("Alice");
        options().upsertOption(alice, "sort", "PRICE_ASC");
        options().deleteOption(alice, "sort");

        options().upsertOption(alice, "sort", "NAME");

        assertEquals(1, count(Tables.OPTIONS));
        assertEquals("NAME", valueOf(alice, "sort"));
    }

    @Test
    @DisplayName("le repository est rattache a la table des options")
    void getTableName_isOptionsTable() {
        assertEquals(Tables.OPTIONS, options().getTableName());
    }
}
