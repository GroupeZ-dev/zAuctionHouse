package fr.maxlego08.zauctionhouse.utils.yaml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlKeyTest {

    @Test
    @DisplayName("le constructeur court initialise sans commentaire inline, sans valeur, non liste")
    void shortConstructor_defaults() {
        YamlKey key = new YamlKey("database.host", "host", 2);

        assertEquals("database.host", key.getFullPath());
        assertEquals("host", key.getKey());
        assertEquals(2, key.getIndentLevel());
        assertNull(key.getInlineComment());
        assertNull(key.getValue());
        assertNull(key.getRawLine());
        assertFalse(key.isList());
        assertTrue(key.getPrecedingComments().isEmpty());
        assertTrue(key.isSection(), "sans valeur ni liste, la cle est une section");
    }

    @Test
    @DisplayName("le constructeur long conserve le commentaire inline")
    void longConstructor_keepsInlineComment() {
        YamlKey key = new YamlKey("debug", "debug", 0, "# active les logs");
        assertEquals("# active les logs", key.getInlineComment());
        assertEquals(0, key.getIndentLevel());
    }

    @Test
    @DisplayName("une valeur rend la cle non-section ; une liste aussi, meme sans valeur")
    void isSection_valueOrList() {
        YamlKey withValue = new YamlKey("a", "a", 0);
        withValue.setValue(5);
        assertFalse(withValue.isSection());
        assertEquals(5, withValue.getValue());

        YamlKey list = new YamlKey("b", "b", 0);
        list.setList(true);
        assertTrue(list.isList());
        assertFalse(list.isSection());

        list.setList(false);
        assertTrue(list.isSection());
    }

    @Test
    @DisplayName("les commentaires precedents s'accumulent dans l'ordre")
    void precedingComments_accumulate() {
        YamlKey key = new YamlKey("a", "a", 0);
        key.addPrecedingComment("# first");
        key.addPrecedingComment("# second");
        assertEquals(List.of("# first", "# second"), key.getPrecedingComments());
    }

    @Test
    @DisplayName("rawLine est stockee telle quelle et toString decrit la cle")
    void rawLine_andToString() {
        YamlKey key = new YamlKey("a.b", "b", 2);
        key.setRawLine("  b: 1 # c");
        assertEquals("  b: 1 # c", key.getRawLine());
        assertEquals("YamlKey{fullPath='a.b', key='b', indent=2}", key.toString());
    }
}
