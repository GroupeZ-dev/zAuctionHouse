package fr.maxlego08.zauctionhouse.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageUtilsTest {

    /**
     * Sous-classe anonyme : la classe est abstraite et ses methodes utiles sont protegees.
     */
    private final MessageUtils utils = new MessageUtils() {
    };

    @Test
    @DisplayName("getString remplace chaque paire cle/valeur dans l'ordre")
    void getString_replacesPairs() {
        String result = MessageUtils.getString("%player% a vendu %item% pour %price%",
                new Object[]{"%player%", "Steve", "%item%", "Diamond", "%price%", 10});
        assertEquals("Steve a vendu Diamond pour 10", result);
    }

    @Test
    @DisplayName("getString sans argument rend le message inchange")
    void getString_noArgs_unchanged() {
        assertEquals("hello", MessageUtils.getString("hello", new Object[0]));
    }

    @Test
    @DisplayName("getString refuse un nombre impair d'arguments")
    void getString_oddArgs_throws() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MessageUtils.getString("x", new Object[]{"%a%"}));
        assertTrue(exception.getMessage().contains("pairs"));
    }

    @Test
    @DisplayName("getString ignore les paires dont la cle ou la valeur est nulle")
    void getString_nullPairs_skipped() {
        String result = MessageUtils.getString("%a% %b% %c%", new Object[]{"%a%", null, null, "x", "%c%", "C"});
        assertEquals("%a% %b% C", result);
    }

    @Test
    @DisplayName("getString remplace toutes les occurrences d'une meme cle")
    void getString_replacesAllOccurrences() {
        assertEquals("1 et 1", MessageUtils.getString("%n% et %n%", new Object[]{"%n%", 1}));
    }

    @Test
    @DisplayName("args delegue a getString")
    void args_delegates() {
        assertEquals("Bonjour Alex", utils.args("Bonjour %name%", "%name%", "Alex"));
    }

    @Test
    @DisplayName("getCenteredMessage rend une chaine vide pour null ou vide")
    void getCenteredMessage_emptyInputs() {
        assertEquals("", utils.getCenteredMessage(null));
        assertEquals("", utils.getCenteredMessage(""));
    }

    @Test
    @DisplayName("getCenteredMessage prefixe d'espaces et conserve le message (codes & traduits en section)")
    void getCenteredMessage_prefixesSpaces() {
        String centered = utils.getCenteredMessage("&aHello");
        assertTrue(centered.endsWith("§aHello"), centered);
        assertTrue(centered.startsWith(" "));
        String spaces = centered.substring(0, centered.indexOf('§'));
        assertTrue(spaces.isBlank());
        assertTrue(spaces.length() > 20, "un message court demande beaucoup de compensation : " + spaces.length());
    }

    @Test
    @DisplayName("un message plus long recoit moins d'espaces de compensation")
    void getCenteredMessage_longerMessage_fewerSpaces() {
        String shortMessage = utils.getCenteredMessage("Hi");
        String longMessage = utils.getCenteredMessage("Hello there, this is a longer line");
        int shortSpaces = shortMessage.length() - "Hi".length();
        int longSpaces = longMessage.length() - "Hello there, this is a longer line".length();
        assertTrue(shortSpaces > longSpaces, shortSpaces + " vs " + longSpaces);
    }

    @Test
    @DisplayName("le gras elargit le texte : un message en gras recoit moins d'espaces que le meme en normal")
    void getCenteredMessage_boldIsWider() {
        String normal = utils.getCenteredMessage("&fCenter me please");
        String bold = utils.getCenteredMessage("&lCenter me please");
        int normalSpaces = normal.indexOf('§');
        int boldSpaces = bold.indexOf('§');
        assertTrue(boldSpaces < normalSpaces, boldSpaces + " vs " + normalSpaces);
    }

    @Test
    @DisplayName("un message deja plus large que la moitie de l'ecran n'est pas prefixe")
    void getCenteredMessage_veryLong_noPrefix() {
        String text = "x".repeat(200);
        assertEquals(text, utils.getCenteredMessage(text));
    }

    @Test
    @DisplayName("message(sender, text) envoie un composant via le helper de composants")
    void message_sendsComponent() {
        CommandSender sender = mock(CommandSender.class);
        utils.message(sender, "&aHello");
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("le helper de composants est partage et initialise")
    void componentMessage_isShared() {
        assertNotNull(utils.componentMessage);
        assertSame(fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper.componentMessage, utils.componentMessage);
    }
}
