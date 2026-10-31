package fr.maxlego08.zauctionhouse.api.storage.dto;

import fr.maxlego08.zauctionhouse.api.log.LogType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogDTOTest {

    private static LogDTO log(Date readedAt) {
        return new LogDTO(1, LogType.PURCHASE, 42, UUID.nameUUIDFromBytes("buyer".getBytes()), UUID.nameUUIDFromBytes("seller".getBytes()),
                "payload", BigDecimal.TEN, "vault", null, readedAt, new Date(0L), new Date(0L));
    }

    @Test
    @DisplayName("isRead est faux tant que readed_at est null")
    void isRead_nullReadedAt_returnsFalse() {
        assertFalse(log(null).isRead());
    }

    @Test
    @DisplayName("isRead est vrai des que readed_at est renseigne, meme a l'epoque 0")
    void isRead_withReadedAt_returnsTrue() {
        assertTrue(log(new Date()).isRead());
        assertTrue(log(new Date(0L)).isRead(), "une date a 0 ms est une date, pas un null");
    }

    @Test
    @DisplayName("deux logs de meme contenu sont egaux, deux logs d'identifiants differents ne le sont pas")
    void equals_isStructural() {
        assertEquals(log(null), log(null));
        assertNotEquals(log(null), new LogDTO(2, LogType.PURCHASE, 42, UUID.nameUUIDFromBytes("buyer".getBytes()), UUID.nameUUIDFromBytes("seller".getBytes()),
                "payload", BigDecimal.TEN, "vault", null, null, new Date(0L), new Date(0L)));
    }
}
