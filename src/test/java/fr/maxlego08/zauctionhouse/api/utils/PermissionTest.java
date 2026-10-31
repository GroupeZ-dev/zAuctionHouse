package fr.maxlego08.zauctionhouse.api.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionTest {

    @Test
    @DisplayName("asPermission met en minuscules et remplace les underscores par des points")
    void asPermission_format() {
        assertEquals("zauctionhouse.use", Permission.ZAUCTIONHOUSE_USE.asPermission());
        assertEquals("zauctionhouse.admin.remove.inventory", Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.asPermission());
        assertEquals("admin.logs.give.item.to.player", Permission.ADMIN_LOGS_GIVE_ITEM_TO_PLAYER.asPermission());
    }

    @Test
    @DisplayName("asPermission(with) concatene le suffixe tel quel")
    void asPermission_withSuffix() {
        assertEquals("zauctionhouse.sell.5", Permission.ZAUCTIONHOUSE_SELL.asPermission(".5"));
        assertEquals("zauctionhouse.admin", Permission.ZAUCTIONHOUSE_ADMIN.asPermission(""));
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    @DisplayName("aucune permission ne contient de majuscule ni d'underscore, et toPermission == asPermission sans arguments")
    void everyPermission_isNormalized(Permission permission) {
        String value = permission.asPermission();
        assertEquals(value.toLowerCase(), value);
        assertFalse(value.contains("_"));
        assertNotNull(permission.getDescription());
        assertNotNull(permission.getArgs());
        if (permission.getArgs().length == 0) {
            assertEquals(value, permission.toPermission());
        }
    }

    @Test
    @DisplayName("la description n'est renseignee que pour les constantes qui en declarent une")
    void description() {
        assertTrue(Permission.ZAUCTIONHOUSE_ADMIN_REMOVE_INVENTORY.getDescription().contains("remove an item"));
        assertEquals("", Permission.ZAUCTIONHOUSE_USE.getDescription());
        assertEquals(0, Permission.ZAUCTIONHOUSE_USE.getArgs().length);
    }
}
