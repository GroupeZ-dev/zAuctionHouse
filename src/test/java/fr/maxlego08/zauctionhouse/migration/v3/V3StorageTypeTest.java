package fr.maxlego08.zauctionhouse.migration.v3;

import fr.maxlego08.zauctionhouse.api.item.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V3StorageTypeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"STORAGE,LISTED", "BUY,PURCHASED", "EXPIRE,EXPIRED"})
    @DisplayName("chaque type V3 est converti vers le conteneur V4 correspondant")
    void toV4StorageType_mapsEveryValue(V3StorageType v3, StorageType v4) {
        assertEquals(v4, v3.toV4StorageType());
    }

    @Test
    @DisplayName("les suffixes de table V3 sont inverses entre BUY et EXPIRE (bizarrerie historique)")
    void tableSuffix_isSwappedForBuyAndExpire() {
        assertEquals("auction", V3StorageType.STORAGE.getTableSuffix());
        assertEquals("auction_expire", V3StorageType.BUY.getTableSuffix());
        assertEquals("auction_buy", V3StorageType.EXPIRE.getTableSuffix());
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({"BUY,BUY", "buy,BUY", "Buy,BUY", "EXPIRE,EXPIRE", "expire,EXPIRE", "STORAGE,STORAGE", "storage,STORAGE"})
    @DisplayName("fromString ignore la casse")
    void fromString_ignoresCase(String value, V3StorageType expected) {
        assertEquals(expected, V3StorageType.fromString(value));
    }

    @ParameterizedTest(name = "\"{0}\" -> STORAGE")
    @ValueSource(strings = {"", "garbage", "LISTED", "PURCHASED", "EXPIRED", "DELETED"})
    @DisplayName("fromString retombe sur STORAGE pour toute valeur inconnue, y compris les noms V4")
    void fromString_unknown_defaultsToStorage(String value) {
        assertEquals(V3StorageType.STORAGE, V3StorageType.fromString(value));
    }

    @Test
    @DisplayName("fromString(null) retombe sur STORAGE")
    void fromString_null_defaultsToStorage() {
        assertEquals(V3StorageType.STORAGE, V3StorageType.fromString(null));
    }
}
