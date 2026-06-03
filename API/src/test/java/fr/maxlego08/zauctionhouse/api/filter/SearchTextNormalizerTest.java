package fr.maxlego08.zauctionhouse.api.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchTextNormalizerTest {

    @Test
    void normalizesSmallCapsToPlainAscii() {
        assertEquals("sandaly", SearchTextNormalizer.normalize("sᴀɴᴅᴀʟʏ"));
    }

    @Test
    void normalizesMixedCaseAndSmallCapsTogether() {
        assertEquals("sandaly poslanca", SearchTextNormalizer.normalize("Sᴀɴᴅᴀʟʏ Pᴏsʟᴀɴᴄᴀ"));
    }

    @Test
    void lowercasesRegularText() {
        assertEquals("diamond sword", SearchTextNormalizer.normalize("Diamond Sword"));
    }
}
