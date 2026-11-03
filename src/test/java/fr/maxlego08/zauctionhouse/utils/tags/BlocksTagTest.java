package fr.maxlego08.zauctionhouse.utils.tags;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code isTagged} et {@code getValues} reposent sur {@code Material#isBlock()}, qui passe par
 * le registre du serveur sur Paper 1.21 : ils ne sont pas testables sans serveur.
 */
class BlocksTagTest {

    @Test
    @DisplayName("la cle du tag est zauctionhouse:blocks")
    void getKey() {
        BlocksTag tag = new BlocksTag();
        NamespacedKey key = tag.getKey();
        assertEquals("zauctionhouse", key.getNamespace());
        assertEquals("blocks", key.getKey());
        assertEquals("zauctionhouse:blocks", key.toString());
        assertEquals(new NamespacedKey("zauctionhouse", "blocks"), tag.getKey());
    }
}
