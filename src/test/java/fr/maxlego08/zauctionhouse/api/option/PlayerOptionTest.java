package fr.maxlego08.zauctionhouse.api.option;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerOptionTest {

    @ParameterizedTest(name = "fromKey({0}.getKey()) rend {0}")
    @EnumSource(PlayerOption.class)
    void fromKey_exactKey_returnsOption(PlayerOption option) {
        assertSame(option, PlayerOption.fromKey(option.getKey()));
    }

    @ParameterizedTest(name = "fromKey ignore la casse pour {0}")
    @EnumSource(PlayerOption.class)
    void fromKey_isCaseInsensitive(PlayerOption option) {
        assertSame(option, PlayerOption.fromKey(option.getKey().toUpperCase()));
    }

    @Test
    @DisplayName("fromKey rend null pour une cle inconnue ou nulle")
    void fromKey_unknownOrNull_returnsNull() {
        assertNull(PlayerOption.fromKey("does_not_exist"));
        assertNull(PlayerOption.fromKey(""));
        assertNull(PlayerOption.fromKey(null));
    }

    @ParameterizedTest(name = "{0} : la valeur par defaut est reconnue quelle que soit sa casse")
    @EnumSource(PlayerOption.class)
    void isDefaultValue_matchesIgnoringCase(PlayerOption option) {
        assertTrue(option.isDefaultValue(option.getDefaultValue()));
        assertTrue(option.isDefaultValue(option.getDefaultValue().toUpperCase()));
        assertFalse(option.isDefaultValue("not-a-default"));
        assertFalse(option.isDefaultValue(null));
    }

    @Test
    @DisplayName("les deux options de diffusion sont actives par defaut")
    void defaults_areTrue() {
        assertEquals("true", PlayerOption.BROADCAST_SELL.getDefaultValue());
        assertEquals("true", PlayerOption.BROADCAST_PURCHASE.getDefaultValue());
        assertEquals("broadcast_sell", PlayerOption.BROADCAST_SELL.getKey());
        assertEquals("broadcast_purchase", PlayerOption.BROADCAST_PURCHASE.getKey());
    }

    @ParameterizedTest(name = "{0} expose un message active et un message desactive")
    @EnumSource(PlayerOption.class)
    void messages_arePresentAndDistinct(PlayerOption option) {
        assertNotNull(option.getEnabledMessage());
        assertNotNull(option.getDisabledMessage());
        assertFalse(option.getEnabledMessage() == option.getDisabledMessage());
    }
}
