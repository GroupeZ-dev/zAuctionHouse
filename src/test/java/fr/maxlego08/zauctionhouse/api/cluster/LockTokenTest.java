package fr.maxlego08.zauctionhouse.api.cluster;

import fr.maxlego08.zauctionhouse.api.item.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LockTokenTest {

    private static Item item(int id) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    @Test
    @DisplayName("noop est une sentinelle de contention : ni acquis, ni indisponible")
    void noop_flags() {
        LockToken token = LockToken.noop();
        assertTrue(token.isNoop());
        assertFalse(token.isUnavailable());
        assertFalse(token.isAcquired());
        assertEquals("NOOP", token.value());
        assertEquals(LockToken.noop(), token, "les sentinelles sont egales entre elles");
    }

    @Test
    @DisplayName("unavailable est une sentinelle d'etat terminal : ni acquis, ni noop")
    void unavailable_flags() {
        LockToken token = LockToken.unavailable();
        assertTrue(token.isUnavailable());
        assertFalse(token.isNoop());
        assertFalse(token.isAcquired());
        assertEquals("UNAVAILABLE", token.value());
        assertNotEquals(LockToken.noop(), token);
    }

    @Test
    @DisplayName("issue(item, owner) encode l'id de l'item, le detenteur et un nonce")
    void issue_withOwner_format() {
        UUID owner = UUID.randomUUID();
        LockToken token = LockToken.issue(item(42), owner);

        String[] parts = token.value().split(":");
        assertEquals(4, parts.length, token.value());
        assertEquals("item", parts[0]);
        assertEquals("42", parts[1]);
        assertEquals(owner.toString(), parts[2]);
        UUID.fromString(parts[3]); // le nonce est un UUID valide
        assertTrue(token.isAcquired());
        assertFalse(token.isNoop());
        assertFalse(token.isUnavailable());
    }

    @Test
    @DisplayName("issue(item, null) marque l'absence de detenteur par un tiret")
    void issue_nullOwner_usesDash() {
        LockToken token = LockToken.issue(item(7), null);
        assertTrue(token.value().startsWith("item:7:-:"));
        assertTrue(token.isAcquired());
    }

    @Test
    @DisplayName("issue(item) et of(item) (deprecie) produisent des jetons sans detenteur, uniques")
    @SuppressWarnings("deprecation")
    void issue_and_of_withoutOwner() {
        Item item = item(3);
        LockToken a = LockToken.issue(item);
        LockToken b = LockToken.of(item);

        assertTrue(a.value().startsWith("item:3:-:"));
        assertTrue(b.value().startsWith("item:3:-:"));
        assertNotEquals(a, b, "chaque acquisition porte son propre nonce");
    }

    @Test
    @DisplayName("deux emissions pour le meme item et le meme detenteur restent distinctes")
    void issue_sameInputs_differentTokens() {
        Item item = item(1);
        UUID owner = UUID.randomUUID();
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            values.add(LockToken.issue(item, owner).value());
        }
        assertEquals(100, values.size());
    }

    @Test
    @DisplayName("un jeton construit directement avec une valeur quelconque est considere acquis")
    void constructor_customValue_isAcquired() {
        LockToken token = new LockToken("anything");
        assertTrue(token.isAcquired());
        assertEquals("anything", token.value());
        assertEquals(new LockToken("anything"), token);
        assertEquals(new LockToken("anything").hashCode(), token.hashCode());
    }

    @Test
    @DisplayName("un jeton dont la valeur est null n'est ni noop ni unavailable")
    void constructor_nullValue_flags() {
        LockToken token = new LockToken(null);
        assertFalse(token.isNoop());
        assertFalse(token.isUnavailable());
        assertTrue(token.isAcquired());
    }
}
