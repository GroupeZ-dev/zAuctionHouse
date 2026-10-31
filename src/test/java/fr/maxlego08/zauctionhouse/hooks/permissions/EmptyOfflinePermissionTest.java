package fr.maxlego08.zauctionhouse.hooks.permissions;

import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermissionResult;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Implementation de repli quand aucun hook de permissions hors-ligne (LuckPerms) n'est present :
 * toute permission est refusee, mais la reponse reste bien formee.
 */
class EmptyOfflinePermissionTest {

    private final EmptyOfflinePermission permission = new EmptyOfflinePermission();

    @Test
    @DisplayName("chaque permission demandee est rendue avec le resultat false")
    void everyPermission_isDenied() {
        Set<String> permissions = new LinkedHashSet<>(List.of("zauctionhouse.limit.10", "zauctionhouse.tax.bypass"));

        List<OfflinePermissionResult> results = permission.hasPermissions(mock(OfflinePlayer.class), permissions).join();

        assertEquals(2, results.size());
        assertEquals(List.of("zauctionhouse.limit.10", "zauctionhouse.tax.bypass"), results.stream().map(OfflinePermissionResult::permission).toList());
        assertTrue(results.stream().noneMatch(OfflinePermissionResult::result));
    }

    @Test
    @DisplayName("ensemble vide -> liste vide, future deja complete")
    void emptySet_emptyResult() {
        var future = permission.hasPermissions(mock(OfflinePlayer.class), Set.of());

        assertTrue(future.isDone());
        assertTrue(future.join().isEmpty());
    }

    @Test
    @DisplayName("le joueur n'est jamais interroge")
    void player_isNeverQueried() {
        OfflinePlayer player = mock(OfflinePlayer.class);

        var future = permission.hasPermissions(player, Set.of("a.b"));

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        verifyNoInteractions(player);
    }
}
