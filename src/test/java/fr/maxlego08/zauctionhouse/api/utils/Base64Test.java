package fr.maxlego08.zauctionhouse.api.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Base64Test {

    @Test
    @DisplayName("encode(null) et decode(null) rendent null")
    void nullInputs_returnNull() {
        assertNull(Base64.encode(null));
        assertNull(Base64.decode(null));
    }

    @Test
    @DisplayName("un tableau vide s'encode en chaine vide, et une chaine vide se decode en tableau vide")
    void empty_roundTrip() {
        assertEquals("", Base64.encode(new byte[0]));
        assertArrayEquals(new byte[0], Base64.decode(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "abc", "abcd", "hello world", "zAuctionHouse"})
    @DisplayName("aller-retour ASCII, avec les trois cas de padding (1, 2 et 0 octets restants)")
    void asciiRoundTrip(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        String encoded = Base64.encode(bytes);
        assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), encoded, "compatible avec l'encodeur standard");
        assertArrayEquals(bytes, Base64.decode(encoded));
    }

    @Test
    @DisplayName("les valeurs connues du RFC 4648 sont respectees")
    void knownVectors() {
        assertEquals("Zg==", Base64.encode("f".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("Zm8=", Base64.encode("fo".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("Zm9v", Base64.encode("foo".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("Zm9vYg==", Base64.encode("foob".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("Zm9vYmE=", Base64.encode("fooba".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("Zm9vYmFy", Base64.encode("foobar".getBytes(StandardCharsets.US_ASCII)));
        assertArrayEquals("foobar".getBytes(StandardCharsets.US_ASCII), Base64.decode("Zm9vYmFy"));
    }

    @Test
    @DisplayName("aller-retour de caracteres non ASCII (UTF-8 multi-octets, octets negatifs)")
    void nonAsciiRoundTrip() {
        String text = "héllo wörld € 日本語 ✓";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.encode(bytes);
        assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), encoded);
        assertEquals(text, new String(Base64.decode(encoded), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("aller-retour de tous les octets 0x00..0xFF")
    void allByteValuesRoundTrip() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < 256; i++) bytes[i] = (byte) i;
        String encoded = Base64.encode(bytes);
        assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), encoded);
        assertArrayEquals(bytes, Base64.decode(encoded));
    }

    @Test
    @DisplayName("aller-retour de longueurs 0 a 128 avec des octets pseudo-aleatoires deterministes")
    void randomLengthsRoundTrip() {
        Random random = new Random(20261031L);
        for (int length = 0; length <= 128; length++) {
            byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            String encoded = Base64.encode(bytes);
            assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), encoded, "length " + length);
            assertArrayEquals(bytes, Base64.decode(encoded), "length " + length);
        }
    }

    @Test
    @DisplayName("decode tolere les espaces, tabulations et retours a la ligne")
    void decode_ignoresWhitespace() {
        byte[] expected = "foobar".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(expected, Base64.decode("Zm9v\nYmFy"));
        assertArrayEquals(expected, Base64.decode(" Zm9v\tYmFy\r\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Z", "Zm", "Zm9", "Zm9vY"})
    @DisplayName("decode rend null quand la longueur utile n'est pas un multiple de 4")
    void decode_invalidLength_returnsNull(String encoded) {
        assertNull(Base64.decode(encoded));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Zm9v!mFy", "Zm9vYm*y", "Zm9vYmF-", "Zm9vYmF€", "Zm=9"})
    @DisplayName("decode rend null sur un caractere hors alphabet ou un padding mal place")
    void decode_invalidCharacter_returnsNull(String encoded) {
        assertNull(Base64.decode(encoded));
    }

    @Test
    @DisplayName("decode rend null quand les bits de bourrage ne sont pas nuls")
    void decode_nonZeroPaddingBits_returnsNull() {
        // "Zh==" : les 4 bits bas de 'h' ne sont pas nuls (seul "Zg==" est canonique pour 'f').
        assertNull(Base64.decode("Zh=="));
        // "Zm9=" : les 2 bits bas de '9' ne sont pas nuls ("Zm8=" est canonique pour "fo").
        assertNull(Base64.decode("Zm9="));
    }

    @Test
    @DisplayName("decode rend null pour un padding incoherent (pad puis donnee)")
    void decode_padThenData_returnsNull() {
        assertNull(Base64.decode("Zm9vYm=y"));
    }
}
