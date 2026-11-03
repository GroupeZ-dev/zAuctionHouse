package fr.maxlego08.zauctionhouse.storage.repository;

import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.PlayerDTO;
import fr.maxlego08.zauctionhouse.testutil.DatabaseTestBase;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRepositoryTest extends DatabaseTestBase {

    @Test
    @DisplayName("upsertPlayer insere puis met a jour le nom sans dupliquer la ligne")
    void upsertPlayer_insertsThenUpdatesName() throws Exception {
        UUID uniqueId = UUID.randomUUID();

        players().upsertPlayer(uniqueId, "Alice");
        players().upsertPlayer(uniqueId, "Alice_renamed");

        assertEquals(1, count(Tables.PLAYERS));
        assertEquals("Alice_renamed", players().select(uniqueId));
        assertEquals("Alice_renamed", queryString("SELECT name FROM " + Tables.PLAYERS + " WHERE unique_id = ?", uniqueId.toString()));
    }

    @Test
    @DisplayName("upsertPlayer(Player) lit l'UUID et le nom du joueur")
    void upsertPlayer_fromBukkitPlayer() {
        var player = MockPlugins.player("Bob");

        players().upsertPlayer(player);

        assertEquals("Bob", players().select(player.getUniqueId()));
        assertEquals(player.getUniqueId(), players().selectByName("Bob"));
    }

    @Test
    @DisplayName("select(uuid) et selectByName rendent null quand le joueur est inconnu")
    void select_unknownPlayer_returnsNull() {
        assertNull(players().select(UUID.randomUUID()));
        assertNull(players().selectByName("nobody"));
    }

    @Test
    @DisplayName("select() charge l'annuaire complet")
    void selectAll_returnsEveryRow() {
        newPlayer("p1");
        newPlayer("p2");
        newPlayer("p3");

        List<PlayerDTO> all = players().select();

        assertEquals(3, all.size());
        assertTrue(all.stream().map(PlayerDTO::name).toList().containsAll(List.of("p1", "p2", "p3")));
    }

    @Test
    @DisplayName("select(List) pagine la clause IN au-dela de 500 identifiants et ne perd aucune ligne")
    void selectIn_chunksAboveLimit() {
        List<String> uuids = new ArrayList<>();
        for (int index = 0; index < 1203; index++) {
            uuids.add(newPlayer("bulk-" + index).toString());
        }
        uuids.add(UUID.randomUUID().toString()); // inconnu : ne doit rien casser

        List<PlayerDTO> rows = players().select(uuids);

        assertEquals(1203, rows.size());
        assertEquals(1203, rows.stream().map(PlayerDTO::unique_id).distinct().count());
    }

    @Test
    @DisplayName("select(List) avec une liste vide ne touche pas la base et rend une liste vide")
    void selectIn_emptyList() {
        assertTrue(players().select(List.of()).isEmpty());
    }
}
